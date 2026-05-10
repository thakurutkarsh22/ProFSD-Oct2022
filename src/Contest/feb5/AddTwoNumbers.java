package Contest.feb5;

public class AddTwoNumbers {
    static class Node {
        Node next;
        int data;

        Node(int data) {
            this.data = data;
            next = null;
        }
    }

    static Node addNumber(Node first, Node second) {
        // return the head of the modified linked list
        Node dummyStart = new Node(0);
        Node curr = dummyStart;
        int sum = 0;

        while (first != null || second != null) {
            if (first != null) {
                sum += first.data;
                first = first.next;
            }
            if (second != null) {
                sum += second.data;
                second = second.next;
            }

            // to store last digit take mod by 10
            curr.next = new Node(sum % 10);
            curr = curr.next;
            // to store carry divide sum by 10
            sum /= 10;
        }

        // if any carry remaining
        if (sum == 1) {
            curr.next = new Node(1);
        }

        return dummyStart.next;
    }
}
