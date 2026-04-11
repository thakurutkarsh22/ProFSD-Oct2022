package LLD.LldQuestions.Splitwise2.models;

public class ExpenseMetaData {
    private String name;
    private String imageUrl;
    private String notes;

    public ExpenseMetaData(String name, String imageUrl, String notes) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.notes = notes;
    }

    public String getName() {
        return this.name;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public String getNotes() {
        return this.notes;
    }


}

