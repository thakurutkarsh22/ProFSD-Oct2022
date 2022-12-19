package LiveClass.Sorting;

import Util.util;

public class MergeSort {
    public static void main(String[] args) {
        int[] arr = {1,5,6,90,3,8,2,70,64};

        util.printArrayInt(arr, "Question");

        mergeSort(arr, 0, arr.length -1);

        util.printArrayInt(arr, "Answer");
    }

    public static void mergeSort(int[] arr, int left, int right) {
//        base condition
        if(left >= right) {
            return;
        }

        int mid = left + (right - left) /2;
        mergeSort(arr, left, mid);
        mergeSort(arr, mid + 1, right);
        mergeArray(arr, left, mid, right);

    }

    public static void mergeArray(int[] arr, int left, int mid, int right) {
        int p1 = left;
        int p2 = mid + 1;
        int[] ansArr = new int[right - left + 1];
        int iter = 0;

        while(p1 <= mid && p2 <= right) {
//            compare
            if(arr[p1] < arr[p2]) {
                ansArr[iter] = arr[p1];
                iter++;
                p1++;
            } else {
                ansArr[iter] = arr[p2];
                iter++;
                p2++;
            }
        }

        while(p1 <=mid) {
            ansArr[iter] = arr[p1];
            iter++;
            p1++;
        }

        while(p2 <= right) {
            ansArr[iter] = arr[p2];
            iter++;
            p2++;
        }


//        change original array

        for (int i = left; i <= right; i++) {
            arr[i] = ansArr[i - left];
        }


    }

//    Tc=> O(nlogn)
//    SC => O(n)
}
