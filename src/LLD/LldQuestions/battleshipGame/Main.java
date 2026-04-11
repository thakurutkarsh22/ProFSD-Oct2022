package LLD.LldQuestions.battleshipGame;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidInputException;
import LLD.LldQuestions.battleshipGame.exception.NoSuchPlayerException;
import LLD.LldQuestions.battleshipGame.models.*;
import LLD.LldQuestions.battleshipGame.service.GameService;
import LLD.LldQuestions.battleshipGame.stratergy.playerPicking.RoundRobinPickingStratergy;
import LLD.LldQuestions.battleshipGame.stratergy.winning.DefaultWinnerStratergy;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws InvalidInputException, NoSuchPlayerException {
        Scanner scn = new Scanner(System.in);
        Boundary boardBoundary = new Boundary( new Coordinates(0, 10), new Coordinates(10, 0 ));

        List<Player> players = new ArrayList<>();
        players.add(getPlayer1(scn, boardBoundary));
        players.add(getPlayer2(scn, boardBoundary));
        GameService game = new GameService(
                players,
                new DefaultWinnerStratergy(),
                new RoundRobinPickingStratergy()
        );

        game.start(scn);

    }

    private  static Player getPlayer1(Scanner scn, Boundary boardBoundary) {

        BoardItem ship1 = new BoardItem("carrier",
                new Boundary(new Coordinates(0, 7), new Coordinates(4,7)));
        BoardItem ship2 = new BoardItem("battleship",
                new Boundary(new Coordinates(4, 1), new Coordinates(4,4)));
        BoardItem ship3 = new BoardItem("cruiser",
                new Boundary(new Coordinates(7, 3), new Coordinates(7,5)));
        BoardItem ship4 = new BoardItem("destroyer",
                new Boundary(new Coordinates(4, 9), new Coordinates(6,9)));
        BoardItem ship5 = new BoardItem("submarine",
                new Boundary(new Coordinates(3, 6), new Coordinates(4,3)));

        ArrayList<BoardItem> ships = new ArrayList<>();
        ships.add(ship1);
        ships.add(ship2);
        ships.add(ship3);
        ships.add(ship4);
        ships.add(ship5);

        Board board = new Board(ships, boardBoundary, new ArrayList<>());

        return new Player("player1", 1, board);
    }


    private  static Player getPlayer2(Scanner scn, Boundary boardBoundary) {

        BoardItem ship1 = new BoardItem("carrier",
                new Boundary(new Coordinates(0, 7), new Coordinates(4,7)));
        BoardItem ship2 = new BoardItem("battleship",
                new Boundary(new Coordinates(4, 1), new Coordinates(4,4)));
        BoardItem ship3 = new BoardItem("cruiser",
                new Boundary(new Coordinates(7, 3), new Coordinates(7,5)));
        BoardItem ship4 = new BoardItem("destroyer",
                new Boundary(new Coordinates(4, 9), new Coordinates(6,9)));
        BoardItem ship5 = new BoardItem("submarine",
                new Boundary(new Coordinates(3, 6), new Coordinates(4,3)));

        ArrayList<BoardItem> ships = new ArrayList<>();
        ships.add(ship1);
        ships.add(ship2);
        ships.add(ship3);
        ships.add(ship4);
        ships.add(ship5);

        Board board = new Board(ships, boardBoundary, new ArrayList<>());

        return new Player("player1", 2, board);
    }

}
