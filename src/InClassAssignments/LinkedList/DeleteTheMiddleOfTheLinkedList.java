package InClassAssignments.LinkedList;




public class DeleteTheMiddleOfTheLinkedList {

    class Node {
        Node next;
        int val;

        Node(int val) {
            this.val = val;
            next = null;
        }
    }


    public static Node deleteMiddleElement(Node head) {
        // return the head of the modified Linked List

        Node temp = head;
        int count = 0;


        while(temp != null) {
            count++;
            temp = temp.next;
        }

        if(count == 1) {
            head.val = -1;
            return head;
        }

        if(count == 2) {
            head.next = null;
            return head;
        }

        if(count >= 3) {
            temp = head;
            int middleMinusOneElement = count/2 -1;

            while(middleMinusOneElement-- !=0) {
                temp = temp.next;
            }

            Node nextElement = temp.next.next;
            temp.next = nextElement;

            return head;

        }

        return  null;


    }
}
