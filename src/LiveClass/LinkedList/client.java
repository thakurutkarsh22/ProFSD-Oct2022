package LiveClass.LinkedList;

public class client {
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
        System.out.println("size: " + linkedList.size());
        linkedList.display();



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


        linkedList.reverseLinkedListPointerIndex();
        System.out.println("size: " + linkedList.size());
        linkedList.display();






    }
}
