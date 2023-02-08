package LiveClass.Stack;

import java.util.Stack;

public class LiveClass1 {
    public static void main(String[] args) {
//        Stack<Integer> stack = new Stack<>(); // java peops
//
////        System.out.println(stack.pop());
//        System.out.println(stack.isEmpty()); // true
//        System.out.println(stack.size());
//
////        ----------   Insert a value ----------------
//        stack.push(1);
//        stack.push(2);
//        stack.push(3);
//
//        System.out.println(stack.isEmpty()); //false
//        System.out.println(stack);
//
////        --------------- Remove top of the stack ---------------
//        System.out.println(stack.pop() + " popped value");
//        System.out.println(stack);
//
//
////        ------------- Peek to see what is on the top of the stack ----
//        System.out.println(stack.peek() + " peeked value");
//        System.out.println(stack);





//       ------------- QUESTIONS -------------

//        String reverseWordString = "america";
//        String reverseWordAns = reverseWord(reverseWordString);
//        System.out.println(reverseWordAns);

        String ValidParenthesis = "[](){([]{[}[]])}";
        Boolean validParenthesisAns = isPeranthesisValid(ValidParenthesis);
        System.out.println(validParenthesisAns + " is valid parenthesis");

    }

    /*
        Question: Reverse the word.
        Input: "america"
        output: "acirema"
     */

    public static String reverseWord(String word) {
        Stack<Character> stack = new Stack();

        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            stack.push(ch);
        }

        StringBuilder sb = new StringBuilder();

        while(!stack.isEmpty()) {
            char popVal = stack.pop();
            sb.append(popVal);
        }

        return sb.toString();

    }

    /*
        Question: Valid Peranthesis
        Input: [({)}]
        Output: false

        Input: [(){}]
        Output: true

     */

    private static char getPair(char ch) {
        char pair = ')';
        if(ch == ')') {
            pair = '(';
        } else if (ch == ']') {
            pair = '[';
        } else if(ch == '}') {
            pair = '{';
        }

        return pair;
    }

    public static boolean isPeranthesisValid(String str) {
        Stack<Character> stack = new Stack<>();

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if(ch == '(' || ch == '{' || ch == '[') {
                // this is for opeaning bracket.
                stack.push(ch);
            } else {
                // this is for closing bracket....
                char pair = getPair(ch);



                if(stack.isEmpty()) {
                    return false;
                }

                char topOfTheStack = stack.peek();

                if(topOfTheStack == pair) {
                    stack.pop();
                } else {
                    return false;
                }
            }
        }

        return stack.isEmpty();

    }

}
