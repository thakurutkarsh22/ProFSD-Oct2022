package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * Thread priority
 *
 * Thread scheduler
 * - If many threads are in the RUNNABLE state but only one CPU can run user code at a time, only
 *   one thread executes while others wait. The part of the JVM/OS that picks which runnable thread
 *   runs next is the thread scheduler (exact policy is platform-dependent).
 *
 * Priority-based execution
 * - Each thread has a priority. Under typical circumstances, higher-priority runnable threads are
 *   more likely to be scheduled ahead of lower-priority ones. This is a hint, not a real-time
 *   guarantee—do not rely on priority for correctness.
 *
 * Priority values (Java API)
 * - Integers from Thread.MIN_PRIORITY (1) through Thread.MAX_PRIORITY (10).
 * - Default for a new thread is usually Thread.NORM_PRIORITY (5), often inherited from the thread
 *   that created it.
 *
 * Same priority
 * - Threads with the same priority are often scheduled in FIFO order among those waiting; the
 *   scheduler may use queues per priority level (again, OS/JVM specific).
 */
public class ThreadPriorityExample {
    public static void main(String[] args) {
//        System.out.println(Thread.currentThread().getName());
//        System.out.println(Thread.currentThread().getPriority());
//        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
//        System.out.println(Thread.currentThread().getPriority());

        System.out.println(Thread.currentThread().getName() + " says Hi");

        Thread one = new Thread(() -> {
            System.out.println("Thread one says Hi!");
        });

        one.setPriority(Thread.MAX_PRIORITY);
        one.start();
    }
}

/*
 * Sample output:
 * main
 * 5
 * 10
 */

/**
 * main says Hi
 * Thread one says Hi!
 */
