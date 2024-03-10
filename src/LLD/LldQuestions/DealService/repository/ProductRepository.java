package LLD.LldQuestions.DealService.repository;

import LLD.LldQuestions.DealService.model.Product;

import java.util.HashMap;

public class ProductRepository {

    HashMap<Integer, Product> productmap = new HashMap<>();

    public Product getProduct(int id) {
        if(this.productmap.containsKey(id)) {
            return this.productmap.get(id);
        } else {
            return null;
        }
    }

    public void createProducts(Product product) {
        this.productmap.put(product.getId(), product);
    }
}
