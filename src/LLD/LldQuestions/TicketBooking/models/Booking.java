package LLD.LldQuestions.TicketBooking.models;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class Booking {

    private final String id;
    private final String userId;
    private final String showId;
    private final List<String> seatIds;
    private final double totalAmount;
    private final Instant createdAt;
    private volatile BookingStatus status;

    public Booking(final String id,
                   final String userId,
                   final String showId,
                   final List<String> seatIds,
                   final double totalAmount) {
        this.id = id;
        this.userId = userId;
        this.showId = showId;
        this.seatIds = Collections.unmodifiableList(seatIds);
        this.totalAmount = totalAmount;
        this.status = BookingStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getShowId() {
        return showId;
    }

    public List<String> getSeatIds() {
        return seatIds;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
