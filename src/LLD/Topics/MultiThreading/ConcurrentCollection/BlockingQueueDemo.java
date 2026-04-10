package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/*
 * What is a BlockingQueue?
 *
 * A BlockingQueue<E> is a thread-safe Queue designed for producer–consumer style programs.
 * Besides offer/poll (non-blocking or timed), it provides operations that *block* the calling
 * thread until the operation can succeed — instead of failing immediately or returning null.
 *
 * Typical operations (names vary slightly by exact method):
 * - put(e)      — add element; if the queue is full (bounded queue), the producer thread WAITS
 *                 until space appears (another thread takes an item).
 * - take()      — remove head; if the queue is empty, the consumer thread WAITS until someone puts.
 * - offer / poll with timeout — try for a while, then give up (useful to avoid waiting forever).
 *
 * Why "blocking" matters
 * - Producers and consumers run at different speeds. Without blocking, you either lose data,
 *   spin in a busy loop, or implement wait/notify yourself. A blocking queue encodes that
 *   coordination safely.
 * - A bounded queue (e.g. ArrayBlockingQueue(capacity)) gives natural *back-pressure*: fast
 *   producers slow down when the buffer is full instead of growing memory without bound.
 *
 * Common implementations
 * - ArrayBlockingQueue — fixed capacity, fair optional, array-backed.
 * - LinkedBlockingQueue — optionally bounded; linked nodes.
 * - PriorityBlockingQueue — unbounded priority queue.
 * - SynchronousQueue — zero capacity handoff (each put waits for a take).
 *
 * Text diagram (producer / consumer / bounded buffer)
 *
 *   Producer thread(s)          BlockingQueue (e.g. cap = 10)          Consumer thread(s)
 *         |                              |                                    |
 *         |  put(item)                   |   [ item | item | ... | free ]   |  take()
 *         +--------------------------->|------------------------------------+-->
 *                                        |
 *   Queue FULL  ------------------------>|  put() blocks until take() frees a slot
 *                                        |
 *   Queue EMPTY                          |<---------------- take() blocks until put() adds
 *
 *   Flow:
 *   - Fast producer, slow consumer → queue fills → put() blocks (producer waits).
 *   - Slow producer, fast consumer → queue drains → take() blocks (consumer waits).
 *
 * Contrast with a normal Queue + synchronized block
 * - You could use wait/notify around a LinkedList; BlockingQueue is a higher-level, reviewed
 *   building block used heavily in java.util.concurrent (ExecutorService work queues, etc.).
 *
 * Demo below: one producer (20 tasks), two consumers in infinite loops. put() blocks when the
 * queue is full; take() blocks when empty. After the producer stops, consumers still wait on
 * take() — stop the JVM manually or extend with a poison-pill / interrupt shutdown for a clean exit.
 */
public class BlockingQueueDemo {
    static final int QUEUE_CAPACITY = 10;
    static BlockingQueue<Integer> taskQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    public static void main(String[] args) {
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 20; i++) {
                    taskQueue.put(i);
                    System.out.println("Task produced : " + i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread consumerOne = new Thread(() -> {
            try {
                while (true) {
                    int task = taskQueue.take();
                    processTask(task, "ConsumerOne");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread consumerTwo = new Thread(() -> {
            try {
                while (true) {
                    int task = taskQueue.take();
                    processTask(task, "ConsumerTwo");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        producer.start();
        consumerOne.start();
        consumerTwo.start();
    }

    private static void processTask(int task, String consumerName) throws InterruptedException {
        System.out.println("Task being processed by " + consumerName + " : " + task);
        Thread.sleep(1000);
        System.out.println("Task consumed by " + consumerName + " : " + task);
    }
}

/**
 * Task produced : 1
 * Task being processed by ConsumerOne : 1
 * Task produced : 2
 * Task being processed by ConsumerTwo : 2
 * Task produced : 3
 * Task produced : 4
 * Task produced : 5
 * Task produced : 6
 * Task produced : 7
 * Task produced : 8
 * Task produced : 9
 * Task produced : 10
 * Task consumed by ConsumerOne : 1
 * Task being processed by ConsumerOne : 3
 * Task produced : 11
 * Task consumed by ConsumerTwo : 2
 * Task being processed by ConsumerTwo : 4
 * Task produced : 12
 * Task produced : 13
 * Task produced : 14
 * Task consumed by ConsumerOne : 3
 * Task being processed by ConsumerOne : 5
 * Task produced : 15
 * Task consumed by ConsumerTwo : 4
 * Task being processed by ConsumerTwo : 6
 * Task produced : 16
 * Task consumed by ConsumerOne : 5
 * Task being processed by ConsumerOne : 7
 * Task produced : 17
 * Task consumed by ConsumerTwo : 6
 * Task being processed by ConsumerTwo : 8
 * Task produced : 18
 * Task consumed by ConsumerOne : 7
 * Task being processed by ConsumerOne : 9
 * Task produced : 19
 * Task consumed by ConsumerTwo : 8
 * Task being processed by ConsumerTwo : 10
 * Task produced : 20
 * Task consumed by ConsumerOne : 9
 * Task being processed by ConsumerOne : 11
 * Task consumed by ConsumerTwo : 10
 * Task being processed by ConsumerTwo : 12
 * Task consumed by ConsumerOne : 11
 * Task being processed by ConsumerOne : 13
 * Task consumed by ConsumerTwo : 12
 * Task being processed by ConsumerTwo : 14
 * Task consumed by ConsumerOne : 13
 * Task being processed by ConsumerOne : 15
 * Task consumed by ConsumerTwo : 14
 * Task being processed by ConsumerTwo : 16
 * Task consumed by ConsumerOne : 15
 * Task being processed by ConsumerOne : 17
 * Task consumed by ConsumerTwo : 16
 * Task being processed by ConsumerTwo : 18
 * Task consumed by ConsumerOne : 17
 * Task being processed by ConsumerOne : 19
 * Task consumed by ConsumerTwo : 18
 * Task being processed by ConsumerTwo : 20
 * Task consumed by ConsumerOne : 19
 * Task consumed by ConsumerTwo : 20
 */
