package LiveClass.LinkedList;


//Add, Get, Delete
public class LinkedList {

    Node head;
    Node tail;
    int size;

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

    public int size() {
        return this.size;
    }

    public int getFirst() throws Exception {

        Node firstNode =  this.head;
        if(firstNode != null) {
            return firstNode.val;
        } else {
            throw new Exception("Size of LL is 0 right now");
        }

    }

    public int getLast() throws Exception {
        if(this.size == 0) {
            throw new Exception("Size of LL is 0 right now");
        }
        return this.tail.val;
    }

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
            throw new Exception("Index out of bound" + "Index: "
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

    public int deleteAt(int index) throws  Exception {
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

}
