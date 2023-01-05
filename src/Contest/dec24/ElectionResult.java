package Contest.dec24;

import java.io.*;
import java.util.*;

public class ElectionResult {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        long x = Long.parseLong(br.readLine());
        long[] arr = new long[7];
        StringTokenizer st = new StringTokenizer(br.readLine());
        for (int i = 0; i < 7; i++) {
            arr[i] = Long.parseLong(st.nextToken());
        }
        if (arr[0] + arr[1] > (x / 2)) {
            System.out.print("NDA");
        } else if (arr[2] + arr[3] > (x / 2)) {
            System.out.print("UPA");
        } else if (arr[4] + arr[5] + arr[6] > (x / 2)) {
            System.out.print("Left");
        } else {
            System.out.print("No government");
        }
    }
}
