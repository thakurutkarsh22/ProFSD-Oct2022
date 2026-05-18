package LLD.LldQuestions.TicketBooking.exceptions;

public class SeatLockExpiredException extends RuntimeException {
    public SeatLockExpiredException(String message) {
        super(message);
    }
}
