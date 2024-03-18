package LLD.LldQuestions.TicTacToe.validators;

import LLD.LldQuestions.TicTacToe.model.Board;
import LLD.LldQuestions.TicTacToe.model.Position;

public class GameValidator {


    public static boolean checkPositionInsideBoardAndNotOccupied (Board board, Position position) {
        int x  = position.getX();
        int y = position.getY();
        String item = board.getBoard()[x][y];
        int boardLength = board.getBoard().length;

        return x>=0 && y>=0 && x <boardLength && y < boardLength && item == "" ;
    }

}
