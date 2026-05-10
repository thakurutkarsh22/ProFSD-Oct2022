package Contest.dec31;

import java.util.*;

public class GCD_Pairs {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        int max = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
            max = Math.max(max, arr[i]);
        }
        int cnt[] = new int[max + 1];
        for (int i = 0; i < n; i++) {
            cnt[arr[i]]++;
        }
        boolean check = true;
        for (int i = max; i >= 1; i--) {
            int cntMultiples = 0;
            for (int j = i; j <= max; j += i) {
                if (cnt[j] > 0) {
                    cntMultiples += cnt[j];
                }
                if (cntMultiples > 1) {
                    System.out.print(i);
                    check = false;
                    break;
                }
            }
            if (!check) {
                break;
            }
        }
        sc.close();
    }
}
