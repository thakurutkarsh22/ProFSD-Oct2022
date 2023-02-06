package Contest.feb5;

public class DeleteNodeWithoutHeadPointer {
    class Node {
        Node next;
        int val;

        Node(int val) {
            this.val = val;
            next = null;
        }
    }

    public static void deleteNode(Node D) {
        // store next node
        Node nextD = D.next;
        // manipulate curr node and next node's values
        D.val = D.next.val;
        // delete next node
        D.next = D.next.next;
        nextD.next = null;
    }
}
