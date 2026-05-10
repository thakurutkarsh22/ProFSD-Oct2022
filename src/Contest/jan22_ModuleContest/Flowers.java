package Contest.jan22_ModuleContest;

import java.util.*;

public class Flowers {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        long flowers = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        boolean isEven = ((n & 1) == 0);
        for (int i = n - 1; (isEven) ? i > 0 : i >= 0; i--) {
            if ((arr[i] & 1) == 1) {
                flowers += arr[i];
            } else {
                flowers += (arr[i] - 1);
            }
        }
        System.out.print(flowers);
        sc.close();
    }
}
