package Contest.march12_ModuleContest;

import java.util.Scanner;

public class MaxMinAddition {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        long a = sc.nextLong();
        long n = sc.nextLong();
        sc.close();

        while (n-- > 0) {
            long num = a;
            long mn = 9, mx = 0;
            while (num > 0) {
                long dig = num % 10;
                mn = Math.min(mn, dig);
                mx = Math.max(mx, dig);
                num /= 10;
            }
            if (mn * mx == 0) {
                break;
            }
            a += mn * mx;
        }
        System.out.print(a);
    }
}
