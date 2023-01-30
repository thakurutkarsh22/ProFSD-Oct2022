package Leetcode;

public class MinimumSizeSubarraySum209 {
    public static void main(String[] args) {

    }


    public int minSubArrayLen(int target, int[] nums) {
            int left = 0;
            int right = 0;

            int min = Integer.MAX_VALUE; // 0

            int currentWindowSum = 0;

            while(left < nums.length && right < nums.length) {
                currentWindowSum += nums[right];
                right++;

                while (currentWindowSum >= target) {
                    currentWindowSum -= nums[left];
                    left++;

                    min = Math.min(min, right - left + 1);

                }
            }

            if(min == Integer.MAX_VALUE) {
//                 we were not able to find the ans.
                return 0;
            } else {
                return min;
            }



    }


//    TC => O(n)
//    Sc => O(1)
}
