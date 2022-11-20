package InClassAssignments.Arrays;

import java.util.Scanner;

public class MaxSumColumn {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int m = scn.nextInt();
        int n = scn.nextInt();

        int[][] arr = new int[m][n];

        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[0].length; j++) {
                arr[i][j] = scn.nextInt();
            }
        }

        int maxSum = MaxColumnSum(arr);
        System.out.println(maxSum);

    }

    public static int MaxColumnSum(int[][] arr) {
        int maxSum = Integer.MIN_VALUE;

        for (int col = 0; col < arr[0].length; col++) {
            int colSum = 0;
            for (int row = 0; row < arr.length; row++) {
                colSum += arr[row][col];
            }
//             after taking sum of all the elements iin the row
//            ill ask the question that haey are your supreme (greater the all )
//            if(colSum > maxSum) {
//                maxSum = colSum;
//            }

            maxSum = (colSum > maxSum) ? colSum : maxSum;
        }
        return maxSum;
    }
//    TC = O(n^2)
//    SC = O(1)
}
