package InClassAssignments.BitManupulation;

import java.util.Scanner;

public class HelpChefina {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T-- !=0) {
            long n = scn.nextLong();
            int k = scn.nextInt();

            boolean ans = isBitSetOrNotAtPosition(n, k - 1);
            if(ans) {
                System.out.println("SET");
            } else {
                System.out.println("NOT SET");
            }


        }
    }

    public static boolean isBitSetOrNotAtPosition(long num, int position) {
        return (num & (1 << (position))) != 0;
    }
}
