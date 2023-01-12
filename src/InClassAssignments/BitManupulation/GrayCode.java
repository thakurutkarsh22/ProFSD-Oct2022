package InClassAssignments.BitManupulation;

import java.util.ArrayList;
import java.util.Scanner;

public class GrayCode {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T-- !=0) {
            int n = scn.nextInt();
            System.out.println(n ^ (n >> 1));
        }
    }
}
