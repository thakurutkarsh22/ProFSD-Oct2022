package LiveClass.Strings;

import java.util.Arrays;

public class LiveLecture1String {
    public static void main(String[] args) {

//------- -------- String Creation ----------------
//        String str1 = "hey there"; // string literal
//        String str2 = new String("hey there!!"); // constructor function / new method
//
//        System.out.println(str1);
//        System.out.println(str2);

//        --------------- ways of inputs ------------
//        buffer reader
//        readLine();

//        scanner...
//        scn.next()


//        --------- concatenation -----------
//        System.out.println("a" + "b"); // "ab" // concatenation
//        String str1 = "abc";
//        String str2 = "def";
//
//        String ansString = str1.concat(str2);
//        System.out.println(ansString); // "abcdef"
//
//        String str = printVowels("shubham");
//        System.out.println(str); //"ua"

//        ----------- substring -------------

//        String str = "aeroplanes";
//        String substringPlanes = str.substring(4); // planes
//        String substringPlane = str.substring(4,9); // ans will be form index (4 -8) // plane
////        it will not include end index...
//        System.out.println(substringPlanes);
//        System.out.println(substringPlane);

//         ------------------ ---- compare String -----------------
        String str = "abc"; //string pool
        String str1 = "abc"; // string pool
        String str2 = new String("abc"); // outside the pool

//        wrong methods to compare strings
        System.out.println(str == str1); // true
        System.out.println(str == str2); // false
        System.out.println(str1 == str2); // false

//        right and only method to compare strings ...
        System.out.println(str.equals(str2)); // true
    }

//    Ques: write a program to print vovels in the string
//    str = "utkarsh"
//    output = "ua"

    public static String printVowels(String str) {
        String ans = "";
        for(int i=0; i<str.length(); i++) {
            char ch = str.charAt(i);

            if(isVowel(ch) == true) {
                ans = ans + ch;
            }
        }
        return ans;
    }

    private static boolean isVowel(char ch) {
        if(ch == 'a' || ch == 'e' || ch == 'i' ||
                ch == 'o' || ch == 'u' ) {
            return true;
        } else {
            return false;
        }
    }


}
