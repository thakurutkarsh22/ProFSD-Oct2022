package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class SumOfLastKNumbers {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int q = sc.nextInt();
        int k = sc.nextInt();
        int[] al = new int[(int) 1e5 + 7];
        int pre = 0, ans = 0, idx = 0;
        while (q-- > 0) {
            int qr = sc.nextInt();
            if (qr == 1) {
                al[idx++] = sc.nextInt();
            } else if (pre == 2) {
                System.out.println(ans);
            } else {
                int i = 0;
                ans = 0;
                for (int j = idx - 1; i < k && j >= 0; j--, i++) {
                    ans += al[j];
                }
                System.out.println(ans);
            }
            pre = qr;
        }
        sc.close();
    }
}
