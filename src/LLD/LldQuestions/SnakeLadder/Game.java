package LLD.LldQuestions.SnakeLadder;

import java.util.Deque;
import java.util.LinkedList;

public class Game {
    Board board;
    Dice dice;
    Deque<Player> playersList = new LinkedList<>();
    Player winner;

    public Game() {
        initializeGame();
    }

    private void initializeGame() {
        this.board = new Board(10, 5, 4);
        this.dice = new Dice(1);
        this.winner = null;
        addPlayers();
    }

    private void addPlayers() {
        Player player1 = new Player("p1", 0);
        Player player2 = new Player("p2", 0);
        this.playersList.add(player1);
        this.playersList.add(player2);
    }

    public void startGame() {
        while (this.winner == null) {
//            check the  player who has the turn
            Player playerTurn = this.findPlayerTurn();
            System.out.println("player turn is:" + playerTurn.id + " current position is: " + playerTurn.currentPosition);

//            roll the dice
            int diceNumber = this.dice.rollDice();

//            get the new position
            int playerNewPosition = playerTurn.currentPosition + diceNumber;
            playerNewPosition = jumpCheck(playerNewPosition);
            playerTurn.currentPosition = playerNewPosition;

            System.out.println("player turn is:" + playerTurn.id + " new position is: " + playerNewPosition);

//            check for winning position
            if(playerNewPosition >= this.board.cells.length * this.board.cells.length -1) {
                this.winner = playerTurn;
            }
        }

        System.out.println("WINNER IS: " + winner.id);
    }

    private Player findPlayerTurn() {
        Player playerTurns = this.playersList.removeFirst();
        this.playersList.addLast(playerTurns);
        return playerTurns;
    }

    private int jumpCheck(int playerNewPosition) {
        if(playerNewPosition > this.board.cells.length*this.board.cells.length-1) {
            return playerNewPosition;
        }

        Cell cell = this.board.getCell(playerNewPosition);
        if(cell.jump != null && cell.jump.start == playerNewPosition) {
            String jumpBy = (cell.jump.start < cell.jump.end) ? "ladder" : "snake";
            System.out.println("jump done by: " + jumpBy);
            return cell.jump.end;
        }
        return playerNewPosition;
    }
}
