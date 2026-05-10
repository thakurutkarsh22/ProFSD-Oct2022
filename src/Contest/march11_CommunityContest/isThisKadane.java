package Contest.march11_CommunityContest;

import java.util.Scanner;

public class isThisKadane {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }
        sc.close();

        int largest = -31, maxSum = Integer.MIN_VALUE, currSum = 0;
        for (int i = 0; i < n; i++) {
            currSum += arr[i];
            largest = Math.max(largest, arr[i]);
            maxSum = Math.max(maxSum, currSum - largest);
            if (currSum < 0) {
                currSum = 0;
                largest = -31;
            }
        }
        System.out.print(maxSum);

    }
}
