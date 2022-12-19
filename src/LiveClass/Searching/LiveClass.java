package LiveClass.Searching;

import java.util.Arrays;
import java.util.Scanner;

public class LiveClass {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

//        int ans = searchLinear(arr, 88);
//        System.out.println(ans);

//        double ansMedian = median(arr);
//        System.out.println(ansMedian);

//        double meanAns = Mean(arr);
//        System.out.printf( "%.2f" , meanAns);

//        System.out.println(Integer.MAX_VALUE  + 1000000);
        int ansBinarySearch = binarySearch(arr, 80823823);
        System.out.println(ansBinarySearch);

    }

    public static int searchLinear(int[] arr, int element) {
        for (int i = 0; i < arr.length; i++) {
            if(arr[i] == element) {
                return i;
            }
        }
        return -1;
    }

//    TC => O(n)
//    SC => O(1)

    /*
        Question: Median of an array
        Input: [7 ,5, 1, 8, 3, 6, 0, 9, 4,2]
        Output: 4.5
        explanation: after sorting take numers which are in the middle...

     */


    public  static double median(int[] arr){
        Arrays.sort(arr);
        if(arr.length % 2 ==0 ){
            int middle1 = arr.length /2;
            int middle2 = (arr.length /2) -1;
            return (arr[middle2] + arr[middle1]) /2.0;
        } else {
            return arr[arr.length/2];
        }
    }

/*
    Question: Mean of an array
    Input: [10, 90, 100]
    Output: 200 /3 = 66.666;
    Explanation: average. Add all the marks and divide it to number of subjects.....
 */

    public static double Mean (int[] arr) {
        double sum = 0.0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }

        return sum /arr.length;
    }


    /*
        Question: Binary search
        Input: [10, 20 , 30, 40, 50, 60, 80, 90, 120], x = 20
        Output: 1
        Explanation: if 20 is found then index of that 20 which is 1, else -1 if not found...
     */

    public static int binarySearch(int[] arr, int target) {
        int left  =0 ;
        int right  = arr.length -1;

        while(left<=right) {
            int mid = left + (right - left) /2;

            if(arr[mid] == target) {
                return mid;
            } else if (target > arr[mid]) {
                left = mid  +1;
            } else {
                right = mid -1;
            }

        }

        return -1;
    }

//    TC => O(lng(n))
//    Sc => O(1)



}
