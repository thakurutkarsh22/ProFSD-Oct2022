package Contest.jan22_ModuleContest;

import java.util.Scanner;

public class HappyDiwali {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int k = sc.nextInt();
        int arr[] = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        int max = -1;
        int ans = 0;
        for (int i = 0; i < n; i++) {
            int temp = arr[i] / k;
            if (arr[i] % k != 0) {
                temp++;
            }
            if (arr[i] <= k) {
                if (max < 0) {
                    ans = i;
                }
                continue;
            }
            if (max <= temp) {
                max = temp;
                ans = i;
            }
        }
        System.out.print(ans + 1);
        sc.close();
    }
}
