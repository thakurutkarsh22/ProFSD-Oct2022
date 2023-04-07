package Contest.jan22_ModuleContest;

import java.util.Scanner;

public class SpecialDigitSum {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        long[] a = new long[n];
        int sum = 0;
        for (int i = 0; i < n; i++) {
            a[i] = sc.nextLong();
            sum += maxDigit(a[i]);
        }
        System.out.print(sum);
        sc.close();
    }

    public static long maxDigit(long n) {
        long max = 0;
        while (n > 0) {
            max = Math.max(max, n % 10);
            n /= 10;
        }
        return max;
    }
}
