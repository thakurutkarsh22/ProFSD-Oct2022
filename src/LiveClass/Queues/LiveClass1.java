package LiveClass.Queues;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

public class LiveClass1 {
    public static void main(String[] args) throws Exception {
        // child            // parent
//        Queue<Integer> queue = new LinkedList<>();
//
//        System.out.println(queue.remove());
//        ----------- add the value inside the queue (enqueue)-------
//        queue.add(1);
//        queue.add(2);
//        queue.add(3);

//        System.out.println(queue);


//        ---------- delete the value from the queue (dequeue) ----------
//        queue.remove();
//
//        System.out.println(queue);

//    ------- get the first value form the queue ---------
//        System.out.println(queue.peek());




        QueuesUsingLL queue = new QueuesUsingLL();
        queue.add(1);
        queue.add(2);
        queue.add(3);

        System.out.println(queue);

        System.out.println(queue.peek());

        System.out.println(queue.remove() + " the removed val");

        System.out.println(queue + " after removal");

        System.out.println(queue.peek());

    }
}
