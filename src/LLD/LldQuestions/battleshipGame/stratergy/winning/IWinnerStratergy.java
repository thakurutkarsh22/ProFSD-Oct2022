package LLD.LldQuestions.battleshipGame.stratergy.winning;

import LLD.LldQuestions.battleshipGame.models.Player;

import java.util.List;

public interface IWinnerStratergy {
    Player getWinner(List<Player> allPlayers);
}
