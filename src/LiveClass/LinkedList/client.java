package LiveClass.LinkedList;

public class client {

    private class Node {
        int val;
        Node next;

        Node() {

        }

        Node(int value) {
            this.val = value;
            this.next = null;
        }
    }
    public static void main(String[] args) throws Exception {
        LinkedList linkedList = new LinkedList();
//        System.out.println(linkedList.head);
//        System.out.println(linkedList.tail);
//        System.out.println(linkedList.size);

//        ----- add first ---------
//        linkedList.addFirst(1);
//        linkedList.addFirst(2);
//        2 -> 1

//        System.out.println("size: " + linkedList.size);
//        linkedList.display();


//        ------- add last ------
        linkedList.addLast(1);
        linkedList.addLast(2);
        linkedList.addFirst(3);

//        System.out.println("size: " + linkedList.size());
//        linkedList.display();


//        --------- add at ---------
        linkedList.addAt(1, 62);
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();



//        ------------- remove first -----------
//        linkedList.removeFirst();
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();


//        ------------- remove last -----------
//        linkedList.removeLast();
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();

        //        ------------- remove at -----------
//        linkedList.removeAt(1);
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();


//        ---------- reverse the Linked List --------
        System.out.println("------------- reverse the Linked List -----------" );
//        linkedList.reverseLinkedListDataIndex();
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();


//        linkedList.reverseLinkedListPointerIndex();
//        System.out.println("size: " + linkedList.size());
//        linkedList.display();


//        ------------- Middle of the LinkedList ------------
        linkedList.addAt(2, 80);
//        linkedList.display();
//        System.out.println(linkedList.middleNode());

//        ------------ Nth element from the last ------------
        linkedList.addFirst(12);
        linkedList.addFirst(13);
        linkedList.addFirst(14);
        linkedList.addFirst(15);

        linkedList.display();

        System.out.println(linkedList.nthElementFromLast(3));

//        System.out.println("---------- deleted Element from the linked list -----");
//        linkedList.deletenthElementFromLast(3);
//
//
//        linkedList.display();


//        -------------- check if linkedlist is cyclic or not ----------

        LinkedList.Node thirdNodeFromStart = linkedList.getNodeAt(2);
        System.out.println(thirdNodeFromStart.val + " thirdNode from start");
        LinkedList.Node lastNode = linkedList.getNodeAt(linkedList.size()-1);
        System.out.println(lastNode.val +"last node ");

        System.out.println(linkedList.isCyclic()+ " is cyclic");

//        this is making the Linked list cyclic
        lastNode.next = thirdNodeFromStart;

        System.out.println(linkedList.isCyclic()+ " is cyclic");


//        ---------- get the node of the cycle -------------

        System.out.println(linkedList.startingNodeOfCycle().val + " loop start from here");







    }
}
