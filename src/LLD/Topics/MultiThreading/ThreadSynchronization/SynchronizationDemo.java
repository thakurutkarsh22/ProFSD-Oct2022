package LLD.Topics.MultiThreading.ThreadSynchronization;

public class SynchronizationDemo {

    private static int counter1 = 0;
    private static int counter2 = 0;
    public static void main(String[] args) throws InterruptedException {
        Thread one = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
//                counter++;
                increment1();
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
//                counter++;
                increment2();
            }
        });


        one.start();
        two.start();

        one.join();
        two.join();



        System.out.println(counter1 + " counter val -- " + counter2);
        // 13976 counter val
        // 15783 counter val
        // This is happening due to non atomic operation
    }

    // there are still problem with the synchronized keyword
    // using the
    public synchronized static void increment1() {
        counter1++;
    }

    public synchronized static void increment2() {
        counter2++;
    }


}

/**
 *
 * --> when we have only increment1
 *
 * Monitor Locks
 * each object in java is associated with the monitor (Mutual exclusion mechanism in java)
 *
 *
 * How synchronized Keyword work in java.
 * when a thread goes the synchronized block it tries to acquire the monitor locks
 *
 * why method level synchronized is bad
 * 1. it synchronized whole method
 * 2. if a class extends a parent class and override the sync method, the method in the child also need to be explicitly synchronized
 *
 *
 * --> when we have increment1 and increment2
 *
 *
 * once thread1 takes monitor lock of method increment1
 * thread 2 is still blocked to take the lock for increment2
 * this is because for a class there is one lock which is held by thread1
 *
 * --- Expanded explanation (same file: increment1 / increment2 are synchronized static) ---
 *
 * Java does not give each static method its own lock. Every synchronized static method on this
 * class uses the same monitor: the Class object for SynchronizationDemo (SynchronizationDemo.class).
 *
 * Flow:
 * 1) Thread 1 enters increment1() and acquires that class lock.
 * 2) Thread 2 enters increment2() and needs the same class lock.
 * 3) Thread 2 blocks until Thread 1 exits increment1() and releases the lock, even though the
 *    methods update different fields (counter1 vs counter2).
 *
 * Contrast:
 * - synchronized instance methods lock on "this" (one lock per object). Two threads can run
 *   synchronized instance methods on different instances at the same time.
 * - synchronized static methods lock on the class. One lock shared by all static synchronized
 *   methods on that class.
 *
 * Practical note: Here both counters could use separate locks (e.g. two private static final Object
 * monitors with synchronized blocks) for finer granularity; the single class lock serializes
 * increment1 and increment2 more than is strictly required for independent counters.
 *
 */
