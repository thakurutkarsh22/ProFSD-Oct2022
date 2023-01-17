package LiveClass.OOPS.Polymorphism.Overriding;

public class MSFlat extends SSFlat {
    boolean lift = true;
    int flatId = 302;
    int height = 20;
    String parking = "yes";

    int length = 12;
    int bredth = 1;

    MSFlat() {
    }



    MSFlat(int flatId, int height, String parking, String color) {
        this.height = height;
        this.parking = parking;
        this.flatId = flatId;

    }

//    @Override
//    public String toString() {
//        return "hey there i am in MSFLAT";
//    }

    @Override
    public int money() {
        int moneyFromSingleStory = super.money();
//        1190

//        MS
        int energyManagement = 500;
        int waterManagement = 200;
        int maintainence = 1000;
//        1250

        return moneyFromSingleStory + energyManagement + waterManagement + maintainence;
    }

//    @Override
//    public String iamStatic() {
//        return "I am inside MS flat";
//    }

    public void canSeeSky() {
        System.out.println("i can see the sky");
    }

}
