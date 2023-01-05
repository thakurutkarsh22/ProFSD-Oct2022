package Contest.dec31;

import java.util.*;

public class GCD_Increment {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        sc.close();

        boolean check = true;
        int skip = 2;
        for (int i = 0; i < n - 1; i++) {
            if (gcd(arr[n - 1], arr[i]) == 1) {
                skip--;
            }
            if (skip == 0) {
                check = false;
            }
        }
        System.out.print(check ? "YES" : "NO");
    }

    public static int gcd(int a, int b) {
        return (b == 0) ? a : gcd(b, a % b);
    }
}
