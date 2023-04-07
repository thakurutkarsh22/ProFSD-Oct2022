package Contest.jan22_ModuleContest;

import java.util.*;

public class YingYangFestival {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int x = sc.nextInt();
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = sc.nextInt();
        }
        Arrays.sort(p);
        int cnt = 0;
        boolean check = false;
        for (int i = 0; i < n; i++) {
            if (x - p[i] >= 0) {
                x -= p[i];
                cnt++;
                if (cnt >= 2) {
                    check = true;
                    break;
                }
            }
        }
        System.out.print(check ? "YES" : "NO");
        sc.close();
    }
}
