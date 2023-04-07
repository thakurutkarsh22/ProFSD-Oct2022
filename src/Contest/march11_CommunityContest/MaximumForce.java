package Contest.march11_CommunityContest;

import java.util.*;

public class MaximumForce {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int k = sc.nextInt();
        long[] arr = new long[n];
        for (int i = 0; i < n; i++) {
            long inp = sc.nextInt();
            arr[i] = inp * inp;
        }
        sc.close();
        Arrays.sort(arr);
        long sum = 0;
        for (int i = 0; i < k; i++) {
            sum += arr[n - i - 1];
        }
        System.out.print(sum);
    }
}
