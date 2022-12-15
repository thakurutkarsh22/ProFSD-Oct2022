package LiveClass.Sorting;

import Util.util;

import java.util.Scanner;

public class LiveClass1 {
    public static void main(String[] args) {
        int[] arr = {1,5,6,90,3,8,2,70,64};

        util.printArrayInt(arr, "Question");

//        InsertionSort(arr);
//        util.printArrayInt(arr, "Sorted Answer");

        BubbleSortIterative(arr);

        util.printArrayInt(arr, "Sorted Answer");



//        BubbleSortRecursive(arr, arr.length);
//        util.printArrayInt(arr, "Sorted Answer");
    }

    public static void InsertionSort(int[] arr) {

        for (int i = 0; i < arr.length; i++) {

            for (int j = i; j >= 1 ; j--) {

                if(arr[j] < arr[j-1]) {
                    int temp = arr[j];
                    arr[j] = arr[j - 1];
                    arr[j - 1] = temp;
                } else {
                    break;
                }
            }
        }

    }


    public static void BubbleSortIterative(int[] arr) {

        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr.length -1 - i; j++) {
                if(arr[j] > arr[j+1]) {
                    swap(arr, j, j+1);
                }
            }
        }
    }

    public static void swap(int[]arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;

        return;
    }

//    TC => O(n^2)
//    SC => O(1)

    public static void BubbleSortRecursive(int[] arr, int n) {

        if(n == 0 || n==1) {
            return;
        }
//        base case

        for (int j = 0; j < n -1; j++) {
            if(arr[j] > arr[j+1]) {
                int temp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = temp;
            }
        }

        BubbleSortRecursive(arr, n-1);

    }

    //    TC => O(n^2)
//    SC => O(1)




}
