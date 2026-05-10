package Contest.march11_CommunityContest;

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
        sc.close();
        Arrays.sort(arr);
        long sum = 0;
        int i = 0, j = n - 1;
        while (i < j) {
            sum += arr[j] - arr[i];
            i++;
            j--;
        }
        System.out.print(sum);
    }
}
