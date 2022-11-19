package InClassAssignments.Arrays;

import java.util.Scanner;

public class BoundaryTraversalOfMatrix {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T !=0) {
            int n = scn.nextInt();
            int m = scn.nextInt();

            int[][] arr = new int[n][m];
            for(int row = 0; row<arr.length;row++){
                for(int col = 0; col< arr[0].length; col++) {
                    arr[row][col] = scn.nextInt();
                }
            }

            //  now out calling or logic starts
            boundaryTraversal(arr);

            T--;
        }
    }


    public static void boundaryTraversal(int[][] arr) {
        int row = 0;
        int col = 0;

//        Top side

        for( col =0 ; col < arr[0].length ; col++ ) {
            System.out.print(arr[row][col] +" ");
        }
        col--;

//        Right side
        row = row + 1;
        for( ; row < arr.length; row++) {
            System.out.print(arr[row][col] +" ");
        }
        row--;

//        bottom side
        if(row > 0) {
            col = col - 1;
            for (; col >= 0; col--) {
                System.out.print(arr[row][col] + " ");
            }
            col++;
        }

//        left side
        if(col >= 0) {
            row = row - 1;
            for (; row >= 1; row--) {
                System.out.print(arr[row][col] + " ");
            }
        }
    }
}