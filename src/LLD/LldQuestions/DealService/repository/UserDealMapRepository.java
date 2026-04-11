package LLD.LldQuestions.DealService.repository;

import LLD.LldQuestions.DealService.model.Deal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UserDealMapRepository {

//    Storing userd with the list of deal ids
    HashMap<Integer, List<Deal>> userDealMap = new HashMap<>();


    public boolean checkIfDealAlreadyUsed(int userID, String dealId) {
        if(!this.userDealMap.containsKey(userID)) {
            return false;
        }

        List<Deal> deals = this.userDealMap.get(userID).stream().filter(x -> x.getId() == dealId).toList();
        if(deals.isEmpty()) {
            return false;
        }

        return true;
    }

    public void createUserDealMapping(int userId, Deal deal) {
        if(!this.userDealMap.containsKey(userId)) {
            this.userDealMap.put(userId, new ArrayList<>());
        }

        this.userDealMap.get(userId).add(deal);
    }
}
