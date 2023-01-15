package LiveClass.OOPS.Inheritance;

public class SSFlat extends GovtFlat {

    int floors = 1;
    String color = "red";
    boolean lift = false;
    int length = 200;
    int bredth = 300;
    int area = 200 * 300;

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

    public SSFlat(String color) {
        this.color = color;
    }

    public int money() {
        int helipad = 100;
        int tennisCourt = 90;
        int swimmingPool = 1000;

        return helipad + tennisCourt + swimmingPool;
    }

    public static void abc() {
        System.out.println("print abc");
    }


}

