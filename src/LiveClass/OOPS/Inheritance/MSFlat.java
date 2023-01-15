package LiveClass.OOPS.Inheritance;

public class MSFlat extends SSFlat {
    boolean lift = true;
    int flatId = 302;
    int height = 20;
    String parking = "yes";

    int length = 12;
    int bredth = 1;

    MSFlat() {
        super(); // this is happening behind the scenes
    }

    MSFlat(int flatId, int height, String parking, String color) {
        super();
        this.height = height;
        this.parking = parking;
        this.flatId = flatId;

    }

    public int volumeOfMS() {
        super.abc();
        System.out.println(super.money());
        return this.height * super.length * super.bredth;
    }

}

//super keyword should be the first keyword in an Constructor
//super keyword refers to the parent .... we can get varibales and static and non static methods.
//super(); // constructor calling ...
//behind the scenes the super is being called automatically by JAVA.
// if there is no super keyword java will put it behind the scenes.
