package Contest.nov13;

import java.io.*;

public class RGB {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int N = Integer.parseInt(br.readLine());
        long B = 0;
        for (long R = 1; R < N; R++) {
            for (long G = 1; G < N; G++) {
                if (R * G >= N)
                    break;
                else
                    B += N - (R * G);
            }
        }
        System.out.print(B);
    }
}
