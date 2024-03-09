package LLD.LldQuestions.battleshipGame.models;

import java.util.List;

public class BoardItem {
    String name;

    Boundary boundary;

    public BoardItem(String name, Boundary boundary) {
        this.name = name;
        this.boundary = boundary;
    }

    public boolean isShipSunked(List<Coordinates> hitCoordinates) {
        List<Coordinates> boardItemCoordinates = this.boundary.getAllCoordinates();

        for(Coordinates coordinate: hitCoordinates) {
            if(!boardItemCoordinates.contains(coordinate)) {
                return false;
            }
        }
        return true;
    }

    public boolean containsCoordinate(Coordinates coordinate) {
        return this.boundary.isCoordinateInsideBoundary(coordinate);
    }

}
