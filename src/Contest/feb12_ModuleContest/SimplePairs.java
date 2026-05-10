package Contest.feb12_ModuleContest;

import java.util.*;

public class SimplePairs {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        int i = 0, j = n - 1;
        long sum = 0;
        while (i < j) {
            sum += arr[j--] - arr[i++];
        }
        System.out.print(sum);
        sc.close();
    }
}
