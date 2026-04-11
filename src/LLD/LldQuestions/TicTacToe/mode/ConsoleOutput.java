package LLD.LldQuestions.TicTacToe.mode;

import LLD.LldQuestions.TicTacToe.model.Board;

public class ConsoleOutput implements IOutput{

    @Override
    public void printBoard(Board board) {
        for(String[] strs: board.getBoard()){
            for(int j=0;j<board.getBoard().length;j++){
                System.out.print(strs[j]+" ");
            }
            System.out.println();
        }
    }
}
