package Leetcode;

public class SearchInsertPosition35 {
    public static void main(String[] args) {

    }

    public int searchInsert(int[] nums, int target) {
        int left = 0;
        int right = nums.length -1;

        while(left< right) {
            int mid = left + (right - left) /2;

            if(nums[mid] == target) {
                return mid;
            } else if(nums[mid] > target) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }

        return left;
    }
}
