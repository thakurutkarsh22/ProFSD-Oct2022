package Contest.feb19_GroupContest;

public class FlattenMultilevelLinkedList {
    class Node {
        int data;
        Node next, Child;

        Node(int item) {
            data = item;
            next = Child = null;
        }
    }

    static void flattenList(Node head) {
        // Your code here
        if (head == null) {
            return;
        }
        Node temp = null;
        Node tail = head;
        while (tail.next != null) {
            tail = tail.next;
        }
        Node curr = head;
        while (curr != tail) {
            if (curr.Child != null) {
                tail.next = curr.Child;
                temp = curr.Child;
                while (temp.next != null) {
                    temp = temp.next;
                }
                tail = temp;
            }
            curr = curr.next;
        }
    }
}
