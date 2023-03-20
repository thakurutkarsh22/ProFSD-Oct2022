package LLD.Patterns.FacadePattern.Scenario2.Facade;

import LLD.Patterns.FacadePattern.Scenario2.ComplexSystem.*;

public class OrderFacade {

    ProductDAO productDAO;
    Invoice invoice;
    Payment payment;
    SendNotification sendNotification;

    public OrderFacade() {
        this.productDAO = new ProductDAO();
        this.invoice = new Invoice();
        this.payment = new Payment();
        this.sendNotification = new SendNotification();
    }

    public void createOrder() {
        Product product = this.productDAO.getProduct(121);
        this.payment.makePayment();
        this.invoice.generateInvoice();
        this.sendNotification.sendNotification();

//        order creation successfully
    }
}
