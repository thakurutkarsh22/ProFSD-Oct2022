package InClassAssignments.Searching;

import java.util.Scanner;

public class LogicalSearch {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        int ans = logicalSearch(arr);
        System.out.println(ans);
    }

    public static int logicalSearch(int[] arr) {
        int n = arr.length;
        int left = 0;
        int right = n -1;


        int diff = (arr[n-1] - arr[0])/n;

        while(left <=right) {
            int mid = left + (right - left) /2;

//            finding the answerrs
            if(mid + 1 < n &&  arr[mid+1] - arr[mid] !=diff) {
                return arr[mid + 1] - diff;
            }

            //            finding the answerrs

            if(mid -1 >=0 && arr[mid] - arr[mid-1] !=diff) {
                return arr[mid] - diff;
            }

//            shrinking the area of search
            if(arr[mid] - arr[0] != (mid-0)*diff) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return -1;

    }

//    TC => O(logn)
//    Sc => O(1)

}
