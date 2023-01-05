package InClassAssignments.Arrays;

import java.util.*;

public class AntiClockWiseReverseSpiral {
    public static void main(String[] args) {
        // TODO: guys give pull request ....
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();
        int[][] mat = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                mat[i][j] = sc.nextInt();
            }
        }
        int topRow = 0;
        int bottomRow = n - 1;
        int leftColumn = 0;
        int rightColumn = m - 1;
        while (leftColumn <= rightColumn && topRow <= bottomRow) {
            // print left column
            for (int i = topRow; i <= bottomRow; i++) {
                System.out.print(mat[i][leftColumn] + " ");
            }
            leftColumn++; // remove printed column
            // print bottom row
            for (int i = leftColumn; i <= rightColumn; i++) {
                System.out.print(mat[bottomRow][i] + " ");
            }
            bottomRow--; // remove printed row
            // print right column
            for (int i = bottomRow; i >= topRow && leftColumn <= rightColumn; i--) {
                System.out.print(mat[i][rightColumn] + " ");
            }
            rightColumn--; // remove printed column
            // print top row
            for (int i = rightColumn; i >= leftColumn && topRow <= bottomRow; i--) {
                System.out.print(mat[topRow][i] + " ");
            }
            topRow++; // remove printed row
        }
    }
}
