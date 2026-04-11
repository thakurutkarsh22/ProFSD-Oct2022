package LLD.LldQuestions.Splitwise2.models;

public class User {

    private String id;
    private String name;
    private String email;
    private String phone;

    public User(final String  id, final String name, final String email, final String phone) {
        this.name = name;
        this.email = email;
        this.id = id;
        this.phone = phone;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return this.name;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPhone() {
        return this.phone;
    }





}
