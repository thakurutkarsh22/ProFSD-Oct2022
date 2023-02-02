package Contest.jan22;

import java.util.*;

public class CandyShopping {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        long m = sc.nextLong();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (m - arr[i] >= 0) {
                m -= arr[i];
                cnt++;
            } else {
                break;
            }
        }
        System.out.print(cnt);
        sc.close();
    }
}
