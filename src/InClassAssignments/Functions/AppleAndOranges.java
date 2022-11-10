package InClassAssignments.Functions;

import java.util.Scanner;

public class AppleAndOranges {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int N = scn.nextInt();
        int A = scn.nextInt();
        int B = scn.nextInt();

        int ans = LikesBoth(N, A, B);
        System.out.println(ans);
    }

    public static int LikesBoth(int N, int A, int B){
        return A + B -N;
    }
}
