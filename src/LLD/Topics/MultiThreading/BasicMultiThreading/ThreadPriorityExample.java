package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                        Thread Priority                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is thread priority?
 * ============================================================================
 *
 * Each Java thread has a priority (int from 1 to 10). The thread scheduler
 * uses priority as a HINT to decide which runnable thread to schedule next.
 * Higher-priority threads are more likely to get CPU time, but this is NOT
 * a guarantee -- it is OS and JVM dependent.
 *
 * ============================================================================
 * 2. Priority values
 * ============================================================================
 *
 *   ┌────────────────────────────────────────────────────────┐
 *   │  Thread.MIN_PRIORITY   = 1   (lowest)                 │
 *   │  Thread.NORM_PRIORITY  = 5   (default for new threads)│
 *   │  Thread.MAX_PRIORITY   = 10  (highest)                │
 *   └────────────────────────────────────────────────────────┘
 *
 *   Priority scale:
 *   1 ──── 2 ──── 3 ──── 4 ──── 5 ──── 6 ──── 7 ──── 8 ──── 9 ──── 10
 *   MIN                         NORM                                 MAX
 *   ◄── less likely to run                    more likely to run ──►
 *
 * ============================================================================
 * 3. How the scheduler works -- diagram
 * ============================================================================
 *
 *   Thread Scheduler (OS/JVM)
 *   ┌────────────────────────────────────────────────────────────┐
 *   │                                                            │
 *   │  RUNNABLE threads:                                        │
 *   │  ┌──────────────────┐                                     │
 *   │  │ Thread A (pri=10)│ ◄── most likely picked first        │
 *   │  │ Thread B (pri=5) │                                     │
 *   │  │ Thread C (pri=1) │ ◄── least likely, but NOT starved   │
 *   │  └──────────────────┘                                     │
 *   │                                                            │
 *   │  Same priority? Threads are typically scheduled FIFO       │
 *   │  among those waiting at the same level.                    │
 *   │                                                            │
 *   │  WARNING: This is a hint, not a contract.                  │
 *   │  Do NOT rely on priority for correctness.                  │
 *   └────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Give a UI repaint thread higher priority so the app feels responsive.
 * - Lower priority for background indexing/cleanup threads.
 * - Real-time audio/video processing threads may use MAX_PRIORITY.
 * - In practice, most Java code leaves priority at default (5) and uses other mechanisms.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
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
