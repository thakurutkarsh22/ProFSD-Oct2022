package Leetcode;

public class FindMinimumInRotatedSortedArray153 {
    public static void main(String[] args) {

    }

    public int findMin(int[] nums) {
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


        return nums[right];



    }
}
