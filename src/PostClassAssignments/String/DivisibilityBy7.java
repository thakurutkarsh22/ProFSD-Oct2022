package PostClassAssignments.String;

import java.util.Scanner;

public class DivisibilityBy7 {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        String s = scn.nextLine();

        boolean ans = isDivisibleByx(s, 10);
        if(ans) {
            System.out.println(1);
        } else {
            System.out.println(0);
        }


    }

    public static boolean isDivisibleBy7(String str){
//        "217217"

        int remainder = 0; //0
        for (int i = 0; i < str.length(); i++) {
            int digit = str.charAt(i) - '0'; //7
            int number = remainder * 10 + digit;
            if(number % 7 == 0) {
                remainder = 0;
            } else {
                remainder = number % 7;
            }
        }

        return  remainder == 0;
    }

    public static boolean isDivisibleByx(String str, int x){
        int remainder = 0;
        for (int i = 0; i < str.length(); i++) {
            int digit = str.charAt(i) - '0';
            int number = remainder * 10 + digit;
            remainder = number % x;
        }
        return  remainder == 0;
    }

//    TC => O(n)
//    SC => O(1)

//    it will check for the divisibility for every digit...
//    but it will be inefficient for divisibility for 10, 2 ,5 which is TC => O(1)

//    If the last three digits of a number are divisible by 8, then the number is completely divisible by 8.
}
