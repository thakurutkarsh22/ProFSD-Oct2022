package InClassAssignments.LinkedList;

public class InsertNodeAtTheHead {
    public static Node addElement(Node head, int k) {

        Node newNode = new Node(k);
        newNode.next = head;

        head = newNode;

        return newNode;

    }
}
