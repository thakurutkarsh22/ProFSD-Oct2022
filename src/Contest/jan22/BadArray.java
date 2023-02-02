package Contest.jan22;

import java.util.*;

public class BadArray {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        boolean check = false;
        for (int i = 1; i < n; i++) {
            if (arr[i - 1] == arr[i]) {
                check = true;
                break;
            }
        }
        System.out.print(check ? "Yes" : "No");
        sc.close();
    }
}
