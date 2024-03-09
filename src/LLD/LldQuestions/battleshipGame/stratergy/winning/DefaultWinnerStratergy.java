package LLD.LldQuestions.battleshipGame.stratergy.winning;

import LLD.LldQuestions.battleshipGame.models.Player;

import java.util.ArrayList;
import java.util.List;

public class DefaultWinnerStratergy implements IWinnerStratergy{
    @Override
    public Player getWinner(List<Player> allPlayers) {
        List<Player> alivePlayers = new ArrayList<>();
        for(Player player: allPlayers) {
            if(!player.isAllShipSinked()) {
                alivePlayers.add(player);
            }
        }

        if(alivePlayers.size() == 1){
            return alivePlayers.get(0);
        }

        return null;
    }
}
