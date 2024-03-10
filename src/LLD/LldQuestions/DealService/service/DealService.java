package LLD.LldQuestions.DealService.service;

import LLD.LldQuestions.DealService.exception.*;
import LLD.LldQuestions.DealService.model.Deal;
import LLD.LldQuestions.DealService.model.Product;
import LLD.LldQuestions.DealService.repository.DealRepository;
import LLD.LldQuestions.DealService.repository.UserDealMapRepository;

import java.util.Date;

public class DealService {
//    CRUD deal and Claim deal....
    DealRepository dealRepository;
    UserDealMapRepository userDealMapRepository;

    public DealService(DealRepository dealRepository, UserDealMapRepository userDealMapRepository) {
        this.dealRepository = dealRepository;
        this.userDealMapRepository = userDealMapRepository;
    }

    public Deal getDeal(String id) {
        return this.dealRepository.getDeal(id);
    }

    public void createDeal(String id, Date startTime, Date endTime, Product product, int discountedPrice, int itemCount) {
        this.dealRepository.createDeal(new Deal(id, startTime, endTime, product, discountedPrice, itemCount));
    }

    public void updateDeal(String id, Date endTime) throws DealNotFoundException, DealAlreadyExpired {
        this.dealRepository.updateDeal(id, endTime);
    }

    public void updateDeal(String id, int itemNumber) throws AllDealItemSoldException, DealNotFoundException {
        this.dealRepository.updateDeal(id, itemNumber);
    }

    public void claimDeal (int userId, String dealId) throws NoItemLeftException, AllDealItemSoldException, DealNotFoundException, DealAlreadyUsed {
        Deal deal = this.getDeal(dealId);

        if(deal.itemCount <= 0) throw  new NoItemLeftException();
        if(this.userDealMapRepository.checkIfDealAlreadyUsed(userId, dealId)) throw new DealAlreadyUsed();
        this.dealRepository.updateDeal(dealId, deal.itemCount - 1);
        this.userDealMapRepository.createUserDealMapping(userId, deal);
    }



}
