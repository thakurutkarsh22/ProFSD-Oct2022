package Leetcode;

import java.util.Stack;

class MinStack {

    Stack<Integer> originalVal;
    Stack<Integer> minVal;

//    int MIN_VAL = Integer.MAX_VALUE;

    public MinStack() {
        originalVal = new Stack<>();
        minVal = new Stack<>();
    }

    public void push(int val) {
        this.originalVal.push(val);

        if(this.minVal.isEmpty()) {
            this.minVal.push(val);
        } else {
            int peekOfMinStack = this.minVal.peek();
            if(peekOfMinStack < val) {
                this.minVal.push(peekOfMinStack);
            } else {
                this.minVal.push(val);
            }
        }
    }

    public void pop() {
        if(this.originalVal.isEmpty()) {
            return;
        }

        this.originalVal.pop();
        this.minVal.pop();
    }

    public int top() {
        return this.originalVal.peek();
    }

    public int getMin() {
        return this.minVal.peek();
    }

//    TC => for every operation is O(1)
//    SC => O(n) for min stack...
}

class MinStackSingle {

    Stack<Integer> stack;

    int MIN_VAL = Integer.MAX_VALUE;

    public MinStackSingle() {
        stack = new Stack<>();
    }

    public void push(int val) {
        if(this.stack.isEmpty()) {
            this.stack.push(val);
        } else {
            if(val >= this.MIN_VAL) {
                this.stack.push(val);
            } else {
                int modifiedAns = 2*val - this.MIN_VAL;
                this.stack.push(modifiedAns);
                this.MIN_VAL = val;
            }
        }
    }

    public void pop() {
        if(stack.isEmpty()) {
            return;
        }

        int popVal = this.stack.pop();

        if(popVal < this.MIN_VAL) {
            this.MIN_VAL = 2*this.MIN_VAL - popVal;
        }


    }

    public int top() {
        if(this.stack.isEmpty()) {
            return -1;
        }

        if(this.stack.peek() < this.MIN_VAL) {
            return this.MIN_VAL;
        }

        return this.stack.peek();

    }

    public int getMin() {
        return this.MIN_VAL;
    }

//    TC => for every operation is O(1)
//    SC => O(1) for min stack...
}
