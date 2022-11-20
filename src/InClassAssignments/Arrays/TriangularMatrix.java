package InClassAssignments.Arrays;

import java.util.Scanner;

public class TriangularMatrix {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[][] arr =  new int[n][n];

        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[0].length; j++) {
                arr[i][j] = scn.nextInt();
            }
        }

//        calling the function

        TraigularMatrix(arr);


    }
    public static void TraigularMatrix(int[][] arr) {

        int greenTriangleSum = 0; // upper triangle
        int blueTriangleSum = 0; // lower triangle

        for (int row = 0; row < arr.length; row++) {
            for (int col = 0; col < arr[0].length; col++) {
                if(col >= row) {
//                     you are in green box ... .
                    greenTriangleSum += arr[row][col];
                }
                if(col <= row) {
//                    you are in blue box ... .
                    blueTriangleSum += arr[row][col];
                }
            }
        }
        System.out.println(greenTriangleSum + " " + blueTriangleSum);
    }

//    TC => O(n^2)
//    SC => O(1)
}
