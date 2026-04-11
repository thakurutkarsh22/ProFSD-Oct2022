package LLD.LldQuestions.battleshipGame.service;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidInputException;
import LLD.LldQuestions.battleshipGame.exception.NoSuchPlayerException;
import LLD.LldQuestions.battleshipGame.models.Player;
import LLD.LldQuestions.battleshipGame.stratergy.playerPicking.IPlayerPickingStratergy;
import LLD.LldQuestions.battleshipGame.stratergy.winning.IWinnerStratergy;

import java.util.List;
import java.util.Scanner;

public class GameService {

    public List<Player> players;
    public IPlayerPickingStratergy nextPlayerStratergy;
    public IWinnerStratergy winnerStratergy;

    public GameService(List<Player> players, IWinnerStratergy winnerStratergy,
                       IPlayerPickingStratergy nextPlayerStratergy) {
        this.players = players;
        this.nextPlayerStratergy = nextPlayerStratergy;
        this.winnerStratergy = winnerStratergy;
    }


    public void start(Scanner scn) throws InvalidInputException, NoSuchPlayerException {
        int currentPlayerIndex = nextPlayerStratergy.firstPlayer(this.players);
        System.out.println("game starting");

        while(true) {
            Player currentPlayer = this.players.get(currentPlayerIndex);
            System.out.println("Current player playing " + currentPlayer.getName());

//            now player will take a chance to hit other players
            try {
                currentPlayer.playerChanceTarget(this.players, scn);

            } catch (NoSuchPlayerException error) {
                System.out.println("hit was out of bounds");
            }

            Player winner = winnerStratergy.getWinner(this.players);

            if(winner != null){
//                winner found
                System.out.println(winner.getName() + " is the winner");
            }

            currentPlayerIndex = nextPlayerStratergy.pickNextPlayer(currentPlayerIndex, this.players);
        }
    }


}
