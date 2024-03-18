package LLD.LldQuestions.TicTacToe.stratergy.playerPicking;

import LLD.LldQuestions.TicTacToe.exceptions.NoPlayersFound;
import LLD.LldQuestions.TicTacToe.model.Player;

import java.util.List;

public class RoundRobinPlayerPickingStratergy implements IPlayerPickingStratergy{
    @Override
    public int firstPlayer(List<Player> players) throws NoPlayersFound {
        if(players.size() == 0) {
            throw new NoPlayersFound("No player found");
        }
        return 0;
    }

    @Override
    public int nextPlayerTurn(List<Player> players, int currentPlayerIndex) {
        return (currentPlayerIndex + 1) % players.size();
    }
}
