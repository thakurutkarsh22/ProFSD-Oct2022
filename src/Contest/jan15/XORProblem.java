package Contest.jan15;

import java.util.Scanner;

public class XORProblem {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            long a = sc.nextLong();
            long cntSetBits = 0;
            while (a != 0) {
                a &= (a - 1);
                cntSetBits++;
            }
            System.out.println((1L << cntSetBits));
        }
        sc.close();
    }
}
