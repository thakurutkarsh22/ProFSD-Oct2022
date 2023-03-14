package Contest.march11_CommunityContest;

import java.util.Scanner;

public class MoveZeroes {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = sc.nextInt();
            }
            int l = 0, r = 0;
            while (r < n) {
                if (arr[r] != 0) {
                    arr[l++] = arr[r];
                }
                r++;
            }
            while (l < n) {
                arr[l++] = 0;
            }
            for (int i : arr) {
                System.out.print(i + " ");
            }
            System.out.println();
        }
        sc.close();
    }
}
