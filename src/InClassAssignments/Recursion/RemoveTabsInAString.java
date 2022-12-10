package InClassAssignments.Recursion;

import java.util.Scanner;

public class RemoveTabsInAString {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        String s = scn.nextLine();

        String ans = removeSpace(s);
        System.out.println(ans);
    }

    public  static String removeSpace(String str) { // "hello world""
//  base case

        if(str.length() == 0) {
            return "";
        }

        if(str.charAt(0) == ' ' || str.charAt(0) == '\t') {
            return removeSpace(str.substring(1));
        }


//        faith and work....
        char character  = str.charAt(0);
        String faithAns = removeSpace(str.substring(1));
        return  character + faithAns;

    }
}
