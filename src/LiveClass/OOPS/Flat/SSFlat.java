package LiveClass.OOPS.Flat;

public class SSFlat {

    int floors;
    String color;
    boolean lift;
    int length;
    int bredth;
    int area;

// Paramaterized constructor ...



    public SSFlat(int floor, String c, boolean lif, int len, int bre, int a) {
        floors = floor;
        color = c;
        lift = lif;
        length = len;
    }

//    default constructor .....
    public  SSFlat() {

    }

    public int money() {
        int helipad = 100;
        int tennisCourt = 90;
        int swimmingPool = 1000;

        return helipad + tennisCourt + swimmingPool;
    }


}
