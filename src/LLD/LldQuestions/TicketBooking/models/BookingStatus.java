package LLD.LldQuestions.TicketBooking.models;

public enum BookingStatus {
    PENDING,    // seats locked, awaiting payment
    CONFIRMED,  // payment succeeded, seats permanently booked
    EXPIRED,    // seat lock TTL elapsed before payment completed
    CANCELLED   // user / system cancelled an already-confirmed booking
}
