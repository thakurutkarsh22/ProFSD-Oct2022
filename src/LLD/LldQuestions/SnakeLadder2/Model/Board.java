package LLD.LldQuestions.SnakeLadder2.Model;

import LLD.LldQuestions.SnakeLadder2.Model.BoardEntity.Jump;

import java.util.List;

public class Board {
//    cells
    Cell[][] cells;

//    so idea is whenever you are creting the board create its entities as well


    public Board(int size, List<Jump> ladder, List<Jump> mine, List<Jump> snake, List<Jump> crocodile) {
//        create board
        intitalizeBoard(size);

//        place entities on the board
        putEntitiesOnBoard(ladder, mine, snake, crocodile);
    }

    public void putEntitiesOnBoard(List<Jump> ladder, List<Jump> mine, List<Jump> snake, List<Jump> crocodile) {
        putPerticularEntity(ladder);
        putPerticularEntity(mine);
        putPerticularEntity(snake);
        putPerticularEntity(crocodile);
    }

    private void putPerticularEntity(List<Jump> entity) {
        for (Jump jump: entity) {
            int start = jump.start; // this talks about from weher in the board jumps starts
            // search on the board
            Cell cell = findCell(start);
            cell.setJump(jump);
        }
    }

    public Cell findCell(int cellid) {
        int sizeOfBoard = this.cells.length;
        int boardRow = cellid / sizeOfBoard;
        int boardColumn = cellid % sizeOfBoard;
        return this.cells[boardRow][boardColumn];
    }

    public void intitalizeBoard(int size) {
        cells = new Cell[size][size];

        int count = 0;
        for (int i = 0 ; i< size; i++) {
            for (int j = 0; j < size; j++) {
                this.cells[i][j] = new Cell();
                this.cells[i][j].setId(count++);
            }
        }

    }

}
