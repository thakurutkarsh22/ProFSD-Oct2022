package Contest.dec11;

import java.util.*;

public class ReduceTo1 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.print(countways(n));
        sc.close();
    }

    public static int countways(int n) {
        int[] dp = new int[n + 1];
        Arrays.fill(dp, 1000);
        dp[0] = 0;
        dp[1] = 0;
        for (int i = 2; i <= n; i++) {
            for (int j = 2; j * j <= i; j++) {
                if (i % j == 0) {
                    dp[i] = Math.min(dp[i], dp[i - j] + 1);
                    dp[i] = Math.min(dp[i], dp[i - (i / j)] + 1);
                }
            }
            dp[i] = Math.min(dp[i], dp[i - 1] + 1);
        }
        return dp[n];
    }
}
