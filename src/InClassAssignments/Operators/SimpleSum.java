package InClassAssignments.Operators;

import java.util.Scanner;

public class SimpleSum {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int a = scn.nextInt();
        int b = scn.nextInt();
        int c = scn.nextInt();
        simpleSum(a, b, c);
    }

    public static void simpleSum(int a, int b, int c){
        // Your code here

        System.out.println(a + b + c);

    }
}
