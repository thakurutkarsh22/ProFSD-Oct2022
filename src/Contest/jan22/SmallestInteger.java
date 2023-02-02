package Contest.jan22;

import java.util.*;

public class SmallestInteger {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        long smallestNum = 1;
        for (int i = 0; i < n; i++) {
            if (arr[i] > smallestNum) {
                break;
            } else {
                smallestNum += arr[i];
            }
        }
        System.out.print(smallestNum);
        sc.close();
    }
}
