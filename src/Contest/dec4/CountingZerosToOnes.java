package Contest.dec4;

import java.util.Scanner;

public class CountingZerosToOnes {
    public static void main(String[] args) {
        // Your code here
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int q = scn.nextInt();

        // int ans = n * n; // 9

        int[] doneRows = new int[n];
        int[] doneCols = new int[n];

        long blankRow = n;
        long blankCol = n;

        while (q != 0) {
            int i = scn.nextInt(); // 1
            int j = scn.nextInt(); // 1

            int ithIndex = i - 1;
            int jthIndex = j - 1;

            if (doneRows[ithIndex] == 0) {
                blankRow--;
                doneRows[ithIndex] = 1;
            }

            if (doneCols[jthIndex] == 0) {
                blankCol--;
                doneCols[jthIndex] = 1;
            }
            long ans = blankRow * blankCol;
            System.out.print(ans + " ");

            q--;
        }

        scn.close();
        // TC => O(q)
        // SC => O(n)

    }
}
