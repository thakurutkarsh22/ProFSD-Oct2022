package LLD.LldQuestions.battleshipGame.stratergy.playerPicking;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidInputException;
import LLD.LldQuestions.battleshipGame.models.Player;

import java.util.List;

public interface IPlayerPickingStratergy {

    public int firstPlayer(List<Player> allPlayers) throws InvalidInputException;

    public int pickNextPlayer(int currentPlayerIndex, List<Player> allPlayers);
}
