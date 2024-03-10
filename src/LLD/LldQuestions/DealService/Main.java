package LLD.LldQuestions.DealService;

import LLD.LldQuestions.DealService.model.Deal;
import LLD.LldQuestions.DealService.model.Product;
import LLD.LldQuestions.DealService.model.UserDealMap;
import LLD.LldQuestions.DealService.repository.DealRepository;
import LLD.LldQuestions.DealService.repository.UserDealMapRepository;
import LLD.LldQuestions.DealService.service.DealService;
import LLD.LldQuestions.Splitwise.Singleton.User;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {




    public static void main(String[] args) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("dd-m-yyyy hh:mm:ss a");

//    create users

            User utkarsh = new User("1", "utkasrh");
            User anuj = new User("2", "anuj");

//    create deals
//            Deal deal1 = new Deal("1", formatter.parse("10-03-2024 10:00:00 AM"),
//                    formatter.parse("10-03-2024 12:00:00 AM"),
//                    new Product(1, "samsung phone", 5000), 2000, 10 );

//    Deal service
            DealService dealService = new DealService(new DealRepository(),  new UserDealMapRepository());

            dealService.createDeal("1", formatter.parse("10-03-2024 10:00:00 AM"),
                    formatter.parse("10-03-2024 12:00:00 AM"),
                    new Product(1, "samsung phone", 5000), 2000, 10);



        } catch (Exception error) {

        }



    }


}
