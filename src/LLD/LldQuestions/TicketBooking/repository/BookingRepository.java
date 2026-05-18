package LLD.LldQuestions.TicketBooking.repository;

import LLD.LldQuestions.TicketBooking.exceptions.BookingNotFoundException;
import LLD.LldQuestions.TicketBooking.models.Booking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BookingRepository {

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public void save(Booking booking) {
        bookings.put(booking.getId(), booking);
    }

    public Booking get(String bookingId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    public Map<String, Booking> getAll() {
        return bookings;
    }
}
