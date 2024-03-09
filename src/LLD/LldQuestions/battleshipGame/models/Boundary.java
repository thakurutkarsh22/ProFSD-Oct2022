package LLD.LldQuestions.battleshipGame.models;

import java.util.ArrayList;
import java.util.List;

public class Boundary {
//    This I think as a rectangular boundary
    Coordinates topLeft;
    Coordinates bottomRight;

    public Boundary(Coordinates topLeft, Coordinates bottomRight) {
        this.topLeft = topLeft;
        this.bottomRight = bottomRight;
    }

    public List<Coordinates> getAllCoordinates() {
        List<Coordinates> list = new ArrayList<>();

        for (int i = topLeft.x; i < bottomRight.x; i++) {
            for (int j = topLeft.y; j >= bottomRight.y ; j--) {
                list.add(new Coordinates(i, j));
            }
        }

        return  list;
    }

    public boolean isCoordinateInsideBoundary(Coordinates coordinate) {
        int topLeftx = this.topLeft.x;
        int topLefty = this.topLeft.y;

        int bottoomRightx  = this.bottomRight.x;
        int bottoomRighty  = this.bottomRight.y;

        int x = coordinate.x;
        int y = coordinate.y;

        if(x >= topLeftx && x <= bottoomRightx && y>= bottoomRighty && y <= topLefty) {
            return true;
        } else {
            return false;
        }

    }
}
