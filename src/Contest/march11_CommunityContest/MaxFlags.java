package Contest.march11_CommunityContest;

import java.util.Scanner;

public class MaxFlags {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int m = sc.nextInt();
            int[] flags = new int[n];
            int max = 0, pre = -1;
            for (int i = 0; i < m; i++) {
                int operation = sc.nextInt();
                if (operation <= n) {
                    flags[operation - 1]++;
                    max = Math.max(max, flags[operation - 1]);
                } else if (pre != max) {
                    for (int j = 0; j < n; j++) {
                        flags[j] = max;
                    }
                    pre = max;
                }
            }
            for (int i : flags)
                System.out.print(i + " ");
            System.out.println();
        }
        sc.close();
    }
}
