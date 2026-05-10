package Contest.jan8_ModuleContest;

import java.util.Scanner;

public class SumDiscrepancy {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        long[] pre = new long[n + 1];
        for (int i = 0; i < n; i++) {
            pre[i + 1] = pre[i] + sc.nextInt();
        }
        int ansIdx = 0;
        for (int i = 1; i <= n; i++) {
            long sum1 = pre[i];
            long sum2 = pre[n] - pre[i];
            if (sum1 > sum2) {
                ansIdx = i;
                break;
            }
        }
        System.out.print(ansIdx);
        sc.close();
    }
}
