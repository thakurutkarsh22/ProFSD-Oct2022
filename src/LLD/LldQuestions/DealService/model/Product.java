package LLD.LldQuestions.DealService.model;

public class Product {

    private int id;
    private String productName;

    private int price;

    public Product(int id, String productName, int price) {
        this.id = id;
        this.productName = productName;
        this.price = price;
    }


    public int getId() {
        return this.id;
    }

    public String getProductName() {
        return this.productName;
    }

    @Override
    public String toString() {
        return "Product { id = " + this.id + "Product name" + this.productName;
    }
}
