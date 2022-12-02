package InClassAssignments.Strings;

import java.util.Scanner;

public class DivisibilityTest {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        String s = scn.next();

        String ans = isDivisibleBy30(s);
        System.out.println(ans);
    }

    public static String isDivisibleBy30(String str) {
//        check for the divisibility by 3

        boolean isStringDivisibleBy3 = false;
        int sum = 0;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.charAt(i) - '0';
            sum += ch;
        }
        if(sum%3 ==0) {
            isStringDivisibleBy3 = true;
        }

//        check for divisibility for 10 .....
        boolean isStringDivisibleBy10 = false;

        if(str.charAt(str.length()-1) == '0') {
            isStringDivisibleBy10 = true;
        }

        if(isStringDivisibleBy10 && isStringDivisibleBy3) {
            return "Yes";
        } else {
            return "No";
        }
    }
}
