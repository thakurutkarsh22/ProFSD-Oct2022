package InClassAssignments.Recursion;

import java.util.Scanner;

public class PrintPattern {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        boolean flag = true;

        while(T-- !=0) {
            int n = scn.nextInt();
            int curr = n;
            System.out.println();
            printPatternWay1(n, curr, flag);
            System.out.println();
            printPatternWay2(n, curr, flag);
            System.out.println();
            printPatternIncreasingDecreasingWay(curr);
        }
    }

    public static void printPatternWay1(int n,int curr, boolean flag) {
        System.out.print(curr + " ");

        // Base condition
        if(flag == false && curr == n) {
            return;
        }

        // work
        if(curr <= 0) {
            flag = false;
        }



        if(flag == true) {
            printPatternWay1(n, curr - 5, flag);
        } else {
            printPatternWay1(n, curr + 5, flag);
        }

    }

    public static void printPatternWay2(int n,int curr, boolean flag) {

        // Base condition
        if(flag == false && curr == n) {
            System.out.print(curr + " ");
            return;
        }

        // work
        if(curr <= 0) {
            flag = false;
        }

        System.out.print(curr + " ");


        if(flag == true) {
            printPatternWay2(n, curr - 5, flag);
        } else {
            printPatternWay2(n, curr + 5, flag);
        }

    }

    public static void printPatternIncreasingDecreasingWay(int n) {

        if(n <=0) {
            System.out.print(n + " ");
            return;
        }

        System.out.print(n + " ");
        printPatternIncreasingDecreasingWay(n - 5);
        System.out.print(n + " ");
    }
}
