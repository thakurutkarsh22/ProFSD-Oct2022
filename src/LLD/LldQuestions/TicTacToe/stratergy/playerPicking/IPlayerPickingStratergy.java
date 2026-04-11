package LLD.LldQuestions.TicTacToe.stratergy.playerPicking;

import LLD.LldQuestions.TicTacToe.exceptions.NoPlayersFound;
import LLD.LldQuestions.TicTacToe.model.Player;

import java.util.List;

public interface IPlayerPickingStratergy {
    public int firstPlayer(List<Player> players) throws NoPlayersFound;

    public int nextPlayerTurn(List<Player> players, int currentPlayerIndex);
}
