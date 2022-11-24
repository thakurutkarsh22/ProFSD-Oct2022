package LiveClass.Strings;

import java.util.Arrays;

public class LiveLecture1String {
    public static void main(String[] args) {

//------- -------- String Creation ----------------
//        String str1 = "hey there"; // string literal " "
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

//        ----------- substring -------------

//        String str = "aeroplanes";
//        String substringPlanes = str.substring(4); // planes
//        String substringPlane = str.substring(4,9); // ans will be form index (4 -8) // plane
////        it will not include end index...
//        System.out.println(substringPlanes);
//        System.out.println(substringPlane);

//         ------------------ ---- compare String -----------------
//        String str = "abc"; //string pool
//        String str1 = "abc"; // string pool
//        String str2 = new String("abc"); // outside the pool

//        wrong methods to compare strings
//        System.out.println(str == str1); // true
//        System.out.println(str == str2); // false
//        System.out.println(str1 == str2); // false

//        right and only method to compare strings ...
//        System.out.println(str.equals(str2)); // true


//        -------- questions ----------
//        String str = printVowels("shubham");
//        System.out.println(str); //"ua"

//        boolean isAnagramResult = isAnagram("trap", "part");
//        System.out.println(isAnagramResult);

//        String reverseString = reverseString("aeroplanes");
//        System.out.println(reverseString);

//        boolean isPalindromebad = isPalindromeBad("aasdsag");
//        System.out.println(isPalindromebad);

        boolean isPalindrome = isPalindrome("madam");
        System.out.println(isPalindrome);



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

//    isAnagram
//    Input : "abcd", "dcab"
//        output: true (bec both of them are using same characters)

    public static boolean isAnagram(String str1, String str2) {
        if(str1.length() != str2.length()) {
            return false;
        }

//        char[] arr1 = str1.toCharArray(); // O(n)
//        char[] arr2 = str2.toCharArray();

        char[] arr1 = new char[str1.length()];
        char[] arr2 = new char[str2.length()];

        for (int i = 0; i < arr1.length; i++) {
            arr1[i] = str1.charAt(i);
        }

        for (int i = 0; i < arr1.length; i++) {
            arr2[i] = str2.charAt(i);
        }

        Arrays.sort(arr1); // O(n* log(n))
        Arrays.sort(arr2);

        // compare the arrays

        for(int i=0; i<arr1.length ;i++) {
            if(arr1[i] != arr2[i]) {
                return false;
            }
        }
        return true;
    }

//    TC => O(n* log(n))
//    SC => O(n)


/*
        Reverse the string
        Input=: america
        output=: acirema
 */

    public static String reverseString(String str) {
        String ansString = "";
        for(int i = str.length() -1; i >=0 ;i--) {
            char ch = str.charAt(i);
            ansString += ch;
        }

        return ansString;
    }

//    TC => O(N)
//    SC => O(N)


    /*
            PALINDROME
            input: "madam"
            output: true
            reason: bec madam can be read from the back and it will be same..

            input: "aeroplanes"
            output: false
     */

    public static boolean isPalindromeBad(String str) {

        String reverseOfString = reverseString(str); //TC & SC=> O(n)


//       TC =>  O(1)
        if(reverseOfString.equals(str)) {
            return true;
        } else {
            return false;
        }
    }

//    TC => O(n)
//    Sc => O(n)

    public static boolean isPalindrome(String str) {

        int i =0;
        int j = str.length()-1;

        while(i < j) {
            char ch1 = str.charAt(i);
            char ch2 = str.charAt(j);

            if(ch1 == ch2) {
                i++;
                j--;
            } else {
                return false;
            }
        }

        return true;
    }

//    TC => O(n) but to be precise O(n/2)
//    SC => O(1)

}
