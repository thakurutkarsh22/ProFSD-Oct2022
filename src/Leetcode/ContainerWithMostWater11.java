package Leetcode;

public class ContainerWithMostWater11 {
    public static void main(String[] args) {

    }


    public int maxArea(int[] height) {
        int left = 0;
        int right = height.length - 1;

        int maxWater = 0; // INTEGER.MIN_VALUE;

        while(left < right) {
            int minHeight = Math.min(height[left], height[right]);
            int distance = right - left;
            int storage = minHeight * distance;
            maxWater = Math.max(maxWater, storage);

            if(height[left] <= height[right]) {
                left++;
            } else {
                right--;
            }
        }
        return maxWater;

    }
}
