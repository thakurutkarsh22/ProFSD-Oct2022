package LiveClass.Stack;

import LiveClass.LinkedList.LinkedList;


//TODO: implement all the ways tp create stack and queues. DSA-2
public class StackUsingLL {

    private LinkedList linkedListForStack;
    StackUsingLL() {
        linkedListForStack = new LinkedList();
    }
    public int push(int item) {
        this.linkedListForStack.addFirst(item);
        return item;
    }
    public int pop() throws Exception {
        if(this.linkedListForStack.size() == 0) {
            throw new Exception("EmptyStackException m");
        }
        int removedValue =  this.linkedListForStack.removeFirst();
        return removedValue;
    }
    public int peek() throws Exception {

        if(this.linkedListForStack.size() == 0) {
            throw new Exception("EmptyStackException m");
        }
        return this.linkedListForStack.getAt(0);
    }

    public boolean isEmpty() {
        return this.linkedListForStack.size() == 0;
    }

    public int size() {
        return this.linkedListForStack.size();
    }

    @Override
    public String toString() {
        return this.linkedListForStack.displayReverse();
    }

}
