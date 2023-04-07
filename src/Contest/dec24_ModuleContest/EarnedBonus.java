package Contest.dec24_ModuleContest;

import java.util.*;

public class EarnedBonus {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int p = sc.nextInt();
        sc.close();
        int ans = 0;
        if (p >= 1000 && p < 10000) {
            ans = 1000;
        } else if (p > 10000 && p < 50000) {
            ans = 5000;
        } else if (p >= 50000 && p < 100000) {
            ans = 10000;
        } else if (p == 100000) {
            ans = 50000;
        }
        System.out.print(ans);
    }
}
