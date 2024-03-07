package LLD.LldQuestions.SnakeLadder2.Service;

import LLD.LldQuestions.SnakeLadder2.Exception.InvalidDIceStratergyException;
import LLD.LldQuestions.SnakeLadder2.Model.Board;
import LLD.LldQuestions.SnakeLadder2.Model.BoardEntity.Jump;
import LLD.LldQuestions.SnakeLadder2.Model.Cell;
import LLD.LldQuestions.SnakeLadder2.Model.Player;
import LLD.LldQuestions.SnakeLadder2.Stratergy.Dice;
import LLD.LldQuestions.SnakeLadder2.Stratergy.MaxDiceStratergy;
import LLD.LldQuestions.SnakeLadder2.Stratergy.MinDiceStratergy;
import LLD.LldQuestions.SnakeLadder2.Stratergy.SumDiceStratergy;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class GameService {
    private Board board;
    private Deque<Player> players = new LinkedList<>();
    private Dice dice;

    private int boradLength;

    private Player winner;

    public GameService(int boardSize, List<Jump> ladder, List<Jump> mine, List<Jump> snake,List<Jump> crocodile,
                       List<Player> players, int stratergyType, int diceCount) throws InvalidDIceStratergyException {
//        set board length
        this.boradLength = boardSize;


//        intialize Game Board
        this.board = new Board(boardSize, ladder, mine, snake, crocodile);



//        Initialize players

        this.players.addAll(players);

//        initialize Dice -> but before we need to decide the stratergy (min, max, sum dice stratergy)

        if(stratergyType == 1) {
            dice = new MaxDiceStratergy(diceCount);
        } else if(stratergyType == 2) {
            dice = new MinDiceStratergy(diceCount);
        } else if(stratergyType == 3) {
            dice = new SumDiceStratergy(diceCount);
        } else {
            throw new InvalidDIceStratergyException("Invalid DIce Stratergy");
        }
    }

    public void startGame() {
        while (this.winner == null) {
//            find the player turn
            Player player = nextPlayerTurn();

//            roll the dice
            int playerDice = this.dice.rollDice();

//            update the player position but before that I could check and validate the poition
            int playerNewPosition = player.playerPosition + playerDice;
            boolean isPlayerAllowedToMove = validateNewPlayerPosition(playerNewPosition);

            if(!isPlayerAllowedToMove) {
                System.out.println("hey player bad Luck");
                playerNewPosition = player.playerPosition;
            }

//


//            Now If move is valid
//            TEST 1: Check if at new position do we ahve a jump and set the player new poistion

            playerNewPosition = jumpCheck(playerNewPosition, player);

//            TEST 2 - check for the other players if ther are on the same cell or not

            for (Player otherPlayer: this.players) {
                if(otherPlayer.playerName != player.playerName) {
                    int currentPositionOfOtherPlayer = otherPlayer.playerPosition;
                    int currentPositionOfPLAYER = playerNewPosition;

                    if(currentPositionOfPLAYER == currentPositionOfOtherPlayer) {
//                        update other player current position to 0, YOU BIT THAT PLAYER
                        otherPlayer.setPlayerPosition(0);
                    }
                }
            }

//            Set the player who is playing its latest position

            player.setPlayerPosition(playerNewPosition);

//            check winning condition

            if(player.playerPosition == this.boradLength* this.boradLength - 1) {
                this.winner = player;
                System.out.println("here is the winner " + this.winner.playerName);
            }

        }
    }

    private int jumpCheck(int playerPosition, Player player) {
        Cell cell = this.board.findCell(playerPosition);

        if(cell.getJump() != null && cell.getJump().start == playerPosition) {
            return cell.getJump().end;
        } else {
            return playerPosition;
        }
    }

    private boolean validateNewPlayerPosition(int position) {
        if(position > this.boradLength * this.boradLength - 1) {
            return false;
        } else {
            return true;
        }
    }

    private Player nextPlayerTurn() {
        Player playerTurn = this.players.getFirst();
        this.players.removeFirst();
        this.players.addLast(playerTurn);
        return playerTurn;
    }


}
