package LLD.LldQuestions.TicketBooking.models;

/*
 * Show-level seat status. We deliberately keep only two terminal states here:
 *
 *   AVAILABLE  -> nobody has paid for this seat yet (it may be transiently
 *                 locked by some user mid-booking, but that lifecycle is
 *                 owned by SeatLockProvider, NOT this enum).
 *   BOOKED     -> payment confirmed; the seat is permanently sold.
 *
 * Why split "transient lock" from "permanent booked"?
 *   - The lock has a TTL (e.g., 10 minutes) and may auto-expire.
 *   - The booked state is durable and only changes via cancel/refund flows.
 * Mixing them on a single enum forces every reader to also reason about TTLs
 * and ownership, which makes the show object harder to keep consistent.
 */
public enum SeatStatus {
    AVAILABLE,
    BOOKED
}
