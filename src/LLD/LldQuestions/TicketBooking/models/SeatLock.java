package LLD.LldQuestions.TicketBooking.models;

import java.time.Instant;

/*
 * A short-lived "soft hold" on a single seat for a single show.
 *
 *   userId    -> who owns this hold; only this user can confirm (book) the seat.
 *   lockedAt  -> when the hold was placed.
 *   expiresAt -> when it auto-expires; after this instant any other user may
 *                lock the seat.
 *
 * The lock is intentionally fine-grained (one per seat) so that:
 *   - we can release/extend single seats independently,
 *   - the cleanup job can remove just the entries it must,
 *   - logging / telemetry can attribute contention down to a single seat.
 */
public class SeatLock {

    private final String seatId;
    private final String showId;
    private final String userId;
    private final Instant lockedAt;
    private final Instant expiresAt;

    public SeatLock(final String seatId,
                    final String showId,
                    final String userId,
                    final Instant lockedAt,
                    final Instant expiresAt) {
        this.seatId = seatId;
        this.showId = showId;
        this.userId = userId;
        this.lockedAt = lockedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isOwnedBy(String candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    public String getSeatId() {
        return seatId;
    }

    public String getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
