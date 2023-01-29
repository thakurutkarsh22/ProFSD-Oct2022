package Leetcode;

import LiveClass.Searching.LiveClass;
import LiveClass.Searching.LiveClass2;

public class SearchA2DMatrixII240 {
    public static void main(String[] args) {

    }


//    public boolean searchMatrix(int[][] matrix, int target) {
//        for (int i = 0; i < matrix.length; i++) {
//            int[] arr = matrix[i];
//            int index = LiveClass.binarySearch(arr, target);
//
//            if(index != -1) {
//                return true;
//            }
//        }
//
//        return false;
//    }
//
////    TC=> nlogn


    public boolean searchMatrix(int[][] matrix, int target) {
        int row = 0;
        int col = matrix[0].length -1;

        while (row < matrix.length && col >= 0) {
            int item = matrix[row][col];
            if(item == target) {
                return true;
            }
            if(item > target) {
                col--;
            } else {
                row++;
            }
        }

        return false;

    }

//    TC => O(arr.length + arr[0].length];

}
