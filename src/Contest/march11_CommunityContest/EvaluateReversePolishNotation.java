package Contest.march11_CommunityContest;

import java.util.*;

public class EvaluateReversePolishNotation {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.nextLine();
        Stack<Integer> stack = new Stack<>();

        while (n-- > 0) {
            String s = sc.nextLine();
            int a = 0, b = 0;

            switch (s) {
                case "+":
                    a = stack.pop();
                    b = stack.pop();
                    stack.push(b + a);
                    break;

                case "-":
                    a = stack.pop();
                    b = stack.pop();
                    stack.push(b - a);
                    break;

                case "*":
                    a = stack.pop();
                    b = stack.pop();
                    stack.push(b * a);
                    break;

                case "/":
                    a = stack.pop();
                    b = stack.pop();
                    stack.push(b / a);
                    break;

                default:
                    stack.push(Integer.parseInt(s));
                    break;
            }
        }
        System.out.print(stack.pop());
        sc.close();
    }
}
