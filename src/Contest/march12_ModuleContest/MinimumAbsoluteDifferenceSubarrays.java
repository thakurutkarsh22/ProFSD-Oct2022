package Contest.march12_ModuleContest;

import java.util.Scanner;

public class MinimumAbsoluteDifferenceSubarrays {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n + 1];
        long preSum = 0;
        for (int i = 1; i <= n; i++) {
            arr[i] = sc.nextInt();
            preSum += arr[i];
        }
        sc.close();

        long min = Integer.MAX_VALUE, leftSum = 0, ansIdx = -1;
        for (int i = 1; i <= n; i++) {
            long absdiff = Math.abs(leftSum - (preSum - leftSum - arr[i]));
            if (min > absdiff) {
                min = absdiff;
                ansIdx = i;
            }
            leftSum += arr[i];
        }
        System.out.print(ansIdx);
    }
}
