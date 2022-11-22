package InClassAssignments.Arrays;

import java.util.Scanner;

public class RowWithMaximum {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int m = scn.nextInt();

        int[][] arr = new int[n][m];

        for (int row = 0; row < arr.length; row++) {
            for (int col = 0; col < arr[0].length; col++) {
                arr[row][col] = scn.nextInt();
            }
        }

        int winnerIndex = rowWinMin(arr);
        System.out.println(winnerIndex);

    }


//    TODO: chetan pull req to improve and break this.
    public static int rowWinMin(int[][] arr) {

        int sum = Integer.MIN_VALUE;
        int winerIndex = -1;
        for (int row = 0; row < arr.length; row++) {
            int sumRow = 0;

            for (int col = 0; col < arr[0].length; col++) {
                if(arr[row][col] == 1) {
                    sumRow = sumRow + arr[row][col];
                }
            }
            // improvement
            if(sumRow==arr.length){
                winerIndex = row;
                break;
            }
            if(sumRow > sum) {
                sum = sumRow;
                winerIndex = row;
            }
        }
        return winerIndex;
    }
}
