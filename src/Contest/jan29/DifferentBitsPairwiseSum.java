package Contest.jan29;

import java.util.Scanner;

public class DifferentBitsPairwiseSum {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }

        long sum = 0;
        int sb = 0, nsb = 0;
        for (int i = 0; i < 32; i++) {
            sb = 0;
            nsb = 0;
            for (int j = 0; j < n; j++) {
                if (((arr[j] >> i) & 1) != 0) {
                    sb++;
                } else {
                    nsb++;
                }
            }
            sum += (sb * 2L * nsb);
        }
        System.out.print(sum);
        sc.close();
    }
}
