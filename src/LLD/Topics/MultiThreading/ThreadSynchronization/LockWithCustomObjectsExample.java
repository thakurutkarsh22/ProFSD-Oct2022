package LLD.Topics.MultiThreading.ThreadSynchronization;

/*
 * Finer-grained locks: two independent counters use two monitors (lock1, lock2).
 * Contrast with SynchronizationDemo: synchronized static methods share one class lock and
 * serialize unrelated work. See block comment at bottom of this file.
 */
public class LockWithCustomObjectsExample {

    private static int counter1 = 0;
    private static int counter2 = 0;

    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();

    public static void main(String[] args) {
        Thread one = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                increment1();
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                increment2();
            }
        });

        one.start();
        two.start();

        try {
            one.join();
            two.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(counter1 + " -- " + counter2);
    }

    private static void increment1() {
        synchronized (lock1) {
            counter1++;
        }
    }

    private static void increment2() {
        synchronized (lock2) {
            counter2++;
        }
    }
}

/**
 * Why this approach is good
 *
 * Problem with one class-wide lock (e.g. two synchronized static methods on the same class):
 * both methods use the same monitor (the Class object). While one thread runs increment1, another
 * thread cannot enter increment2 even though counter1 and counter2 are unrelated—you pay extra
 * contention and lose parallelism.
 *
 * What this example does:
 * - counter1++ is guarded only by lock1; counter2++ only by lock2.
 * - One thread can hold lock1 while another holds lock2 at the same time, so updates stay
 *   thread-safe without serializing two independent operations on a single lock.
 *
 * Why dedicated Object instances:
 * - Intent is obvious: each lock protects one piece of state.
 * - static final ensures one shared monitor per counter for the whole class (never use
 *   synchronized (new Object()) inside the method—that would create a different lock per call and
 *   would not exclude other threads).
 *
 * Caveat: if two counters must always change together as one invariant, you would need one lock
 * (or a defined lock order) across both updates—not two independent locks.
 */
