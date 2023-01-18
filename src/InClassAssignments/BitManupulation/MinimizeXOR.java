package InClassAssignments.BitManupulation;

import java.util.Arrays;
import java.util.Scanner;

public class MinimizeXOR {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        System.out.println(getMinXor(arr));
    }

    public static int getMinXor(int[] arr) {
        Arrays.sort(arr);

        int ans = Integer.MAX_VALUE;
        for (int i = 0; i < arr.length - 1; i++) {
            int val = arr[i];
            int nextval = arr[i + 1];

            int xorAns = val ^ nextval;
            ans = Math.min(xorAns, ans);
        }

        return ans;
    }

}
