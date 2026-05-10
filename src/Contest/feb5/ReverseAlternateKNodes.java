package Contest.feb5;

public class ReverseAlternateKNodes {
    class Node {
        Node next;
        int val;

        Node(int val) {
            this.val = val;
            next = null;
        }
    }

    public static Node ReverseAlternateK(Node head, int k) {
        // enter your code here
        if (head == null || k == 1) {
            return head;
        }
        Node currNode = head, prevNode = null, nextNode = null;
        int cntOfNodes = 0;

        // reverse first k nodes
        while (currNode != null && cntOfNodes < k) {
            nextNode = currNode.next;
            currNode.next = prevNode;
            prevNode = currNode;
            currNode = nextNode;
            cntOfNodes++;
        }

        // now move head to the kth  node because k nodes are reversed
        if (head != null) {
            head.next = currNode;
        }

        // skip next k nodes by moving currNode pointer 
        cntOfNodes = 0;
        while (cntOfNodes < k - 1 && currNode != null) {
            currNode = currNode.next;
            cntOfNodes++;
        }

        // we have to repeate this process until currNode becomes null
        // so recursive call for list starting from currNode's next and make 
        if (currNode != null) {
            currNode.next = ReverseAlternateK(currNode.next, k);
        }

        // prev is new head of the list so return prev
        return prevNode;
    }
}
