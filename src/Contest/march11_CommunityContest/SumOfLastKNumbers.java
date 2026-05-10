package Contest.march11_CommunityContest;

import java.util.Scanner;

public class SumOfLastKNumbers {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int q = sc.nextInt();
        int k = sc.nextInt();
        int[] arr = new int[100007];
        int idx = 0, pre = 0, ans = 0;
        while (q-- > 0) {
            int qr = sc.nextInt();
            if (qr == 1) {
                arr[idx++] = sc.nextInt();
            } else if (pre == 2) {
                System.out.println(ans);
            } else {
                int i = idx - 1, j = k;
                ans = 0;
                while (i >= 0 && j > 0) {
                    ans += arr[i];
                    i--;
                    j--;
                }
                System.out.println(ans);
            }
            pre = qr;
        }
        sc.close();
    }
}
