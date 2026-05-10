package Contest.feb19_GroupContest;

public class Add1ToNumberRepresentedAsLinkedList {

    static class Node {
        Node next;
        int data;

        Node(int data) {
            this.data = data;
            next = null;
        }
    }

    static Node addOne(Node head) {
        // return the head of the modified linked list
        Node ans = new Node(0);
        Node ansHead = ans, temp = head;
        int carry = 1;
        while (temp != null) {
            carry += temp.data;
            ans.next = new Node(carry % 10);
            ans = ans.next;
            carry /= 10;
            temp = temp.next;
        }
        if (carry == 1) {
            ans.next = new Node(1);
        }
        return ansHead.next;
    }
}