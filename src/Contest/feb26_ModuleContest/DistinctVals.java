package Contest.feb26_ModuleContest;

import java.util.*;

public class DistinctVals {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Set<Integer> set = new HashSet<>();
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (!set.add(arr[i])) {
                cnt++;
            }
        }
        System.out.print(cnt);
        sc.close();
    }
}
