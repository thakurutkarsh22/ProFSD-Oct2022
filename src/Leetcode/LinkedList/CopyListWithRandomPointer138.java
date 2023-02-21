package Leetcode.LinkedList;


class Node {
    int val;
    Node next;
    Node random;

    public Node(int val) {
        this.val = val;
        this.next = null;
        this.random = null;
    }
}

public class CopyListWithRandomPointer138 {
    public Node copyRandomList(Node head) {

//        CLone the nodes ......

        Node iter = head;

        while(iter != null) {
            Node clone = new Node(iter.val);
            Node nextElementOfIter = iter.next;
            iter.next = clone;
            clone.next = nextElementOfIter;

            iter = nextElementOfIter;
        }


//        Lets Assign the random pointers to the cloneList...

        iter = head;

        while(iter != null) {
            if(iter.random != null) {
                Node clone = iter.next;
                clone.random = iter.random.next;
            }

            iter = iter.next.next;
        }

//        Break the list (decouple the list)...


        Node original = head;
        Node clone = original.next;
        Node cloneAns = original.next;

        while(clone.next != null) {
            Node nextOriginal = original.next.next;
            Node nextClone = clone.next.next;

            original.next = nextOriginal;
            clone.next = nextClone;

            original = nextOriginal;
            clone = nextClone;
        }

        original.next = original.next.next;

        return cloneAns;
    }
}
