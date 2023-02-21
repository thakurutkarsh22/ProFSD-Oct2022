package Leetcode;

import java.util.Scanner;
import java.util.Stack;

public class LargestRectangleInHistogram {
    public static void main(String[] args) {

    }

    public int largestRectangleArea(int[] heights) {
        int max = Integer.MIN_VALUE;
        Stack<Integer> stack = new Stack<>();
        stack.push(0);

        for (int i = 1; i < heights.length; i++) {

//            pop the values bec the shorter height element will destroy(shorten) the ans
            while (!stack.isEmpty() && heights[i] < heights[stack.peek()]) {
                int popVal = stack.pop();
                int height = heights[popVal];

                int leftBoundary;
                if(stack.isEmpty()) {
                    leftBoundary = -1;
                } else {
                    leftBoundary = stack.peek();
                }

                int distance = i - leftBoundary - 1;

                int area = distance * height;

                max = Math.max(max, area);

            }
// process for every height.
            stack.push(i);

        }


//        If there is value left inside stack that also could contribute to the ans

        while(!stack.isEmpty()) {
            int popVal = stack.pop();
            int height = heights[popVal];
            int leftBoundary;
            if(stack.isEmpty()) {
                leftBoundary = -1;
            } else {
                leftBoundary = stack.peek();
            }

            int distance = heights.length- leftBoundary - 1;

            int area = distance * height;

            max = Math.max(max, area);

        }

        return max;
    }
}
