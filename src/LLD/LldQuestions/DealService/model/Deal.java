package LLD.LldQuestions.DealService.model;

import java.util.Date;
import java.util.UUID;

public class Deal {

    private String id;
    public Date startTime;
    public Date endTime;

    public Product product;
    public int itemCount;
    public int discountedPrice;

    public Deal(String id, Date startTime, Date endTime, Product product, int discountedPrice, int itemCount) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.discountedPrice = discountedPrice;
        this.product = product;
        this.itemCount = itemCount;
    }

    public String getId() {
        return  this.id;
    }

    public void setEndTime(Date date) {
        this.endTime = date;
    }

}
