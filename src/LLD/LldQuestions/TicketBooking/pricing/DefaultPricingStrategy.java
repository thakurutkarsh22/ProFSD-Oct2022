package LLD.LldQuestions.TicketBooking.pricing;

import LLD.LldQuestions.TicketBooking.models.Movie;
import LLD.LldQuestions.TicketBooking.models.Seat;
import LLD.LldQuestions.TicketBooking.models.SeatType;

import java.util.Map;

/*
 * Basic pricing:  price = base(movie) * multiplier(seatType)
 *
 * base(movie) resolution order (most specific wins):
 *   1) explicit per-movie override   -- e.g., "Dhurandhar" -> 500
 *   2) per-language default          -- e.g., "Tamil"      -> 200
 *   3) global fallback               -- e.g., 300
 *
 * Multipliers: REGULAR x1.0, PREMIUM x1.5, RECLINER x2.0.
 */
public class DefaultPricingStrategy implements PricingStrategy {

    private final Map<String, Double> moviePrice;     // movieId   -> base
    private final Map<String, Double> languagePrice;  // language  -> base
    private final double defaultBase;

    public DefaultPricingStrategy(Map<String, Double> moviePrice,
                                  Map<String, Double> languagePrice,
                                  double defaultBase) {
        this.moviePrice = moviePrice;
        this.languagePrice = languagePrice;
        this.defaultBase = defaultBase;
    }

    @Override
    public double priceFor(Movie movie, Seat seat) {
        return basePrice(movie) * seatMultiplier(seat.getType());
    }

    private double basePrice(Movie movie) {
        if (moviePrice.containsKey(movie.getId())) {
            return moviePrice.get(movie.getId());
        }
        if (languagePrice.containsKey(movie.getLanguage())) {
            return languagePrice.get(movie.getLanguage());
        }
        return defaultBase;
    }

    private double seatMultiplier(SeatType type) {
        switch (type) {
            case PREMIUM:  return 1.5;
            case RECLINER: return 2.0;
            case REGULAR:
            default:       return 1.0;
        }
    }
}
