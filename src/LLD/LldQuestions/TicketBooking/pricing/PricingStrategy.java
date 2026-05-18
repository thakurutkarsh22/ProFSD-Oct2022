package LLD.LldQuestions.TicketBooking.pricing;

import LLD.LldQuestions.TicketBooking.models.Movie;
import LLD.LldQuestions.TicketBooking.models.Seat;

/*
 * Pluggable pricing engine.
 *
 * Decouples HOW prices are computed from the Show model. Swap the
 * implementation for flat / dynamic / surge pricing -- Show stays untouched.
 *
 * Extension hooks we can talk about in the interview but don't have to code:
 *   - add `LocalDateTime showStart` for weekend / prime-time surcharges
 *   - chain multiple strategies via a Composite for rule pipelines
 */
public interface PricingStrategy {

    double priceFor(Movie movie, Seat seat);
}
