package LiveClass.Recursion;

import java.util.ArrayList;

public class LiveClass2 {
    public static void main(String[] args) {
        int[] nums ={1,2,3};
        printPermutation("abc", "");
    }



    public static void printPermutation(String str, String ans) {
//        base case /// ....

        if(str.length() == 0) {
            System.out.println(ans);
        }

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i); // a

            String leftSubstring = str.substring(0, i);
            String rightSubstring = str.substring(i+1);
            String restOfTheString = leftSubstring + rightSubstring; // bc

            printPermutation(restOfTheString, ans + ch);
        }
    }

    /*
        Leetcode 46
        Question: Print all permutation of [1,2,3]
        Input:[1,2,3]
        Output: [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
     */

//    public ArrayList<ArrayList<Integer>> permute(int[] nums) {
//
//    }
}
