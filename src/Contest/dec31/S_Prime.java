package Contest.dec31;

import java.util.*;

public class S_Prime {
    public static void main(String[] args) {
        // Your code here
        int MAXN = 10000009, lim = (int) Math.sqrt(MAXN);
        boolean isNotPrime[] = new boolean[MAXN];
        isNotPrime[0] = isNotPrime[1] = true;
        for (int i = 2; i <= lim; i++) {
            for (int j = i * i; j < MAXN; j += i) {
                isNotPrime[j] = true;
            }
        }

        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int cnt = 0;
            for (int i = n; i > n / 2; i--) {
                if (!isNotPrime[i]) {
                    cnt++;
                }
            }
            System.out.println(cnt);
        }
        sc.close();
    }
}
