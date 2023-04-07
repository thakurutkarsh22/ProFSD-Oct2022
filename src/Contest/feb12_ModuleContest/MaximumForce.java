package Contest.feb12_ModuleContest;

import java.util.*;

public class MaximumForce {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int k = sc.nextInt();
        long[] arr = new long[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
            if (arr[i] < 0) {
                arr[i] = -arr[i];
            }
        }
        Arrays.sort(arr);
        long force = 0;
        for (int i = n - 1; k > 0; i--, k--) {
            long sqr = arr[i] * arr[i];
            force += sqr;
        }
        System.out.print(force);
        sc.close();
    }
}
