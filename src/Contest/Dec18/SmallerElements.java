package Contest.Dec18;

import java.util.Scanner;

public class SmallerElements {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        int q = sc.nextInt();
        for (int i = 0; i < n; i++) {
            int k = sc.nextInt();
            int ans = smallerElements(arr, n, k);
            System.out.println(ans);
        }
        sc.close();
    }

    static int smallerElements(int arr[], int n, int k) {
        // Enter your code here
        int l = 0, r = n - 1, mid = 0;
        while (l <= r) {
            mid = l + (r - l) / 2;
            if (arr[mid] <= k) {
                if (arr[mid] == k) {
                    return mid + 1;
                }
                l = mid + 1;
            } else
                r = mid - 1;
        }
        return l;
    }
}
