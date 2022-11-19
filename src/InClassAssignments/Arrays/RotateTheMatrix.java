package InClassAssignments.Arrays;

import java.util.Scanner;

public class RotateTheMatrix {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);

        int n = scn.nextInt();
        int[][] arr = new int[n][n];

        for(int i=0;i<arr.length;i++){
            for (int j = 0; j < arr[0].length; j++) {
                arr[i][j] = scn.nextInt();
            }
        }
        // ------ xxxxx  xx x x end of input xxxxxx --------


        int[][] ans90 = RotateMatrix90(arr);
        printMatrix(ans90);

        System.out.println();

        int[][] ans180 = RotateMatrix90(ans90);
        printMatrix(ans180);
    }

    public static int[][] RotateMatrix90(int[][] arr) {
        int[][] transposeArr = Transpose(arr, arr.length);

//        reverse every row of this 2 -d array.
        for(int i=0;i<arr.length;i++){
            reverseSwap(transposeArr[i]);
        }

        return transposeArr;
    }

    public static int[] reverseWithSpace(int[] arr) {
//
        int[] newArr = new int[arr.length];
        int n = arr.length;

        for(int row = n -1 ; row>= 0; row--) {
            newArr[n - 1 - row] = arr[row];
        }

        return newArr;
    }
//    SC && TC == O(N)

    public static int[] reverseSwap(int[] arr) {
//
        int i =0; // 0
        int j= arr.length -1; // 4

        while(j>i) {
//            swap
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;

//            move the poiner
            i++;
            j--;
        }

        return arr;
    }
//    TC = O(N) to be more precise O(N/2)
//    SC = O(1)

    public static int[][] Transpose(int[][] arr, int N) {
        int[][] newArr = new int[N][N];

        for(int j=0; j<arr[0].length;j++){
            for(int i=0 ; i<arr.length; i++) {
                newArr[j][i] = arr[i][j];
            }
        }
        // printed

        return newArr;
    }

    public static void printMatrix(int[][] arr) {
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[0].length; j++) {
                System.out.print(arr[i][j] + " ");
            }
            System.out.println();
        }
    }
}
