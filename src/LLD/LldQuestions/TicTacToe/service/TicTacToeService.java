package LLD.LldQuestions.TicTacToe.service;

import LLD.LldQuestions.TicTacToe.exceptions.NoPlayersFound;
import LLD.LldQuestions.TicTacToe.model.Board;
import LLD.LldQuestions.TicTacToe.model.Player;
import LLD.LldQuestions.TicTacToe.model.Position;
import LLD.LldQuestions.TicTacToe.stratergy.playerPicking.IPlayerPickingStratergy;
import LLD.LldQuestions.TicTacToe.validators.GameValidator;

import java.util.List;
import java.util.Scanner;

public class TicTacToeService {

    UserService userService;
    Board board;

    IPlayerPickingStratergy playerPickingStratergy;

    Scanner scn;

    public TicTacToeService(UserService userService, int boardLength,
                            IPlayerPickingStratergy playerPickingStratergy) {
        this.userService = userService;
        this.board = new Board(boardLength);
        this.playerPickingStratergy = playerPickingStratergy;
        this.scn = new Scanner(System.in);
    }

    private List<Player> getAllPlayers() {
        return this.userService.getUsersList();
    }

    public void playGame() throws NoPlayersFound {
        try {
            int currentPlayerIndex = this.playerPickingStratergy.firstPlayer(this.getAllPlayers());
            while (true) {
                Player currentPlayer = this.getAllPlayers().get(currentPlayerIndex);

                System.out.println("enter the coordinate where you want tour symbol");
                int x = this.scn.nextInt();
                int y = this.scn.nextInt();
                Position position = new Position(x, y);

                if(GameValidator.
                        checkPositionInsideBoardAndNotOccupied(this.board, position)) {
                    this.board.setSymbolPosition(position, currentPlayer.getSymbol());
                    this.board.printBoard();

//                    check winner
                    if(this.checkboard(x,y, currentPlayer.getSymbol())){
                        System.out.println("WInner is " +
                                currentPlayer.getName() + currentPlayer.getSymbol());
                        return;
                    }

                    currentPlayerIndex = this.playerPickingStratergy.
                            nextPlayerTurn(this.getAllPlayers(), currentPlayerIndex);

                }

            }

        } catch (NoPlayersFound e) {
            throw new RuntimeException(e);
        }

    }

    private boolean checkboard(int row,int col,String symbol){
        boolean winRow = true, winCol = true, winLeft = true, winRight = true;
        for(int i=0;i<board.getBoard().length;i++){
            if(!board.getSymbolPosition(new Position(row,i)).equals(symbol)){
                winRow = false;
            }
            if(!board.getSymbolPosition(new Position(i,col)).equals(symbol)){
                winCol = false;
            }
            if(!board.getSymbolPosition(new Position(i,i)).equals(symbol)){
                winLeft = false;
            }
            if(!board.getSymbolPosition(new Position(i,board.getBoard().length-i-1))
                    .equals(symbol)){
                winRight =false;
            }
        }
        return winRow || winCol || winLeft || winRight;
    }




}
