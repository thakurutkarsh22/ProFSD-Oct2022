package Contest.Dec18;

import java.util.Scanner;

public class SearchingAnElementInASortedArray {

    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while (T-- != 0) {
            int n = scn.nextInt();
            int target = scn.nextInt();

            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = scn.nextInt();
            }

            int ans = binarySearch(arr, target);
            System.out.println(ans);

        }

        scn.close();

    }

    public static int binarySearch(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (arr[mid] == target) {
                return mid;
            } else if (target > arr[mid]) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }

        }

        return -1;
    }
}
