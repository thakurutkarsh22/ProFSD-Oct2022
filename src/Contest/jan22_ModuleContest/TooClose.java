package Contest.jan22_ModuleContest;

import java.util.Scanner;

public class TooClose {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int k = sc.nextInt();
        int[] arr = new int[n];
        int max = 0;
        boolean isKPresent = false;
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
        boolean[] temp = new boolean[max + 1];
        for (int i = 0; i < n; i++) {
            temp[arr[i]] = true;
        }
        int cnt1 = 0, cnt2 = 0, ans1 = 0, ans2 = 0;
        boolean check = true;
        for (int i = k; i <= max; i++) {
            if (!temp[i]) {
                cnt1 = i - k;
                ans1 = i;
                check = false;
                break;
            }
        }
        for (int i = k; i > 0; i--) {
            if (!temp[i]) {
                cnt2 = k - i;
                ans2 = i;
                break;
            }
        }
        if (k == max) {
            System.out.print(ans2);
            return;
        }
        if (check) {
            System.out.print(ans2);
            return;
        }
        System.out.print((cnt1 < cnt2) ? ans1 : ans2);
    }
}
