package LLD.LldQuestions.SnakeLadder;

import java.util.concurrent.ThreadLocalRandom;

public class Board {
    Cell[][] cells;

    Board(int boardSize, int numberOfSnakes, int numberOfLadders) {
        initializeCells(boardSize);
        adSnakesLadders(this.cells, numberOfSnakes, numberOfLadders);
    }

    private void initializeCells(int boardSize) {
        this.cells = new Cell[boardSize][boardSize];

        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                Cell cellObj = new Cell();
                this.cells[i][j] = cellObj;
            }
        }
    }

    private void adSnakesLadders(Cell[][] cells, int numberOfSnakes, int numberOfLadders) {
        int upperBoundOfCells = cells.length*cells.length-1;
        while (numberOfSnakes > 0) {
            int snakeHead = ThreadLocalRandom.current().nextInt(1, upperBoundOfCells);
            int snakeTail = ThreadLocalRandom.current().nextInt(1, upperBoundOfCells);

            if(snakeTail >= snakeHead) {
                continue;
            }

            Jump snakeObj = new Jump();
            snakeObj.start = snakeHead;
            snakeObj.end = snakeTail;

            Cell cell = getCell(snakeHead);
            cell.jump = snakeObj;

            numberOfSnakes--;
        }

        while (numberOfLadders > 0) {
            int ladderStart = ThreadLocalRandom.current().nextInt(1, upperBoundOfCells);
            int ladderEnd = ThreadLocalRandom.current().nextInt(1, upperBoundOfCells);

            if(ladderStart >= ladderEnd) {
                continue;
            }

            Jump ladderObj = new Jump();
            ladderObj.start = ladderStart;
            ladderObj.end = ladderEnd;

            Cell cell = getCell(ladderStart);
            cell.jump = ladderObj;
            numberOfLadders--;
        }
    }

    Cell getCell(int playerPosition) {
        int boardRow = playerPosition / cells.length;
        int boardColumn = playerPosition % cells.length;
        return cells[boardRow][boardColumn];
    }
}
