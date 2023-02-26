package LiveClass.Queues;

import LiveClass.LinkedList.LinkedList;

public class QueuesUsingLL {
    private LinkedList linkedListForQueues;

    QueuesUsingLL() {
        linkedListForQueues = new LinkedList();
    }

    public int add(int item) {
        this.linkedListForQueues.addLast(item);
        return item;
    }

    public int remove() throws Exception {
        if(this.linkedListForQueues.size() == 0) {
            throw new Exception("No such element m");
        }

        return this.linkedListForQueues.removeFirst();
    }


    public int peek() throws Exception {

        if(this.linkedListForQueues.size() == 0) {
            return -1;
        }
        return this.linkedListForQueues.getAt(0);
    }

    @Override
    public String toString() {
        return this.linkedListForQueues.displayInArr();
    }

}
