package LiveClass.OOPS.Flat;

import java.util.Scanner;

public class SSFlatCLient {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in); // object

        int floors;
        String color;
        boolean lift;
        int length;
        int bredth;
        int area;

        SSFlat uFlat = new SSFlat(10, "red", true, 100, 100, 10000);
        SSFlat uFlat1 = new SSFlat();
        System.out.println(uFlat.area + ": area");
        System.out.println(uFlat.lift);
        System.out.println(uFlat.floors + " : floors");

        System.out.println(uFlat.money());


    }
}