package Contest.dec31;

import java.util.*;

public class Help_Megha {
    public static void main(String[] args) {
        // Your code here
        int MAXN = 100009, lim = (int) Math.sqrt(MAXN);
        boolean isNotPrime[] = new boolean[MAXN];
        isNotPrime[0] = isNotPrime[1] = true;
        for (int i = 2; i <= lim; i++) {
            for (int j = i * i; j < MAXN; j += i) {
                isNotPrime[j] = true;
            }
        }
        int[] cnt = new int[MAXN];
        int temp = 0;
        for (int i = 2; i < MAXN; i++) {
            if (!isNotPrime[i])
                cnt[i] = ++temp;
        }

        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            boolean check = true;
            for (int i = n; i >= 2; i--) {
                if (!isNotPrime[i]) {
                    check = false;
                    System.out.println(cnt[i]);
                    break;
                }
            }
            if (check) {
                System.out.println(0);
            }
        }
        sc.close();
    }
}
