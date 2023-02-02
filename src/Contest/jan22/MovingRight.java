package Contest.jan22;

import java.util.Scanner;

public class MovingRight {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        int cnt = 0;
        int max = 0;
        for (int i = 1; i < n; i++) {
            if (arr[i - 1] >= arr[i]) {
                cnt++;
                max = Math.max(cnt, max);
            } else {
                cnt = 0;
            }
        }
        System.out.print(max);
        sc.close();
    }
}
