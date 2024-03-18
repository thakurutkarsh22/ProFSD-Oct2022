package LLD.LldQuestions.TicTacToe.service;

import LLD.LldQuestions.TicTacToe.model.Player;
import LLD.LldQuestions.TicTacToe.repository.UserRepository;

import java.util.List;

public class UserService {

    UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Player> getUsersList() {
        return this.userRepository.getUsersList();
    }

    public void addPlayer(Player player) {
        this.userRepository.addPlayer(player);
    }

}
