package Contest.nov20;

import java.util.Scanner;

public class HappyBalloons {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        sc.close();
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            // index starts with 0, so alternating given conditions
            if (((i % 2) == 0) && ((arr[i]) % 2 != 0))
                cnt++;
            else if (((i % 2) != 0) && ((arr[i]) % 2 == 0))
                cnt++;
        }
        System.out.print(cnt);
    }
}
