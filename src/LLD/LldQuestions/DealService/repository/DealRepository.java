package LLD.LldQuestions.DealService.repository;

import LLD.LldQuestions.DealService.exception.AllDealItemSoldException;
import LLD.LldQuestions.DealService.exception.DealAlreadyExpired;
import LLD.LldQuestions.DealService.exception.DealNotFoundException;
import LLD.LldQuestions.DealService.model.Deal;

import java.util.Date;
import java.util.HashMap;

public class DealRepository {
    HashMap<String, Deal> dealMap = new HashMap<>();


    public Deal getDeal(String id) {
        if(!this.dealMap.containsKey(id)) {
            return null;
        }

        return this.dealMap.get(id);
    }

    public void createDeal(Deal deal) {
        this.dealMap.put(deal.getId(), deal);
    }

    public Deal updateDeal(String id, Date endTime) throws DealNotFoundException, DealAlreadyExpired {

//        Idea is IF deal need to be extended it can be extended but before the endTime

        if(this.dealMap.get(id) == null) throw new DealNotFoundException("Wrong deal id, deal do not exist");

        Deal deal = this.dealMap.get(id);
        Date oldDealEndTime =  deal.endTime;
        if(new Date().after(deal.endTime) ) {
            throw new DealAlreadyExpired("deadl end time is already finished cant update");
        }

        deal.setEndTime(endTime);
        return deal;
    }

    public Deal updateDeal(String id, int itemCount) throws DealNotFoundException, AllDealItemSoldException {
        if(this.dealMap.get(id) == null) throw new DealNotFoundException("Wrong deal id, deal do not exist");

        Deal deal = this.dealMap.get(id);

        if(deal.itemCount <= 0) throw new AllDealItemSoldException("ALl item out of stock");

        deal.itemCount = itemCount;

        return deal;
    }





}
