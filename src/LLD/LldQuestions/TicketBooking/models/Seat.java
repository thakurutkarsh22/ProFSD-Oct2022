package LLD.LldQuestions.TicketBooking.models;

public class Seat {

    private final String id;
    private final int row;
    private final int col;
    private final SeatType type;

    public Seat(final String id, final int row, final int col, final SeatType type) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public SeatType getType() {
        return type;
    }
}
