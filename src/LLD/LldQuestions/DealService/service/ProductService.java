package LLD.LldQuestions.DealService.service;

import LLD.LldQuestions.DealService.model.Product;
import LLD.LldQuestions.DealService.model.User;
import LLD.LldQuestions.DealService.repository.ProductRepository;
import LLD.LldQuestions.DealService.repository.UserRepository;

public class ProductService {
    ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product getUser(int productId){
        return this.productRepository.getProduct(productId);
    }

    public void createProduct(int id, int price, String name) {
        this.productRepository.createProducts(new Product(id, name, price));
    }
}
