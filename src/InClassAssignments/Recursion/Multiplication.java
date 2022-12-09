package InClassAssignments.Recursion;

import java.util.Scanner;

public class Multiplication {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T-- !=0) {
            int val1 = scn.nextInt();
            int val2 = scn.nextInt();
            int ans = Multiply_by_recursion(val1, val2);
            System.out.println(ans);
        }
    }

    public static int  Multiply_by_recursion(int M, int N) {
        // base condition....
        if(N == 0) {
            return 0;
        }

        int ans = M + Multiply_by_recursion(M, N-1);
        return ans;
    }
}
