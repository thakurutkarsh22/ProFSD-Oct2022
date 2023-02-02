package Contest.jan22;

import java.util.*;

public class ModSquare {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        int max = 0;
        Arrays.sort(arr);
        for (int i = 0; i < n - 1; i++) {
            max = Math.max(max, arr[i] % arr[n - 1]);
        }
        System.out.print(max);
        sc.close();
    }
}
