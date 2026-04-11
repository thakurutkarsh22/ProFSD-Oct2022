package LLD.LldQuestions.DealService.service;

import LLD.LldQuestions.DealService.model.User;
import LLD.LldQuestions.DealService.repository.UserRepository;

public class UserService {

    UserRepository  userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUser(int userId){
        return this.userRepository.getUser(userId);
    }

    public void createUser(int id, String name, String email, String phone) {
        this.userRepository.createUser(new User(id, name, email, phone));
    }

}
