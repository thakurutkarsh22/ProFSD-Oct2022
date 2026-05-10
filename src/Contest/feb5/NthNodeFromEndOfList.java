package Contest.feb5;

public class NthNodeFromEndOfList {
    class Node {
        int data;
        Node next;

        Node(int d) {
            data = d;
            next = null;
        }
    }

    static int getNthFromLast(Node head, int n) {
        // taking two pointers
        Node slow = head;
        Node fast = head;
        // first move fast n-1 steps
        for (int i = 0; i < n - 1; i++) {
            if (fast.next == null) {
                return -1;
            }
            fast = fast.next;
        }
        // move both pointers simultaneously
        while (fast.next != null) {
            fast = fast.next;
            slow = slow.next;
        }
        // whenever fast's next will reach to end
        // slow will reach at nth node from end
        return slow.data;
    }
}
