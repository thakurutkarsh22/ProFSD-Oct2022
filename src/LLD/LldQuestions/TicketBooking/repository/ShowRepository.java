package LLD.LldQuestions.TicketBooking.repository;

import LLD.LldQuestions.TicketBooking.exceptions.ShowNotFoundException;
import LLD.LldQuestions.TicketBooking.models.Show;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Thread-safe in-memory store for Shows.
 *
 * The map itself uses ConcurrentHashMap so that lookups (the dominant op)
 * never block. Mutations to a Show's *internal* state (e.g. seatStatus) are
 * NOT protected here -- that contract belongs to BookingService, which
 * acquires the per-show lock before touching seatStatus.
 */
public class ShowRepository {

    private final Map<String, Show> shows = new ConcurrentHashMap<>();

    public void addShow(Show show) {
        shows.put(show.getId(), show);
    }

    public Show getShow(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            throw new ShowNotFoundException("Show not found: " + showId);
        }
        return show;
    }

    public Map<String, Show> getAllShows() {
        return shows;
    }
}
