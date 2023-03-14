package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class TooClose {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int k = sc.nextInt();
        int[] arr = new int[n];
        boolean isKPresent = false;
        int max = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
            max = Math.max(max, arr[i]);
            if (arr[i] == k) {
                isKPresent = true;
            }
        }
        sc.close();
        if (!isKPresent) {
            System.out.print(k);
            return;
        }
        boolean[] check = new boolean[max + 1];
        for (int i = 0; i < n; i++) {
            check[arr[i]] = true;
        }
        int cnt1 = 0, cnt2 = 0, ans1 = 0, ans2 = 0;
        boolean check1 = true;
        for (int i = k; i <= max; i++) {
            if (!check[i]) {
                cnt1 = i - k;
                ans1 = i;
                check1 = false;
                break;
            }
        }
        for (int i = k; i > 0; i--) {
            if (!check[i]) {
                cnt2 = k - i;
                ans2 = i;
                break;
            }
        }
        if (k == max) {
            System.out.print(ans2);
            return;
        }
        if (check1) {
            System.out.print(ans2);
            return;
        }
        System.out.print((cnt1 < cnt2) ? ans1 : ans2);
    }
}
