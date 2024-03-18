package LLD.LldQuestions.TicTacToe;

import LLD.LldQuestions.TicTacToe.exceptions.NoPlayersFound;
import LLD.LldQuestions.TicTacToe.model.Player;
import LLD.LldQuestions.TicTacToe.repository.UserRepository;
import LLD.LldQuestions.TicTacToe.service.TicTacToeService;
import LLD.LldQuestions.TicTacToe.service.UserService;
import LLD.LldQuestions.TicTacToe.stratergy.playerPicking.RoundRobinPlayerPickingStratergy;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws NoPlayersFound {

//        set players

        List<Player> players = new ArrayList<>();
        Player p1 = new Player(1, "player1", "0");
        Player p2 = new Player(2, "player2", "x");

        UserService userService = new UserService(new UserRepository());
        userService.addPlayer(p1);
        userService.addPlayer(p2);


//        set Game
        int boardLength = 3;
        TicTacToeService ticTacToeService = new TicTacToeService(userService, boardLength,
               new RoundRobinPlayerPickingStratergy());

//        start game

        try {
            ticTacToeService.playGame();
        } catch (NoPlayersFound e) {
            throw new RuntimeException(e);
        }
    }
}
