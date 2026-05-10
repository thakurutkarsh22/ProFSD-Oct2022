package Contest.feb5;

public class DeleteEvenNodesFromTheList {

    class Node {
        int data;
        Node next;

        Node(int x) {
            data = x;
            next = null;
        }
    }

    static Node deleteEven(Node head) {
        // Enter your code here
        if (head == null || head.next == null) {
            return head;
        }
        Node curr = head.next, prev = head;

        while (curr != head) {
            // if data is even then store it in temporary node
            // and change previous node's next to current node next
            // then change temporary node's next to null to complete deletion
            if ((curr.data & 1) == 0) {
                Node currNode = curr;
                prev.next = curr.next;
                curr = curr.next;
                currNode.next = null;
            }
            // else run both prev and curr pointers ahead
            else {
                prev = curr;
                curr = curr.next;
            }
        }
        return head;
    }
}
