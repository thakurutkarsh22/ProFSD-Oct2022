package Contest.nov6;

import java.util.Scanner;

public class Rectangle {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int m = scn.nextInt();
        scn.close();
        rectangleStars1(n, m);
        // rectangleStars2(n, m);
    }

    // method 1
    public static void rectangleStars1(int n, int m) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                // boundary elements printing
                if (i == 0 || i == m - 1 || j == 0 || j == n - 1) {
                    System.out.print("*");
                } else
                    System.out.print(" ");
            }
            System.out.println();
        }
    }

    // method 2
    public static void rectangleStars2(int n, int m) {
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < m; col++) {
                if (row == 0 || row == n - 1 || col == 0 || col == m - 1) {
                    // rows are done
                    if (row == 0 || row == n - 1) {
                        System.out.print("*");
                    }
                    // columns
                    if (col == 0 || col == m - 1) {
                        if (!(row == 0 || row == n - 1)) {
                            System.out.print("*");
                        }
                    }
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }
}
