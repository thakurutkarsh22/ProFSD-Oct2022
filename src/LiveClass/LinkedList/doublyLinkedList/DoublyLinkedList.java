package LiveClass.LinkedList.doublyLinkedList;

import LiveClass.LinkedList.LinkedList;

//
public class DoublyLinkedList {

    Node head;
    Node tail;
    int size;

    private class Node {
        int val;
        Node next;

        Node prev;

        Node() {

        }

        Node(int value) {
            this.val = value;
            this.next = null;
            this.prev = null;
        }
    }

    DoublyLinkedList() {

    }

//    Head to tail printing
    public void display(){
        Node temp = this.head;

        while(temp != null) {
            System.out.print(temp.val+ " -> ");
            temp = temp.next;
        }

        System.out.println("null : Left to right");
    }

//    Tail to head printing
    public void displayReverse() {
        Node temp = this.tail;

        while(temp != null) {
            System.out.print(temp.val+ " -> ");
            temp = temp.prev;
        }

        System.out.println("null : Right to left");
    }

    public int size() {
        return this.size;
    }

    public Node getNodeAt(int index) throws Exception {
        if(this.size == 0) {
            throw new Exception("Size of LL is 0 right now");
        }

        if(index < 0 || index >= this.size) {
            throw new Exception("Index out of bound" + "Index: "
                    + index + " , Size: " + this.size);
        }

        Node temp = this.head;

        while(index-- != 0) {
            temp = temp.next;
        }

        return temp;

    }

    public void addLast(int data) {
//        Create the node and fill the data
        Node newNode = new Node(data);

//        attach to the Chain


        if(this.size == 0) {
            this.head = this.tail =  newNode;
            this.size = 1;
        } else {

            // Attach node to chain

            Node tailNode = this.tail;
            tailNode.next = newNode;
            newNode.prev = tailNode;

//            change the tail Pointer (update the summary)
            this.tail = newNode;

//          change the size
            this.size += 1;
        }
    }

    public void addFirst(int data) {
        //        Create the node and fill the data
        Node newNode = new Node(data);

//        attach to the Chain

        if(this.size == 0) {
            this.head = newNode;
            this.tail = newNode;
            this.size = 1;
        } else {
//            connect new node in the chain..
            newNode.next = this.head;
            this.head.prev = newNode;

//            update the summary
            this.head = newNode;
            this.size +=1;
        }
    }

    public void addAt(int index, int data) throws Exception {

        if(index == 0) {
            this.addFirst(data);
            return;
        }
        if(index == this.size) {
            this.addLast(data);
            return;
        }

        if(index < 0 || index >= this.size ) {
            throw new Exception("Index out of bound (m)" + "Index: "
                    + index + " , Size: " + this.size);
        }


//        Create new node

        Node newNode = new Node(data);

//        Find previous Node of the index
        Node previousNode = this.getNodeAt(index -1);
//        New node next will be the (next)* of the previousNode
        Node nextOfPreviousNode = previousNode.next;

        previousNode.next = newNode;
        newNode.prev = previousNode;
        nextOfPreviousNode.prev = newNode;
        newNode.next = nextOfPreviousNode;

        // 1.       Privious Node's Next should be newly created node
        //        2. newNode your previous would be previousNode
        //        3. nextOfPreviousNode your prev would be new Node.
        //        4. newnode next set

//        update the summary...

        this.size += 1;

    }

}
