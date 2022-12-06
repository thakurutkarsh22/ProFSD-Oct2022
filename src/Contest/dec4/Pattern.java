package Contest.dec4;

import java.util.Scanner;

public class Pattern {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        Pattern(n);
    }

    public static void Pattern(int N){
        // 1st row
        System.out.println("*");

        // middle ROw ... . . . .

        for(int i = 1; i< N-1; i++) {
            // starting star
            System.out.print("*");

            // ^ this thing
            for(int j = 0; j < i ; j++) {
                System.out.print("^");
            }

            // ending star
            System.out.print("*");
            System.out.println();
        }

        // last row
        for(int i =0; i <= N; i++) {
            System.out.print("*");
        }
    }

//    TODO: a new function and send ME PR .......
}
