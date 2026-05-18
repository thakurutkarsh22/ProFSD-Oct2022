package LLD.LldQuestions.TicketBooking.lock;

import LLD.LldQuestions.TicketBooking.models.SeatLock;

import java.util.List;

/*
 * Abstraction for the seat-locking mechanism.
 *
 * Why an interface?
 *   - Today: in-memory implementation (good enough for a single JVM).
 *   - Tomorrow: distributed implementation backed by Redis (SETNX + PEXPIRE)
 *     or DB row-locks for a horizontally-scaled BookingService.
 *   The rest of the system (BookingService) does not care which one it is.
 *
 * Contract:
 *   - lockSeats() is ALL-OR-NOTHING. Either every seat in `seatIds` becomes
 *     held by `userId`, or no state changes at all. Partial locks are bugs.
 *   - lockSeats() is non-blocking. It returns false on conflict; the caller
 *     decides whether to retry, fail, or surface to the user.
 *   - A lock auto-expires after `lockTimeoutSeconds`. Expired locks are
 *     treated as if they never existed (lazy purge + background sweep).
 */
public interface SeatLockProvider {

    boolean lockSeats(String showId, List<String> seatIds, String userId);

    void unlockSeats(String showId, List<String> seatIds, String userId);

    boolean validateLock(String showId, List<String> seatIds, String userId);

    SeatLock getSeatLock(String showId, String seatId);
}
