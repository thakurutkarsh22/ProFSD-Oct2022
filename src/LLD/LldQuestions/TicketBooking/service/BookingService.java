package LLD.LldQuestions.TicketBooking.service;

import LLD.LldQuestions.TicketBooking.exceptions.SeatLockExpiredException;
import LLD.LldQuestions.TicketBooking.exceptions.SeatUnavailableException;
import LLD.LldQuestions.TicketBooking.lock.SeatLockProvider;
import LLD.LldQuestions.TicketBooking.models.Booking;
import LLD.LldQuestions.TicketBooking.models.BookingStatus;
import LLD.LldQuestions.TicketBooking.models.Show;
import LLD.LldQuestions.TicketBooking.repository.BookingRepository;
import LLD.LldQuestions.TicketBooking.repository.ShowRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║                          BookingService                                ║
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * The booking lifecycle is intentionally a TWO-PHASE flow:
 *
 *   ┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────┐
 *   │ initiateBooking()    │───►│   <user pays>        │───►│ confirm()    │
 *   │ - acquire seat locks │    │  (external payment   │    │ - validate   │
 *   │ - PENDING booking    │    │   gateway, takes     │    │   lock still │
 *   │ - TTL starts ticking │    │   seconds-minutes)   │    │   owned      │
 *   └──────────────────────┘    └──────────────────────┘    │ - mark BOOKED│
 *                                                           │ - release    │
 *                                                           │   lock       │
 *                                                           └──────────────┘
 *
 * Why two phases?
 *   - Payment is *slow* (seconds-to-minutes) and EXTERNAL. Holding any kind
 *     of strong lock across an external network call is a recipe for
 *     deadlocks, lost holds on JVM crash, and timeouts that block other users.
 *   - The seat-lock is the "soft hold" -- short-lived and revocable.
 *   - The Show.seatStatus = BOOKED is the "permanent commit" -- only flipped
 *     after payment succeeds, under a per-show critical section.
 *
 * Per-show ReentrantLock, again, why?
 *   - Both initiateBooking() and confirmBooking() must atomically (a) check
 *     Show.seatStatus and (b) interact with SeatLockProvider. If those two
 *     mutations are split across two locks, a race window exists where
 *     another thread can squeeze in between them.
 *   - Holding ONE per-show lock for the union of those checks closes that
 *     window without serializing different shows against each other.
 *
 * What the seat-lock does NOT do:
 *   - It is not a payment lock. Payment runs OUTSIDE this service.
 *   - It is not a database transaction. In a real impl this method would
 *     wrap the "mark BOOKED + persist Booking" step in one DB transaction
 *     for crash safety; the in-memory version skips that for clarity.
 */
public class BookingService {

    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final SeatLockProvider seatLockProvider;

    /*
     * Per-show coarse lock used by initiateBooking()/confirmBooking() to make
     * the (read seatStatus + interact with lockProvider) compound action
     * atomic. SeatLockProvider has its own internal per-show lock for its own
     * map; we deliberately keep them separate so the provider can be swapped
     * out (e.g., to Redis) without losing this service's atomicity guarantee.
     */
    private final Map<String, ReentrantLock> showLocks = new ConcurrentHashMap<>();

    public BookingService(ShowRepository showRepository,
                          BookingRepository bookingRepository,
                          SeatLockProvider seatLockProvider) {
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
        this.seatLockProvider = seatLockProvider;
    }

    public Booking initiateBooking(String userId, String showId, List<String> seatIds) {
        Show show = showRepository.getShow(showId);
        ReentrantLock showLock = lockFor(showId);

        showLock.lock();
        try {
            // 1) Reject the entire party if ANY requested seat is already
            //    permanently sold. Done first because a BOOKED seat will never
            //    become AVAILABLE again on its own; no point trying to lock.
            for (String seatId : seatIds) {
                if (show.isBooked(seatId)) {
                    throw new SeatUnavailableException(
                            "Seat already booked: " + seatId + " on show " + showId);
                }
            }

            // 2) ALL-OR-NOTHING soft hold. False here means at least one seat
            //    is currently locked by another in-flight booking attempt.
            boolean acquired = seatLockProvider.lockSeats(showId, seatIds, userId);
            if (!acquired) {
                throw new SeatUnavailableException(
                        "Seats currently held by another user: " + seatIds);
            }

            // 3) Persist a PENDING booking. Computing the price under the lock
            //    avoids reading a half-mutated price map -- not strictly
            //    needed today (price is immutable), but cheap insurance.
            double total = computeTotal(show, seatIds);
            Booking booking = new Booking(
                    UUID.randomUUID().toString(), userId, showId, seatIds, total);
            bookingRepository.save(booking);
            return booking;
        } finally {
            showLock.unlock();
        }
    }

    public Booking confirmBooking(String bookingId, String userId) {
        Booking booking = bookingRepository.get(bookingId);
        ReentrantLock showLock = lockFor(booking.getShowId());

        showLock.lock();
        try {
            // The lock can have been wiped out by the cleanup sweep, or
            // released by an explicit cancel. Either way, the user's window
            // is gone and we MUST refuse to confirm -- otherwise we'd be
            // booking seats that someone else may now legitimately hold.
            boolean stillHeld = seatLockProvider.validateLock(
                    booking.getShowId(), booking.getSeatIds(), userId);
            if (!stillHeld) {
                booking.setStatus(BookingStatus.EXPIRED);
                throw new SeatLockExpiredException(
                        "Seat lock expired before payment confirmation for booking " + bookingId);
            }

            // Promote AVAILABLE -> BOOKED. After this, the seat-lock is
            // redundant (no future lockSeats() can win it because the show
            // will reject a BOOKED seat in step 1 above), so we release it.
            Show show = showRepository.getShow(booking.getShowId());
            for (String seatId : booking.getSeatIds()) {
                show.markBooked(seatId);
            }
            seatLockProvider.unlockSeats(
                    booking.getShowId(), booking.getSeatIds(), userId);

            booking.setStatus(BookingStatus.CONFIRMED);
            return booking;
        } finally {
            showLock.unlock();
        }
    }

    public void cancelPendingBooking(String bookingId, String userId) {
        Booking booking = bookingRepository.get(bookingId);
        ReentrantLock showLock = lockFor(booking.getShowId());

        showLock.lock();
        try {
            if (booking.getStatus() != BookingStatus.PENDING) {
                return; // already confirmed/expired -- nothing to release
            }
            seatLockProvider.unlockSeats(
                    booking.getShowId(), booking.getSeatIds(), userId);
            booking.setStatus(BookingStatus.CANCELLED);
        } finally {
            showLock.unlock();
        }
    }

    private ReentrantLock lockFor(String showId) {
        return showLocks.computeIfAbsent(showId, k -> new ReentrantLock(true));
    }

    private double computeTotal(Show show, List<String> seatIds) {
        double total = 0.0;
        for (String seatId : seatIds) {
            total += show.getSeatPrice().get(seatId);
        }
        return total;
    }
}
