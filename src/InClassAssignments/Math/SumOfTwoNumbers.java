package InClassAssignments.Math;

import java.util.Scanner;

public class SumOfTwoNumbers {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int a = scn.nextInt();
        int b = scn.nextInt();
        int c = scn.nextInt();

        boolean condition = (a+b == c) || (a + c == b) || (b+c == a);

        if(condition) {
            System.out.println("YES");
        } else {
            System.out.println("NO");
        }
    }
}
