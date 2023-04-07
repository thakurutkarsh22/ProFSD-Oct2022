package Contest.jan29;

import java.util.Scanner;

public class RightMostDifferentBit {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int m = sc.nextInt();
            int n = sc.nextInt();
            if (m == n) {
                System.out.println(-1);
                break;
            }
            for (int i = 0; i < 32; i++) {
                if ((m & (1 << i)) != (n & (1 << i))) {
                    System.out.println(i + 1);
                    break;
                }
            }
        }
        sc.close();
    }
}
