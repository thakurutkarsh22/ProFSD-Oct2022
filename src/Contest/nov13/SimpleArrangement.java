package Contest.nov13;

import java.io.*;

public class SimpleArrangement {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine());
        String s = br.readLine();
        String[] str = s.split(" ");
        int arr[] = new int[n];
        for (int i = 0; i < n; i++)
            arr[i] = Integer.parseInt(str[i]);
        for (int i = 0; i < n; i++) {
            System.out.print(arr[arr[i]] + " ");
        }
    }
}
