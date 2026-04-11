package LLD.LldQuestions.TicTacToe.repository;

import LLD.LldQuestions.TicTacToe.model.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {

//    player id, vs detail of player storage
    HashMap<Integer, Player> userRepo = new HashMap<>();

    public List<Player> getUsersList() {
        List<Player> al = new ArrayList();
        for(Map.Entry<Integer, Player> entry : this.userRepo.entrySet()){
            al.add(entry.getValue());
        }
        return al;
    }

    public void addPlayer(Player player) {
        int playerId = player.getId();
        this.userRepo.put(playerId, player);
    }


}
