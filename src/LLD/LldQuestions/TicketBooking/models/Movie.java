package LLD.LldQuestions.TicketBooking.models;

public class Movie {

    private final String id;
    private final String name;
    private final int durationMinutes;
    private final String language;

    public Movie(final String id, final String name, final int durationMinutes, final String language) {
        this.id = id;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.language = language;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getLanguage() {
        return language;
    }
}
