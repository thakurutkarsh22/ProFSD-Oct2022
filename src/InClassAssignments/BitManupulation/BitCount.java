package InClassAssignments.BitManupulation;

import java.util.Scanner;

public class BitCount {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        long n = scn.nextLong();
        System.out.println(countSetBits(n));
    }

    public static int countSetBits(long num) {
        int count = 0;
        while(num != 0) {
            long rightMostSetBit = rightMostSetBit(num);
            if (rightMostSetBit != 0) {
                count++;
            }
            num -= rightMostSetBit;
        }

        return count;

    }

    public static long rightMostSetBit(long num) {
        long numComplement = ~num;
        long twosComplementOfNum = numComplement  + 1;

        return num & twosComplementOfNum;
    }
}
