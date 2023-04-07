package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class EqualDifference {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = sc.nextInt();
        }
        sc.close();
        int cnt = 0;
        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n; k++) {
                    if (Math.abs(a[i] - a[j]) == Math.abs(a[j] - a[k])) {
                        cnt++;
                    }
                }
            }
        }
        System.out.print(cnt);
    }
}
