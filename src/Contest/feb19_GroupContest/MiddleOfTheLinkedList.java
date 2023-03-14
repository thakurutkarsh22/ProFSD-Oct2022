package Contest.feb19_GroupContest;

public class MiddleOfTheLinkedList {
    class Node {
        int val;
        Node next, Child;

        Node(int item) {
            val = item;
            next = Child = null;
        }
    }

    public static void MiddleElement(Node head) {
        if (head.next == null) {
            System.out.print(head.val);
            return;
        }
        Node slow = null, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow == null ? head : slow.next;
            fast = fast.next.next;
        }
        if (fast == null) {
            System.out.print(slow.val + " " + slow.next.val);
        } else {
            System.out.print(slow.next.val);
        }
    }
}
