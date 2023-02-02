package Contest.jan8;

import java.util.Scanner;

public class RahulsGroceries {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int x = sc.nextInt();
            int A[] = new int[n];
            int B[] = new int[n];
            for (int i = 0; i < n; i++) {
                A[i] = sc.nextInt();
            }
            for (int i = 0; i < n; i++) {
                B[i] = sc.nextInt();
            }

            int cost = 0;

            for (int i = 0; i < n; i++) {
                if (A[i] >= x) {
                    cost += B[i];
                }
            }
            System.out.println(cost);
            sc.close();
        }
    }
}
