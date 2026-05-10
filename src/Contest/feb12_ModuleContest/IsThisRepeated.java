package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class IsThisRepeated {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int arr[] = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        sc.close();
        int cnt = 0;
        for (int i = 1; i < n; i++) {
            if (arr[i - 1] == arr[i]) {
                cnt++;
                if (cnt == 2) {
                    System.out.print("Yes");
                    return;
                }
            } else {
                cnt = 0;
            }
        }
        System.out.print(cnt == 2 ? "Yes" : "No");
    }
}
