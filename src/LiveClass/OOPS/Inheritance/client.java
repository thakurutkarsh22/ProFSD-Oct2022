package LiveClass.OOPS.Inheritance;

public class client {
    public static void main(String[] args) {
        SSFlat obj = new SSFlat();
        System.out.println(obj.money());
        System.out.println(obj.color);
        System.out.println(obj.isLegal);

        System.out.println("--------------------------");

        MSFlat objMs = new MSFlat();
        System.out.println(objMs.flatId);
        System.out.println(objMs.height);
        System.out.println(objMs.parking);

        System.out.println(objMs.money());
        System.out.println(objMs.color.toUpperCase());

        System.out.println(objMs.isLegal);
    }

//    boolean lift = true;
//    int flatId = 302;
//    int height = 20;
//    String parking = "yes";

//    int floors = 1;
//    String color = "red";
//    boolean lift = false;
//    int length = 200;
//    int bredth = 300;
//    int area = 200 * 300;

//    money

}
