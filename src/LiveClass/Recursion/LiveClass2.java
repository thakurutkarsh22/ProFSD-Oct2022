package LiveClass.Recursion;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class LiveClass2 {
    public static void main(String[] args) {
//        int[] nums ={1,2,3};
//        printPermutation("abc", "");
//
//        ArrayList<Integer> current = new ArrayList<>();
//        ArrayList<ArrayList<Integer>> ansList = new ArrayList<>();
//
//        permute(nums, current, ansList);
//
//        System.out.println(ansList);


        int n = 4;
        int k = 2;
        ArrayList<Integer> current = new ArrayList<>();
        ArrayList<ArrayList<Integer>> ansList = new ArrayList<>();
        int[] num = {1,2,3};
        combination(n, k, 1, current, ansList);

        System.out.println(ansList);

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

    public static void permute(int[] nums, ArrayList<Integer> current, ArrayList<ArrayList<Integer>> ansList) {
//        base ....
        if(nums.length == current.size()) {
            ansList.add(new ArrayList(current));
            return;
        }

        for (int i = 0; i < nums.length; i++) {
            int item = nums[i];
            if(current.contains(item)) {
                continue;
            }
            current.add(item);
            permute(nums, current, ansList);
            current.remove(current.size() - 1);
        }
    }


    public static void combination(int n, int k, int vidx, ArrayList<Integer> current, ArrayList<ArrayList<Integer>> answerList) {
//        something base case ...

        if(current.size() == k) {
            answerList.add(new ArrayList<>(current));
            return;
        }

        for (int i = vidx; i <= n ; i++) {
            current.add(i);
            combination(n, k, i + 1, current, answerList);
            current.remove(current.size() - 1);
        }
    }
}
