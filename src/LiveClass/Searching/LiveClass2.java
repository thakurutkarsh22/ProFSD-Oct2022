package LiveClass.Searching;

import java.util.Scanner;

public class LiveClass2 {
    public static void main(String[] args) {
        Scanner scn =  new Scanner(System.in);
        int n = scn.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        int findMinans = findMin(arr);
        System.out.println(findMinans);

        int searchInsertans = searchInsert(arr, 7);
        System.out.println(searchInsertans);
    }

    /*
        Leetcode: 153: Find Minimum in Rotated Sorted Array
        Input: nums = [3,4,5,1,2]
        Output: 1
        Explanation: The original array was [1,2,3,4,5] rotated 3 times.
     */

    public static int findMin(int[] nums) {
        int left = 0;
        int right = nums.length -1;

        while(left < right) {

            int mid = left + (right - left)/2;

            if(nums[mid] > nums[right]) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        return nums[left];
    }

    /*
        Leetcode: 35. Search Insert Position
        Input: nums = [1,3,5,6], target = 5
        Output: 2

        Input: nums = [1,3,5,6], target = 2
        Output: 1
     */

    public static int searchInsert(int[] nums, int target) {
        int left = 0;
        int right = nums.length -1;

        while(left <= right) {

            int mid = left + (right - left)/2;

            if(nums[mid] == target) {
                return mid;
            } else if(nums[mid] > target) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }

        }

        return left;
    }

    /*
        Leetcode: 162. Find Peak Element
        Input: nums = [1,2,3,1]
        Output: 2
        Explanation: 3 is a peak element and your function should return the index number 2.

        Input: nums = [1,2,1,3,5,6,4]
        Output: 5
        Explanation: Your function can return either index number 1 where the peak element is 2, or index number 5 where the peak element is 6.
     */

    public static int findPeakElement(int[] nums) {
        int left = 0;
        int right = nums.length -1;

        while(left < right) {
            int mid = left + (right - left)/2;

            if(nums[mid] < nums[mid + 1]) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        return left;
    }


}
