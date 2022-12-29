package InClassAssignments.Math;

import java.util.Scanner;

public class MakeMultiple {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();
        while(T-- != 0) {
            int a = scn.nextInt();
            int b = scn.nextInt();

            if(b % a == 0 || 2*a <= b) {
                System.out.println("YES");
            } else {
                System.out.println("NO");
            }
        }
    }
}
