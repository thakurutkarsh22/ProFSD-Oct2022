package InClassAssignments.Arrays;

import java.util.Scanner;

public class TransposeOfAMatrix {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(i + j + " ");
            }
            System.out.println();
        }

    }
//    TC = O(n^2)
//    SC = O(1)
}