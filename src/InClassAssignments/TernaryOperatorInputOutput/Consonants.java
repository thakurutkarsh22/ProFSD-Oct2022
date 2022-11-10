package InClassAssignments.TernaryOperatorInputOutput;

import java.util.Scanner;

public class Consonants {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);

        String s = scn.nextLine();
        int n = s.length();
        String ans = "";
        for(int index =0; index < n; index++) {
            char ch = s.charAt(index);
            boolean condition = (ch == 'a' || ch == 'e' || ch == 'i' ||
                    ch == 'o' || ch == 'u'
            );
            String returnString = condition ? ans : ans + ch;
            ans = returnString;
        }
        System.out.println(ans);
    }
}
