package InClassAssignments.Loops;

import java.util.Scanner;

public class PrintDigits {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int M = scn.nextInt();

        Print_Digits(M);
    }

    public static void Print_Digits(int M) {
        String num = "";
        while(M!=0) {
            int lastDigit = M % 10;
            M = M/10;
            num = num + lastDigit;
        }

        // System.out.println(num + " this is a num");

        int N =  Integer.parseInt(num);

        // System.out.println(N + "  N this is a num");



// ----  will print the numbers ..... in words
        while(N!=0) {
            int lastDigit = N % 10;
            N = N/10;

            if(lastDigit ==0) {
                System.out.print("zero ");
            } else if (lastDigit == 1) {
                System.out.print("one ");
            } else if (lastDigit == 2) {
                System.out.print("two ");
            } else if (lastDigit == 3) {
                System.out.print("three ");
            } else if (lastDigit == 4) {
                System.out.print("four ");
            } else if (lastDigit == 5) {
                System.out.print("five ");
            } else if (lastDigit == 6) {
                System.out.print("six ");
            } else if (lastDigit == 7) {
                System.out.print("seven ");
            } else if (lastDigit == 8) {
                System.out.print("eight ");
            } else if (lastDigit == 9) {
                System.out.print("nine ");
            }

        }
    }
}
