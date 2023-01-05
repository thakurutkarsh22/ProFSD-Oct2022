package Contest.nov13;

import java.io.*;

public class Buildings {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine());
        String s = br.readLine();
        String[] str = s.split(" ");
        long arr[] = new long[n];
        for (int i = 0; i < n; i++)
            arr[i] = Long.parseLong(str[i]);
        int cnt = 1;
        long max = arr[0];
        for (int i = 1; i < n; i++) {
            long curr = arr[i];
            if (max < curr) {
                cnt++;
                max = Math.max(max, curr);
            }
        }
        System.out.print(cnt);
    }
}
