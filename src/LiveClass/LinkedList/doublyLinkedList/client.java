package LiveClass.LinkedList.doublyLinkedList;

public class client {
    public static void main(String[] args) throws Exception {
        DoublyLinkedList doublyLinkedList = new DoublyLinkedList();
        System.out.println(doublyLinkedList);

//        ------- add first ----------
//        doublyLinkedList.addFirst(12);
//        doublyLinkedList.addFirst(1);
//
//        System.out.println(doublyLinkedList.size());
//        doublyLinkedList.display();
//        doublyLinkedList.displayReverse();

//        ------- add last ----------
        doublyLinkedList.addLast(12);
        doublyLinkedList.addLast(1);

        System.out.println(doublyLinkedList.size());
        doublyLinkedList.display();
        doublyLinkedList.displayReverse();


//        ------- add at ----------
        doublyLinkedList.addAt(-100, 3);
//        doublyLinkedList.addFirst(99);

        System.out.println(doublyLinkedList.size());
        doublyLinkedList.display();
        doublyLinkedList.displayReverse();

    }
}
