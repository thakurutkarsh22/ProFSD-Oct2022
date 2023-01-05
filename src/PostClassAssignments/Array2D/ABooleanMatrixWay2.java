package PostClassAssignments.Array2D;

import java.io.*;
import java.util.*;

public class ABooleanMatrixWay2 {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int T = Integer.parseInt(br.readLine());
        while (T-- > 0) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            int m = Integer.parseInt(st.nextToken());
            int n = Integer.parseInt(st.nextToken());
            // creating a row of ones
            StringBuilder rowOfOnes = new StringBuilder();
            for (int i = 0; i < n; i++, rowOfOnes.append("1 "))
                ;
            for (int i = 0; i < m; i++) {
                StringBuilder row = new StringBuilder(br.readLine());
                // checking row contains 1 or not
                if (row.indexOf("1") != -1) {
                    System.out.println(rowOfOnes);
                } else
                    System.out.println(row);
            }
        }
    }
}