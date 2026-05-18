package LLD.LldQuestions.TicketBooking.models;

import java.util.ArrayList;
import java.util.List;

public class Screen {

    private final String id;
    private final String name;
    private final String theatreId;
    private final List<Seat> seats;

    public Screen(final String id, final String name, final String theatreId) {
        this.id = id;
        this.name = name;
        this.theatreId = theatreId;
        this.seats = new ArrayList<>();
    }

    public void addSeat(Seat seat) {
        this.seats.add(seat);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTheatreId() {
        return theatreId;
    }

    public List<Seat> getSeats() {
        return seats;
    }
}
