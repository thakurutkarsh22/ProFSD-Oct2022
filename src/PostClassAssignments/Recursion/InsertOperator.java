package PostClassAssignments.Recursion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.StringTokenizer;

public class InsertOperator {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            StringTokenizer st = new StringTokenizer(br.readLine());
            int n = Integer.parseInt(st.nextToken());
            long target = Long.parseLong(st.nextToken());
            long[] arr = new long[n];
            st = new StringTokenizer(br.readLine());
            for(int i=0; i<n; i++){
                arr[i] = Long.parseLong(st.nextToken());
            }

        boolean ans = isop(arr, arr.length, target);
        if(ans) {
            System.out.println("YES");
        } else {
            System.out.println("NO");
        }
    }

    public  static boolean isop(long[] arr, int n, long target) {
//       base case
        if(n == 0) {
             return false;
        }
        if(n == 1) {
            if(arr[0] == target) {
                return true;
            } else {
                return false;
            }
        }

//        faith and work

        boolean additionFaith = isop(arr, n -1, target + arr[n - 1]);
        boolean subtractionFaith = isop(arr, n -1, target - arr[n - 1]);

        return additionFaith || subtractionFaith;

    }
}
