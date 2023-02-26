package InClassAssignments.Stacks;

import Util.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.Stack;

//TODO: see this asnwer is wrong..

public class EvaluateExpressionUsingStack {
    public static void main(String[] args) throws IOException {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        scn.nextLine();

        String[] str = scn.nextLine().split(" ");




        System.out.println(eval(str));

    }

    public static int eval(String[] postFixExpression) {
        Stack<Integer> stack = new Stack<>();

//        Case1: If you see Operand -> simply push in the stack
//        Case2: If you see any Operator
//            -> pop 2 values from the stack
//            -> Merge popVal2 operator and popVal1 in this sequence.
//            -> put the above result in the stack

        for (int i = 0; i < postFixExpression.length; i++) {
            char ch = postFixExpression[i].charAt(0);


            if(Character.isDigit(ch)) {
                stack.push(Integer.parseInt(ch+""));
            } else {
                int popVal1 = stack.pop();
                int popVal2 = stack.pop();

                switch (ch) {
                    case '+': {
                        int ans = popVal2 + popVal1;
                        stack.push(ans);
                        break;
                    }

                    case '-': {
                        int ans = popVal2 - popVal1;
                        stack.push(ans);
                        break;
                    }

                    case '/': {
                        int ans = popVal2 / popVal1;
                        stack.push(ans);
                        break;
                    }

                    case '*': {
                        int ans = popVal2 * popVal1;
                        stack.push(ans);
                        break;
                    }

                }
            }
        }

        return stack.peek();
    }
}
