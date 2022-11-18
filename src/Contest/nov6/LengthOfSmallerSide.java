package Contest.nov6;

import java.util.Scanner;

public class LengthOfSmallerSide {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int r = scn.nextInt();
        double pi = 22/7.0;
        double ans = pi * r /3.0;

        System.out.printf("%.2f",ans);
    }
}
