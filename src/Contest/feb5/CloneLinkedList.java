package Contest.feb5;

public class CloneLinkedList {
    static class Node {
        Node next;
        Node random;
        int val;

        Node(int val) {
            this.val = val;
            next = null;
            random = null;
        }
    }

    public static Node CloneList(Node head) {
        if (head == null) {
            return null;
        }

        // First step: make copy of each node,
        // and link them together side-by-side in a single list.
        Node temp = head, next = null;
        while (temp != null) {
            Node clone = new Node(temp.val);
            next = temp.next;
            temp.next = clone;
            clone.next = next;
            temp = next;
        }

        // Second step: assign random pointers for the copy nodes.
        temp = head;
        while (temp != null) {
            if (temp.random != null) {
                temp.next.random = temp.random.next;
            }
            temp = temp.next.next;
        }

        // Third step: restore the original list, and extract the copy list.
        Node original = head, clone = head.next, cloneAns = original.next;
        while (clone.next != null) {
            Node nextOfOriginal = original.next.next;
            Node nextOfClone = clone.next.next;

            original.next = nextOfOriginal;
            clone.next = nextOfClone;
            clone = nextOfClone;
            original = nextOfOriginal;
        }
        original.next = original.next.next;
        return cloneAns;
    }
}
