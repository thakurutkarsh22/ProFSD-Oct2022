package LLD.LldQuestions.battleshipGame.stratergy.playerPicking;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidInputException;
import LLD.LldQuestions.battleshipGame.models.Player;

import java.util.List;

public class RoundRobinPickingStratergy implements IPlayerPickingStratergy {

    @Override
    public int firstPlayer(List<Player> allPlayers) throws InvalidInputException {
        if(allPlayers.size() == 0) {
            throw new InvalidInputException("invalid input ");
        }
        return 0;
    }
    @Override
    public int pickNextPlayer(int currentPlayerIndex, List<Player> allPlayers) {
//        round
        return (currentPlayerIndex + 1) % allPlayers.size();
    }
}
