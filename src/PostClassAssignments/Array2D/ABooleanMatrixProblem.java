package PostClassAssignments.Array2D;

import java.util.ArrayList;
import java.util.Scanner;

public class ABooleanMatrixProblem {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = Integer.parseInt(scn.nextLine());

        while(T-- !=0) {
            String[] rowCOlArr = scn.nextLine().split(" ");
            int n = Integer.parseInt(rowCOlArr[0]);
            int m = Integer.parseInt(rowCOlArr[1]);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < m; i++) {
                sb.append(1+" ");
            }
            //"1 1 1 1"

            String[] matrix = new String[n]; // ["1 0 0 0", "0 0 0 0", "0 1 0 0", "0 0 0 0", "0 0 0 1"];

            for (int i = 0; i < matrix.length; i++) {
                matrix[i] = scn.nextLine();
            }



            String[] ans = matrix(matrix, sb.toString());
            for (int i = 0; i < ans.length; i++) {
                System.out.println(ans[i]);
            }
        }
    }

    public static String[] matrix(String[] arr, String OneRow) {
//   ["1 1 1 1", "0 0 0 0", "0 1 0 0", "0 0 0 0", "0 0 0 1"];
        for (int i = 0; i < arr.length; i++) {
            String line = arr[i]; //"1 0 0 0"
            if(line.contains("1")) {
                arr[i] = OneRow;
            }
        }

        return arr;
    }
}
