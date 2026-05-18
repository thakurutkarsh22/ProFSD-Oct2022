package LLD.LldQuestions.TicketBooking.models;

import LLD.LldQuestions.TicketBooking.pricing.PricingStrategy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 * A scheduled showing of a Movie on a Screen at a specific time.
 *
 * Important threading notes:
 * ─ seatStatus is a plain HashMap by design. It is ONLY read/written
 *   while the per-show ReentrantLock (held in BookingService) is acquired,
 *   so no extra synchronization is needed here. Pulling the lock up to the
 *   service keeps the model dumb and avoids "lock-inside-lock" reasoning.
 * ─ seatPrice is immutable after construction. Prices are computed ONCE
 *   from the injected PricingStrategy and frozen for the life of the show
 *   so two reads always agree -- nothing surprises a user mid-booking.
 *
 * Pricing decoupling:
 * ─ Show does NOT decide how a seat is priced. A PricingStrategy is injected
 *   at construction. Swap the strategy to get flat / rule-based / surge
 *   pricing without ever touching this class. (Open/Closed Principle.)
 */
public class Show {

    private final String id;
    private final Movie movie;
    private final Screen screen;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Map<String, SeatStatus> seatStatus;
    private final Map<String, Double> seatPrice;

    public Show(final String id,
                final Movie movie,
                final Screen screen,
                final LocalDateTime startTime,
                final LocalDateTime endTime,
                final PricingStrategy pricingStrategy) {
        this.id = id;
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.endTime = endTime;
        this.seatStatus = new HashMap<>();
        this.seatPrice = new HashMap<>();

        for (Seat seat : screen.getSeats()) {
            this.seatStatus.put(seat.getId(), SeatStatus.AVAILABLE);
            this.seatPrice.put(seat.getId(), pricingStrategy.priceFor(movie, seat));
        }
    }

    public boolean isBooked(String seatId) {
        return seatStatus.get(seatId) == SeatStatus.BOOKED;
    }

    public void markBooked(String seatId) {
        seatStatus.put(seatId, SeatStatus.BOOKED);
    }

    public void markAvailable(String seatId) {
        seatStatus.put(seatId, SeatStatus.AVAILABLE);
    }

    public String getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Map<String, SeatStatus> getSeatStatus() {
        return seatStatus;
    }

    public Map<String, Double> getSeatPrice() {
        return seatPrice;
    }
}
