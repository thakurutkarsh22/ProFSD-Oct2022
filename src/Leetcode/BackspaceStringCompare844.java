package Leetcode;

import java.util.Stack;

public class BackspaceStringCompare844 {
    public static void main(String[] args) {
        backspaceCompare("ab#c", "ad#c");
    }

    public static boolean backspaceCompare(String s, String t) {
        Stack<Character> stackforS = new Stack<>();
        Stack<Character> stackforT = new Stack<>();

//        Operation for string s

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if(ch != '#') {
                stackforS.push(ch);
            } else {
                if(!stackforS.isEmpty()) {
                    stackforS.pop();
                }

            }
        }

        //        Operation for string t

        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if(ch != '#') {
                stackforT.push(ch);
            } else {
                if(!stackforT.isEmpty()) {
                    stackforT.pop();
                }
            }
        }

        System.out.println(stackforS);
        System.out.println(stackforT);

        while(!stackforS.isEmpty() && !stackforT.isEmpty()) {
            char popVal1 = stackforS.pop();
            char popVal2 = stackforT.pop();
            if(popVal2 != popVal1) {
                return false;
            }
        }

        if(!stackforS.isEmpty() || !stackforT.isEmpty()) {
            return false;
        }

        return true;
    }
}
