package Contest.feb19_GroupContest;

public class IntersectionOfTwoLinkedLists {
    class Node {
        int data;
        Node next, Child;

        Node(int item) {
            data = item;
            next = Child = null;
        }
    }

    public static Node intersection(Node head1, Node head2) {
        // Enter your code here
        if (head1 == null || head2 == null)
            return null;
        Node one = head1;
        Node sec = head2;
        while (one != sec) {
            one = one == null ? head2 : one.next;
            sec = sec == null ? head1 : sec.next;
        }
        return one;
    }
}
