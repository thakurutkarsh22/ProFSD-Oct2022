package LLD.Topics.MultiThreading.ThreadSynchronization;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                  Fine-Grained Locking (Custom Lock Objects)            ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is fine-grained locking?
 * ============================================================================
 *
 * Instead of one big lock for the whole class (synchronized static), you create
 * separate lock objects for independent pieces of state. Threads touching
 * different state can run in PARALLEL because they acquire different locks.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                      │
 *   │   lock1 (Object)          lock2 (Object)                           │
 *   │   guards counter1         guards counter2                           │
 *   │                                                                      │
 *   │   Thread 1                Thread 2                                  │
 *   │   synchronized(lock1)     synchronized(lock2)  <-- DIFFERENT locks! │
 *   │   │  counter1++           │  counter2++                             │
 *   │   release lock1           release lock2                             │
 *   │                                                                      │
 *   │   ◄────── both run in PARALLEL ──────►                              │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 *   Contrast with SynchronizationDemo.java (one class lock):
 *
 *   ┌──────────────────────────────────────┐
 *   │  ONE class lock                      │
 *   │  Thread 1: increment1() -- lock      │
 *   │  Thread 2: increment2() -- BLOCKED!  │  <-- unnecessary serialization
 *   └──────────────────────────────────────┘
 *
 *   vs. this file (two locks):
 *
 *   ┌──────────────────────────────────────┐
 *   │  lock1          lock2                │
 *   │  Thread 1       Thread 2             │
 *   │  increment1()   increment2()         │  <-- both run at the same time!
 *   └──────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Database connection pool: separate lock for "borrow" vs "return" operations.
 * - ConcurrentHashMap uses per-bucket locks (same idea, scaled to N buckets).
 * - Web server: separate locks for session store vs request counter.
 * - Any class with multiple independent mutable fields that are accessed concurrently.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
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
 * Output: 10000 -- 10000
 * Both counters correct AND both threads ran in parallel (faster than single class lock).
 */
