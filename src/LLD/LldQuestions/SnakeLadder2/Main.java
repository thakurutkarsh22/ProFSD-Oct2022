package LLD.LldQuestions.SnakeLadder2;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidDIceStratergyException;
import LLD.LldQuestions.SnakeLadder2.Exception.InvalidInputException;
import LLD.LldQuestions.SnakeLadder2.Model.BoardEntity.Jump;
import LLD.LldQuestions.SnakeLadder2.Model.Player;
import LLD.LldQuestions.SnakeLadder2.Service.GameService;

import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws InvalidInputException, InvalidDIceStratergyException {

        Scanner scn = new Scanner(System.in);

        System.out.println("Enter Board size >0");

        int boardSize = scn.nextInt();

        System.out.println("Enter Number of Snakes");

        int totalSnakes = scn.nextInt();
        List<Jump> snakes = new LinkedList<>();
        for(int i=0;i < totalSnakes;i++) {
            System.out.println("Enter Detail of Snake " + i + 1);
            int start = scn.nextInt();
            int end = scn.nextInt();

            if(start <= end) {
                throw new InvalidInputException("Start can never be less then end");
            }
            if(start >= boardSize* boardSize) {
                throw new InvalidInputException("Snake should be in board");
            }

            Jump jump = new Jump(start, end);
            snakes.add(jump);
        }

        System.out.println("Enter Number of Ladder");
        int totalLadder = scn.nextInt();
        List<Jump> ladder = new LinkedList<>();
        for(int i=0;i < totalLadder;i++) {
            System.out.println("Enter Detail of ladder " + i + 1);
            int start = scn.nextInt();
            int end = scn.nextInt();

            if(start >= end) {
                throw new InvalidInputException("end can never be less then start");
            }
            if(end >= boardSize* boardSize) {
                throw new InvalidInputException("Ladder should be in board");
            }

            Jump jump = new Jump(start, end);
            ladder.add(jump);
        }

        List<Jump> mines = new LinkedList<>();
        List<Jump> crocodile = new LinkedList<>();

        System.out.println("Enter number of players");
        int noOfPlayers = scn.nextInt();
        List<Player> players = new LinkedList<>();
        for (int i = 0; i < noOfPlayers; i++) {
            Player player = new Player(UUID.randomUUID().toString(), "Player " + i + 1, 0);
            players.add(player);
        }

        System.out.println("Enter no. of dice");
        int noOfDice = scn.nextInt();

        System.out.println("Enter Stratergy 1 2 or 3");
        int stratergyType = scn.nextInt();



        GameService game = new GameService(boardSize, ladder, mines, snakes, crocodile, players,stratergyType, noOfDice);
        game.startGame();
    }
}
