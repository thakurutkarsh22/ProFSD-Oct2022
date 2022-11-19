package InClassAssignments.Arrays;

import java.util.Scanner;

public class SimpleTranspose {
    public static void main (String[] args) {
        // Your code here
        Scanner scn = new Scanner(System.in);
        int N = scn.nextInt();
        int[][] arr = new int[N][N];

        for(int i = 0; i<arr.length;i++){
            for(int j =0; j<arr[0].length; j++) {
                arr[i][j] = scn.nextInt();
            }
        }

        //  this is our main logic
        int[][] ansarr = Transpose(arr, N);

        // here i can print this.... choice if i want to print
        for(int i = 0; i<arr.length;i++){
            for(int j =0; j<arr[0].length; j++) {
                System.out.print(ansarr[i][j] +" ");
            }
            System.out.println();
        }

    }

    //
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
//    TC && SC = O(n^2)
}
