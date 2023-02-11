package LiveClass.Stack;

import Util.util;

import java.util.HashMap;
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

//        String ValidParenthesis = "[](){([]{[}[]])}";
//        Boolean validParenthesisAns = isPeranthesisValid(ValidParenthesis);
//        System.out.println(validParenthesisAns + " is valid parenthesis");


//        int[] nextGreaterElementArr = new int[]{1, 2, 7, 6, 25, 13, 12, 19, 10};
//        int[] ansNextGreaterElementArr = nextGreaterElement(nextGreaterElementArr);
//        util.printArrayInt(ansNextGreaterElementArr, "Next greatest element");

//        int minimumOperationAns = minNoOfOperationBalance("}{{{");
//        System.out.println(minimumOperationAns);


        System.out.println(InfixToPostFix("a-b/c-d*c"));


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


    /*
        Question: Next Greater Element
        Input: 1 2 7 6 25 13 12 19 10.
        Output: 2 7 25 25 -1 19 19 -1 -1
     */

    public static int[] nextGreaterElement(int[] arr) {

        HashMap<Integer, Integer> map = new HashMap<>();

        Stack<Integer> stack = new Stack<>();
        stack.push(0);

        for(int i=1; i<arr.length;i++) {
            int item = arr[i];

            while(!stack.isEmpty() && (arr[stack.peek()] < item)) {
                int popIndex = stack.pop();
                map.put(popIndex, item);
            }
            stack.push(i);

        }

        while(!stack.isEmpty()) {
            int popIndex = stack.pop();
            map.put(popIndex, -1);
        }

        int[] ansArr = new int[arr.length];
        for (int i = 0; i < ansArr.length; i++) {
            ansArr[i] = map.get(i);
        }

        return ansArr;

    }

    /*
        Question: Min Operations to Balance the Barackets.
        Input: "}}{{}}{{"
        Output: 2
     */

    public static int minNoOfOperationBalance(String str) {
        if(str.length() %2 !=0) {
            return -1;
        }
        Stack<Character> stack = new Stack<>();
        stack.push(str.charAt(0));

        for (int i = 1; i < str.length(); i++) {
            char ch = str.charAt(i);

            if(checkIfOpeaningBracket(ch)) {
                stack.push(ch);
            } else if (!checkIfOpeaningBracket(ch) &&
                    !stack.isEmpty() && checkIfOpeaningBracket(stack.peek())) {
                stack.pop();
            } else {
                stack.push(ch);
            }
        }

        int openBracketCount = 0;
        int closingBracketCount = 0;

        while(!stack.isEmpty()) {
            char popChar = stack.pop();

            if(checkIfOpeaningBracket(popChar)) {
                openBracketCount++;
            } else {
                closingBracketCount++;
            }
        }

//        System.out.println(openBracketCount + " : " + closingBracketCount);

        // CASE - I (when opeaning and closing brackets are divisible by 2)
        //        CASE - II (when opeaning and closing brackets are not divisible by 2)
        if(openBracketCount %2 ==0 && closingBracketCount%2 == 0) {
            return (openBracketCount + closingBracketCount)/2;
        } else {
            return (openBracketCount + closingBracketCount + 2)/2;
        }
    }

    public static boolean checkIfOpeaningBracket(char ch) {
        return (ch == '{' || ch == '(' || ch == '[') ? true : false;
    }



    /*
        Question: Infix To postfix
        Input: "a-b/c - d*c
        Output: abc/-dc*-
     */

    public static String InfixToPostFix(String infixStr) {

        StringBuilder sb = new StringBuilder();
        Stack<Character> stack = new Stack<>();

        for (int i = 0; i < infixStr.length(); i++) {
            char ch = infixStr.charAt(i);
            if(ch >= 'a' && ch <= 'z') {
                sb.append(ch);
            } else {
                int precedenceOfChar = getPrecedence(ch);
//                removing higher precedence from stack || removing the same precedence from stack
                while(!stack.isEmpty() && getPrecedence(stack.peek()) >= precedenceOfChar) {
                    char popChar = stack.pop();
                    sb.append(popChar);
                }
                stack.push(ch);
            }
        }

        while (!stack.isEmpty()) {
            char popChar = stack.pop();
            sb.append(popChar);
        }

        return sb.toString();
    }

    public static int getPrecedence(char ch) {
        if(ch == '-' || ch == '+') {
            return 1;
        } else {
            return 2;
        }
    }





}
