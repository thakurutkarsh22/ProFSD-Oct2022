package LLD.LldQuestions.battleshipGame.models;

import LLD.LldQuestions.battleshipGame.exception.CoordinateOutsideBoundaryException;

import java.util.List;

public class Board {

    List<BoardItem> ships;

    Boundary boundary;
    List<Coordinates> bombedCoordinates;


    public Board(List<BoardItem> ships, Boundary boundary, List<Coordinates> bombedCoordinates) {
        this.ships = ships;
        this.bombedCoordinates = bombedCoordinates;
        this.boundary = boundary;
    }

//    Board will recieve the bombing that it needs to store.

    public void takeHit(Coordinates hitCoordinate) throws CoordinateOutsideBoundaryException {
//        check if the localtion is alright
        if(!this.boundary.isCoordinateInsideBoundary(hitCoordinate)) {
            throw new CoordinateOutsideBoundaryException();
        }
        this.bombedCoordinates.add(hitCoordinate);
    }

    public boolean isAllShipsDestroyed() {
        for(BoardItem item: this.ships) {
            boolean isItemSunk = item.isShipSunked(this.bombedCoordinates);
            if(!isItemSunk) return  false;
        }

        return true;
    }


}
