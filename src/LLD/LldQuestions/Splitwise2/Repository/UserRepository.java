package LLD.LldQuestions.Splitwise2.Repository;

import LLD.LldQuestions.Splitwise2.models.User;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private Map<String, User> userMap = new HashMap<>();
    private Map<String, Map<String, Double>> balanceSheet = new HashMap<>();

    public void addUser(User user) {
        this.userMap.put(user.getId(), user);
        this.balanceSheet.put(user.getId(), new HashMap<String, Double>());
    }

    public Map<String, User> getUserMap() {
        return this.userMap;
    }

    public Map<String, Map<String, Double>> getBalanceSheet() {
        return this.balanceSheet;
    }

}
