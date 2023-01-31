package LiveClass.LinkedList;


//Add, Get, Delete
public class LinkedList {

    private Node head;
    private Node tail;
    private int size;

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

    LinkedList() {

    }

    public void display() {
        Node temp = this.head;

        while(temp != null) {
            System.out.print(temp.val+ " -> ");
            temp = temp.next;
        }

        System.out.println("null");
    }

//    TC => O(n)

    public int size() {
        return this.size;
    }

//    TC => O(1)

    public int getFirst() throws Exception {

        Node firstNode =  this.head;
        if(firstNode != null) {
            return firstNode.val;
        } else {
            throw new Exception("Size of LL is 0 right now");
        }

    }

//    TC => O(1)

    public int getLast() throws Exception {
        if(this.size == 0) {
            throw new Exception("Size of LL is 0 right now");
        }
        return this.tail.val;
    }

    //    TC => O(1)

    public int getAt(int index) throws Exception {
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

        return temp.val;

    }

//    TC => O(n)

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

    //    TC => O(n)


    public void addLast(int data) {
//        Create the node and fill the data
        Node newNode = new Node(data);

//        attach to the Chain


        if(this.size == 0) {
            this.head = newNode;
            this.tail = newNode;
            this.size = 1;
        } else {
            // Attach node to chain

            Node tailNode = this.tail;
            tailNode.next = newNode;

//            change the tail Pointer
            this.tail = newNode;

//          change the size
            this.size += 1;
        }
    }

//    TC => O(1)

    public void addFirst(int data) {
        //        Create the node and fill the data
        Node newNode = new Node(data);

//        attach to the Chain

        if(this.size == 0) {
            this.head = newNode;
            this.tail = newNode;
            this.size = 1;
        } else {
            newNode.next = this.head;
            this.head = newNode;
            this.size +=1;
        }
    }

    //    TC => O(1)

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
        newNode.next = nextOfPreviousNode;

//        Privious Node's Next should be newly created node
        previousNode.next = newNode;
        this.size += 1;

    }

//    TC => O(n)


    public int removeFirst() throws Exception {
        if(this.size == 0) {
            throw new Exception("List is empty");
        }

        int removedValue = this.head.val;


        if (this.size == 0) {
            this.head = this.tail = null;
        } else {
            this.head = this.head.next;
        }

        this.size -= 1;

        return  removedValue;

    }
//    TC => O(1)


    public int removeLast() throws Exception {
        if(this.size == 0) {
            throw new Exception("List is empty");
        }

        int removedValue = this.head.val;

        if (this.size == 0) {
            this.head = this.tail = null;
        } else {
            Node nodeBeforeTail = this.getNodeAt(this.size -2);
            nodeBeforeTail.next = null;
            this.tail = nodeBeforeTail;
        }

        this.size -= 1;

        return  removedValue;

    }

    //    TC => O(n)

    public int removeAt(int index) throws  Exception {
        if(this.size == 0) {
            throw new Exception("List is empty");
        }


        if(index < 0 || index >= this.size ) {
            throw new Exception("Index out of bound" + "Index: "
                    + index + " , Size: " + this.size);
        }

        int removedValue = this.head.val;

        if(index == 0) {
            return this.removeFirst();
        } else if(index == this.size -1) {
            return  this.removeLast();
        } else {
            Node nodeBeforeIndexNode = this.getNodeAt(index - 1);
            Node afterIndexNode = this.getNodeAt(index + 1);
            nodeBeforeIndexNode.next = afterIndexNode;
        }

//        update the LL summary
        this.size--;


        return removedValue;
    }

    //    TC => O(n)



    /*
        Question: Reverse the Linked List (Data Index Method)
        Input: 1 -> 3 -> 6 -> 18 -> 20
        Output: 20 18 6 3 1
        Explanation: hey last tail should be the first and the head should be at the tail.
     */

    public void reverseLinkedListDataIndex() throws Exception {
        int left = 0;
        int right = this.size() - 1;

        while(left < right) {
            Node leftNode = this.getNodeAt(left);
            Node rightNode = this.getNodeAt(right);

            int temp = rightNode.val;
            rightNode.val = leftNode.val;
            leftNode.val = temp;


            left++;
            right--;
        }
    }

//    TC => O(n^2)
//    SC => O(1)


    /*
        Question: Reverse the Linked List (Pointer Index Method)
        Input: 1 -> 3 -> 6 -> 18 -> 20
        Output: 20 18 6 3 1
        Explanation: hey last tail should be the first and the head should be at the tail.

     */

    public void reverseLinkedListPointerIndex() {
        Node previousNode = this.head;
        Node currentNode = this.head.next;
        Node nextNode;

        while (currentNode != null) {
//            we have address of the chain that is going to be detached
            nextNode = currentNode.next;

//            detach the chain
            currentNode.next = previousNode;

//            Move the pointers
            previousNode = currentNode;
            currentNode = nextNode;

        }

//        make the head next null;
        this.head.next = null;

//        change the summary
        Node temp = this.head;
        this.head = this.tail;
        this.tail = temp;
    }

//    TC => O(n)
//    SC => O(1)

}
