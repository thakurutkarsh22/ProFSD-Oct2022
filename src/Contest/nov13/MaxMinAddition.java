package Contest.nov13;

import java.io.*;

public class MaxMinAddition {

    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String s = br.readLine();
        String[] str = s.split(" ");
        long a = Long.parseLong(str[0]);
        long n = Long.parseLong(str[1]);
        long ans = a;
        for (int i = 0; i < n; i++) {
            long max = Long.MIN_VALUE;
            long min = Long.MAX_VALUE;
            // checking digits
            while (ans > 0) {
                max = Math.max(max, (ans % 10));
                min = Math.min(min, (ans % 10));
                ans /= 10;
            }
            // if minimum digit will be zero then
            // no need to add max*min futher
            if (min == 0) {
                break;
            }
            a += max * min;
            ans = a;
        }
        System.out.print(a);
    }
}
