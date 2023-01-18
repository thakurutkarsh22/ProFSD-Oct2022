package LiveClass.OOPS.Inheritance;

public class client {
    public static void main(String[] args) {
//        SSFlat obj = new SSFlat();
//        System.out.println(obj.money());
//        System.out.println(obj.color);
//        System.out.println(obj.isLegal);
//
//        System.out.println("--------------------------");
//
        MSFlat objMs = new MSFlat();
        System.out.println(objMs.flatId);
        System.out.println(objMs.height);
        System.out.println(objMs.parking);
//
//        System.out.println(objMs.money());
//        System.out.println(objMs.color.toUpperCase());
//
//        System.out.println(objMs.isLegal);


        System.out.println("-------- personalized multi story flats --------- ");

        MSFlat ambanisFlat = new MSFlat(2001, 50, "no", "pink");
        System.out.println(ambanisFlat.flatId);
        System.out.println(ambanisFlat.height);
        System.out.println(ambanisFlat.parking);
        System.out.println(ambanisFlat.color);
        System.out.println(ambanisFlat.volumeOfMS());

    }


}
