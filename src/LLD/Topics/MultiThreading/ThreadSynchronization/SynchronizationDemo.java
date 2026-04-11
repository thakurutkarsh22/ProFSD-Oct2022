package LLD.Topics.MultiThreading.ThreadSynchronization;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     synchronized keyword                               ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is synchronized?
 * ============================================================================
 *
 * The synchronized keyword ensures that only ONE thread at a time can execute
 * a block of code protected by the same monitor (lock). This prevents race
 * conditions on shared mutable state.
 *
 * ============================================================================
 * 2. The problem -- race condition without synchronized
 * ============================================================================
 *
 *   counter++ is NOT atomic. It is actually 3 steps:
 *     1. READ  counter value
 *     2. ADD   1 to the value
 *     3. WRITE new value back
 *
 *   Thread 1                Thread 2
 *   ────────                ────────
 *   READ counter = 5        READ counter = 5   (same value!)
 *   ADD  5 + 1 = 6          ADD  5 + 1 = 6
 *   WRITE counter = 6       WRITE counter = 6  (lost update! should be 7)
 *
 *   Result: two increments, but counter only went from 5 to 6 instead of 7.
 *
 * ============================================================================
 * 3. How synchronized static works -- diagram
 * ============================================================================
 *
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │              SynchronizationDemo.class                         │
 *   │              (THE single class-level lock)                     │
 *   │                                                                │
 *   │   synchronized static increment1()  ──┐                      │
 *   │                                        ├── SAME lock!         │
 *   │   synchronized static increment2()  ──┘                      │
 *   └────────────────────────────────────────────────────────────────┘
 *
 *   Thread 1 (increment1)              Thread 2 (increment2)
 *   ───────────────────────             ───────────────────────
 *   acquire class lock  <-- got it!     acquire class lock <-- BLOCKED!
 *   │  counter1++                           │  waiting...
 *   release class lock                      │  waiting...
 *                                       acquire class lock <-- now got it
 *                                       │  counter2++
 *                                       release class lock
 *
 *   Problem: increment1 and increment2 touch DIFFERENT counters, but they
 *   block each other because they share the SAME class lock. This serializes
 *   independent work unnecessarily.
 *
 *   Fix: use separate lock objects (see LockWithCustomObjectsExample.java).
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Protecting shared counters, caches, or collections from concurrent modification.
 * - Implementing thread-safe singleton (double-checked locking uses synchronized).
 * - Guarding critical sections in legacy code before java.util.concurrent existed.
 * - Any read-modify-write operation on shared state (e.g., balance transfer in banking).
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class SynchronizationDemo {

    private static int counter1 = 0;
    private static int counter2 = 0;
    public static void main(String[] args) throws InterruptedException {
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

        one.join();
        two.join();



        System.out.println(counter1 + " counter val -- " + counter2);
    }

    public synchronized static void increment1() {
        counter1++;
    }

    public synchronized static void increment2() {
        counter2++;
    }


}

/**
 * Expected: 10000 counter val -- 10000 (correct values, but unnecessarily serialized).
 * Without synchronized: values < 10000 due to lost updates (race condition).
 */
