package Contest.nov20;

import java.io.*;
import java.util.*;

public class CrazyWalk {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        int m = Integer.parseInt(st.nextToken());
        int n = Integer.parseInt(st.nextToken());
        // if row or column is odd then can reach all blocks
        if (n % 2 != 0 || m % 2 != 0)
            System.out.print("YES");
        else
            System.out.print("NO");
    }
}
