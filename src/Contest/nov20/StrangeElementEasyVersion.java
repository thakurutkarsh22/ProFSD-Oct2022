package Contest.nov20;

import java.util.Scanner;

public class StrangeElementEasyVersion {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();
        int[][] arr = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                arr[i][j] = sc.nextInt();
            }
        }
        sc.close();

        int cnt1 = 0; // checking row with all 1's
        for (int i = 0; i < n; i++) {
            int cnt = 0;
            for (int j = 0; j < m; j++) {
                if (arr[i][j] == 1)
                    cnt++;
            }
            if (cnt == m)
                cnt1++;
        }

        int cnt2 = 0; //// checking columns with all 1's
        for (int i = 0; i < m; i++) {
            int cnt = 0;
            for (int j = 0; j < n; j++) {
                if (arr[j][i] == 1)
                    cnt++;
            }
            if (cnt == n)
                cnt2++;
        }

        System.out.print(cnt1 * cnt2);
    }
}
