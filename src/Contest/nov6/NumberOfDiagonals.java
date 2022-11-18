package Contest.nov6;

import java.util.Scanner;

public class NumberOfDiagonals {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T !=0) {
            int N = scn.nextInt();
            int ans = numberOfDiagonal(N);
            System.out.println(ans);

            T--;
        }
    }

    public static int numberOfDiagonal(int N){
//Enter your code here
        if(N <4) {
            return 0;
        }
        int ans = N * (N-3) /2;
        return ans;
    }
}
