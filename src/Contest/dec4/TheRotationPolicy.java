package Contest.dec4;

import java.util.Scanner;

public class TheRotationPolicy {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int A = scn.nextInt();
        int B = scn.nextInt();
        scn.close();
        int ans = RotationPolicy(A, B);
        System.out.println(ans);
    }

    public static int RotationPolicy(int A, int B) {
        int count = 0;
        for (int i = A; i <= B; i++) {
            if (i % 6 == 0 || i % 6 == 2) {
                count++;
            }
        }

        return count;
    }
}
