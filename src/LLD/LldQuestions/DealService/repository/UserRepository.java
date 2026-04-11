package LLD.LldQuestions.DealService.repository;

import LLD.LldQuestions.DealService.model.User;

import java.util.HashMap;

public class UserRepository {

//    user database

    HashMap<Integer, User> userMap = new HashMap<>();

    public User getUser(int userId) {
        return this.userMap.get(userId);
    }

    public User createUser(User user) {
        this.userMap.put(user.getId(), user);
        return user;
    }

}
