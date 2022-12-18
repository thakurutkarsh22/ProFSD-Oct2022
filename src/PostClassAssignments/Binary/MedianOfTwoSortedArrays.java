package PostClassAssignments.Binary;

import java.util.Scanner;

/*
    Example :
    6 9
1 3 19 29 89 129
2 4 6 8 20 27 33 87 128
 */

public class MedianOfTwoSortedArrays {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int m = scn.nextInt();

        int[] arr1 = new int[n];
        for (int i = 0; i < n; i++) {
            arr1[i] = scn.nextInt();
        }

        int[] arr2 = new int[m];
        for (int i = 0; i < m; i++) {
            arr2[i] = scn.nextInt();
        }

        double ans = medianHard(arr1, arr2);
        System.out.println(ans);
    }

    public static double medianEasy(int[] arr1, int[] arr2) {
        int[] arr3 = new int[arr1.length + arr2.length];

        int i = 0;
        int j = 0;
        int k = 0;

        while(i < arr1.length && j < arr2.length) {
            int elem1 = arr1[i];
            int elem2 = arr2[j];

            if(elem1 > elem2) {
                arr3[k] = elem2;
                j++;
            } else {
                arr3[k] = elem1;
                i++;
            }
            k++;
        }

        if(i != arr1.length) {
            while(i != arr1.length) {
                arr3[k] = arr1[i];
                i++;
                k++;
            }
        }

        if(j != arr2.length) {
            while(j != arr2.length) {
                arr3[k] = arr2[j];
                j++;
                k++;
            }
        }

        for (int l = 0; l < arr3.length; l++) {
            System.out.print( arr3[l] +" ");
        }

        if(arr3.length %2 ==0) {
            int val1 = arr3[arr3.length/2];
            int val2 = arr3[(arr3.length/2) - 1];
            double ans = ( val1 + val2) /2.0;
            return ans;

        } else {
            return arr3[arr3.length/2];
        }
    }

//    TC & SC => O(n+m)


    public static double medianHard(int[] arr1 , int[] arr2) {
//        int n1 = arr1.length;
//        int n2 = arr2.length;

//        some check ....

        int start = 0;
        int end = arr1.length;
        int partitionX = 0;
        int partitionY = 0;
        int mid = (arr1.length + arr2.length + 1) /2;

        while(start <= end) {
            partitionX = (start + end) /2;
            partitionY = mid - partitionX;

            int maxLeftX = partitionX == 0 ? Integer.MIN_VALUE : arr1[partitionX -1];
            int minRightX = partitionX == arr1.length ? Integer.MAX_VALUE : arr1[partitionX];

            int maxLeftY = partitionY == 0 ? Integer.MIN_VALUE : arr2[partitionY -1];
            int minRightY = partitionY == arr2.length ? Integer.MAX_VALUE : arr2[partitionY];


            if(maxLeftX <= minRightY && maxLeftY <= minRightX) {
                if((arr1.length + arr2.length ) % 2 ==0) {
                    return (Math.max(maxLeftX, maxLeftY) + Math.min(minRightY, minRightX)) /2.0;
                }else {
                    return Math.max(maxLeftX, maxLeftY)/1.0;
                }
            } else if(maxLeftX > minRightY) {
                end = partitionX -1;
            } else {
                start = partitionX -1;
            }


        }

        return 1.0;

    }


}
