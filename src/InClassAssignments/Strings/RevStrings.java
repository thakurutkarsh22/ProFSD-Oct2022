package InClassAssignments.Strings;

import LiveClass.Strings.LiveLecture1String;

import java.util.Scanner;

public class RevStrings {
    public static void main (String[] args) {
        // Your code here
        Scanner scn = new Scanner(System.in);
        String s = scn.next();

        String ans = LiveLecture1String.reverseStringStringBuilder(s);
//        String ans = reverseStringStringBuilder(s);
        System.out.println(ans);

    }

//    public static String reverseStringStringBuilder(String str) {
//        StringBuilder ansString = new StringBuilder();
//        for(int i = str.length() -1; i >=0 ;i--) {
//            char ch = str.charAt(i);
//            ansString.append(ch);
//        }
//
//        return ansString.toString();
//    }
}