package LLD.LldQuestions.TicTacToe.model;

import java.io.PipedOutputStream;

public class Board {

    private final String[][] board;

    public Board(int n) {
        this.board = new String[n][n];
        for (String[] strings : this.board) {
            for (int j = 0; j < this.board.length; j++) {
                strings[j] = "";
            }
        }
    }

    public void printBoard() {
        for (String[] strings: this.board){
            for(int i=0; i<strings.length; i++) {
                System.out.print(strings[i] + " ");
            }
            System.out.println();
        }
    }

    public void setSymbolPosition(final Position position, final String symbol){
        int x = position.getX();
        int y = position.getY();

        this.board[x][y] = symbol;
    }

    public String getSymbolPosition(final Position position) {
        int x = position.getX();
        int y = position.getY();

        return this.board[x][y];
    }

    public String[][] getBoard() {
        return board;
    }
}
