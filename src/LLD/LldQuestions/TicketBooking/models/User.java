package LLD.LldQuestions.TicketBooking.models;

public class User {

    private final String id;
    private final String name;
    private final String email;

    public User(final String id, final String name, final String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
