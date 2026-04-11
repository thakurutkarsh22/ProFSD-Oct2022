package LLD.LldQuestions.battleshipGame.models;

import LLD.LldQuestions.battleshipGame.exception.NoSuchPlayerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Player {
    String name;
    int id;

    Board board;

    public Player(String name, int id, Board board) {
        this.name = name;
        this.id = id;
        this.board = board;
    }

    public String getName() {
        return this.name;
    }


    public boolean isAllShipSinked() {
        return this.board.isAllShipsDestroyed();
    }

//    because the player needs to hit the best possible target from the list of enemies
    public PlayerChanceTarget playerChanceTarget(List<Player> allPlayers, Scanner scn) throws NoSuchPlayerException {
        List<Player> enemyPlayers = new ArrayList<>();

        for(Player p : allPlayers) {
            if(p.id != this.id) {
                enemyPlayers.add(p);
            }
        }

//        Now think about the chance that a player will take, a player will think according to the situation
//        and will change the stratergy accordingly. SO TAKE INPUT FOR THAT

//        Scanner scn = new Scanner(System.in);
        System.out.println("Choose Player id you want to hit");
        int enemyId = scn.nextInt();

        Player enemyPlayer = null;

        for(Player p : allPlayers) {
            if(p.id == enemyId) {
                enemyPlayer = p;
            }
        }

        if(enemyPlayer == null) {
            throw new NoSuchPlayerException();
        }

        System.out.println("Enter coordinates of the enemy you want to target");
        int x = scn.nextInt();
        int y = scn.nextInt();

        return new PlayerChanceTarget(enemyPlayer, new Coordinates(x, y));

    }
}
