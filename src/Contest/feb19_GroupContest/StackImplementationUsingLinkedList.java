package Contest.feb19_GroupContest;

public class StackImplementationUsingLinkedList {

    class Node {
        Node next;
        int val;

        Node(int val) {
            this.val = val;
            next = null;

        }
    }

    Node top = null;

    public void push(int x) {
        // enter your code here
        Node newNode = new Node(x);
        if (top == null) {
            top = newNode;
            return;
        }
        newNode.next = top;
        top = newNode;
    }

    public void pop() {
        // enter your code here
        if (top == null) {
            return;
        }
        Node temp = top;
        top = top.next;
        temp.next = null;
    }

    public void top() {
        // enter your code here
        System.out.println(top == null ? 0 : top.val);
    }
}
