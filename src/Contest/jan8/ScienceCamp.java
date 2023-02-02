package Contest.jan8;

import java.util.*;

public class ScienceCamp {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }

        Arrays.sort(arr);
        int sum = arr[0] + arr[n - 1];
        boolean check = true;
        long total = 0;
        int i = 0, j = n - 1;
        while (i < j) {
            if (arr[i] + arr[j] == sum) {
                total += arr[i] * 1L * arr[j];
            } else {
                check = false;
                break;
            }
            i++;
            j--;
        }
        System.out.print(check ? total : -1);
        sc.close();
    }
}
