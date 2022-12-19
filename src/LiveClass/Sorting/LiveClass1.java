package LiveClass.Sorting;

import Util.util;

import java.util.Scanner;

public class LiveClass1 {
    public static void main(String[] args) {
        int[] arr = {1,5,6,90,3,8,2,70,64};

        util.printArrayInt(arr, "Question");

//        InsertionSort(arr);
//        util.printArrayInt(arr, "Sorted Answer");

//        BubbleSortIterative(arr);
//
//        util.printArrayInt(arr, "Sorted Answer");



//        BubbleSortRecursive(arr, arr.length);
//        util.printArrayInt(arr, "Sorted Answer");


//        SelectionSort(arr);
//        util.printArrayInt(arr, "sorted answer");

        QuickSort(arr);
        util.printArrayInt(arr, "sorted answer");
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

    public static void SelectionSort(int[] arr) {

        for (int i = 0; i < arr.length; i++) {
            int lowest = i;

            for(int j = i + 1; j < arr.length; j++) {
                if(arr[j] < arr[lowest]) {
                    lowest = j;
                }
            }

//            swap lowest to i
            int temp =  arr[lowest];
            arr[lowest] = arr[i];
            arr[i] = temp;
        }
    }

//    TC => O(n^2)
//    SC=> O(1)


    public static void QuickSort(int[] arr) {
        QuickSortAlgo(arr, 0, arr.length - 1);
    }

    public static void QuickSortAlgo(int[] arr, int low, int high) {

//        base case
        if(low > high) {
            return;
        }

//        faith and work
        int partition = partition(arr, low, high); // some work ......


        QuickSortAlgo(arr, low, partition -1);
        QuickSortAlgo(arr, partition + 1, high);

    }

    public static int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int i = low - 1;
        for (int j = low; j <= high -1; j++) {
            if(arr[j] < pivot) {
                i++;
                util.swapInArrays(arr, i, j);
            }
        }
        util.swapInArrays(arr, i+1, high);
        return i+1;
    }

//    TC => O(nlogn)
//    SC => O(1)

//    in bad case TC = >O(n^2)....






}
