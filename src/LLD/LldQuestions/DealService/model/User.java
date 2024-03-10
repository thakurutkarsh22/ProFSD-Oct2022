package LLD.LldQuestions.DealService.model;

public class User {

    private final int id;
    private final String name;
    private final String email;
    private final String phoneNumber;


    public User(final int  id, final String name, final String email, final String phoneNumber) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }


}
