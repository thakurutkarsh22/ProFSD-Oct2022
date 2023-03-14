package Contest.march11_CommunityContest;

import java.util.Scanner;

public class MaximumAfterRangeQueries {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int T = sc.nextInt();
        while (T-- > 0) {
            int n = sc.nextInt();
            int q = sc.nextInt();
            int[] arr = new int[n + 1];
            int max = 0;
            for (int i = 1; i <= n; i++) {
                arr[i] = i;
            }
            for (int i = 0; i < q; i++) {
                int start = sc.nextInt();
                int end = sc.nextInt();
                int k = sc.nextInt();
                for (int j = start; j <= end; j++) {
                    arr[j] += k;
                }
            }
            for (int i : arr) {
                max = Math.max(max, i);
            }
            System.out.println(max);
        }
        sc.close();
    }
}
