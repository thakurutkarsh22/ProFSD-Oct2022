package Contest.Dec18;

import java.util.*;

public class Fast_Search {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        int q = sc.nextInt();
        while (q-- > 0) {
            int l = sc.nextInt();
            int r = sc.nextInt();
            int cnt = upperBound(arr, n, r) - lowerBound(arr, n, l);
            System.out.print(cnt + 1 + " ");
        }
        sc.close();
    }

    public static int lowerBound(int[] arr, int n, int l) {
        int st = 0, en = n - 1, mid = 0;
        while (st <= en) {
            mid = (en + st) / 2;
            if (arr[mid] >= l) {
                en = mid - 1;
            } else
                st = mid + 1;
        }
        return st;
    }

    public static int upperBound(int[] arr, int n, int r) {
        int st = 0, en = n - 1, mid = 0;
        while (st <= en) {
            mid = (en + st) / 2;
            if (arr[mid] <= r) {
                st = mid + 1;
            } else
                en = mid - 1;
        }
        return en;
    }
}
