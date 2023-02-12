package Leetcode;

import Util.util;

import java.util.Stack;

public class AsteroidCollision735 {
    public static void main(String[] args) {
        int[] ans = asteroidCollision(new int[]{8, -8});
        util.printArrayInt(ans, "astroid collision");

    }
// TC => [-2,1,1,-1]
//    TC => [-2,-2,1,-1]
//    These test case handling will be opposite when included in while loop.
//    get things seperated..
// TC => [8,-8]

    public static int[] asteroidCollision(int[] asteroids) {
        Stack<Integer> stack = new Stack<>();

        for (int i = 0; i < asteroids.length; i++) {
            int astroid = asteroids[i];

//            condition for collision....
            if (!stack.isEmpty() && stack.peek() > 0 && astroid < 0 ) {
                int diff = astroid + stack.peek();

                while (diff < 0 && !stack.isEmpty() && stack.peek()> 0) {
                    stack.pop();

//                   update the difference
                    if(!stack.isEmpty()) {
                        diff = astroid + stack.peek();
                    }
                }

// when astorids are of equal weight and in opposite direction.

                if(diff == 0) {
                    stack.pop();
                    continue;
                }


//                if diff > 0 , the -ve astroid is not powerfull enough
                if(diff > 0) {
                    continue;
                }


            }

//            astdoid would be added to the ans..
            stack.push(astroid);

        }

        int[] ansArr = new int[stack.size()];
        for (int i = ansArr.length -1; i >=0 ; i--) {
            ansArr[i] = stack.pop();
        }
        return ansArr;
    }
}
