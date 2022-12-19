package Contest;

import java.util.Scanner;

public class Permutation {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);

        permutation("abc", "");
    }

    public static void permutation(String str, String ans) {
//        Base case
        if(str.length() == 0) {
            System.out.println(ans);
            return;
        }

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            String leftSubString  = str.substring(0, i);
            String rightSubString  = str.substring(i+1);
            String restOfTheString = leftSubString + rightSubString;
            permutation(restOfTheString, ans + ch);
        }
    }
}
