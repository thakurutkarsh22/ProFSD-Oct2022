package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class HappyBalloons {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
            if (((i & 1) == 0 && (arr[i] & 1) != 0) || ((i & 1) != 0 && (arr[i] & 1) == 0)) {
                cnt++;
            }
        }
        System.out.print(cnt);
        sc.close();
    }
}
