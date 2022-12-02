package InClassAssignments.Strings;

import java.util.Scanner;

public class OddCharacter {
    public static void main (String[] args) {
        // Your code here

        Scanner scn = new Scanner(System.in);
        String s = scn.next();

        String ans = printOddCharacter(s);
        System.out.println(ans);


    }

    public static String printOddCharacter(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i = i+2) {
            sb.append(str.charAt(i) + " ");
        }
        return sb.toString();
    }
}
