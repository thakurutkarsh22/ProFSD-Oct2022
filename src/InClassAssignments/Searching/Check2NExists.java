package InClassAssignments.Searching;

import java.util.Arrays;
import java.util.Scanner;

public class Check2NExists {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        boolean ans = checking2n(arr);
        if (ans) {
            System.out.println("YES");
        } else {
            System.out.println("NO");
        }

    }

    public static boolean checking2n(int[] arr) {
        Arrays.sort(arr);

        for (int i = 0; i < arr.length; i++) {
            int element = arr[i];
            int binarySearchAnswer = binarySearch(arr, 2 * element);
            if (binarySearchAnswer != -1) {
                return true;
            }
        }

        return false;
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
