package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     extends Thread                                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is "extends Thread"?
 * ============================================================================
 *
 * Instead of implementing Runnable, your class directly extends java.lang.Thread
 * and overrides run(). You then call start() on the instance itself.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   ┌──────────────────┐        ┌──────────────────┐
 *   │  Thread1          │        │  Thread2          │
 *   │  extends Thread   │        │  extends Thread   │
 *   │  run() { ... }    │        │  run() { ... }    │
 *   └────────┬─────────┘        └────────┬─────────┘
 *            │                           │
 *        one.start()                 two.start()
 *            │                           │
 *   ┌─── OS Thread 1 ───┐     ┌─── OS Thread 2 ───┐     ┌─── Main Thread ──────┐
 *   │ run(): prints 0-4  │     │ run(): prints 0-4  │     │ prints "Done" + loop │
 *   └────────────────────┘     └────────────────────┘     └──────────────────────┘
 *         ◄─── all three run concurrently (interleaved output) ───►
 *
 *   Note: main prints "Done executing" right after start() -- it does NOT wait
 *   for Thread1/Thread2 to finish (no join() here).
 *
 * ============================================================================
 * 3. Runnable vs extends Thread
 * ============================================================================
 *
 *   Feature               extends Thread             implements Runnable
 *   ───────────────────   ──────────────────────     ──────────────────────
 *   Inheritance           Burns single inheritance   Free to extend another class
 *   Coupling              Tight (IS-A Thread)        Loose (IS-A task)
 *   Reuse                 Only with Thread            Works with Executor, etc.
 *   Simplicity            Direct start()             Needs new Thread(runnable)
 *   Recommendation        Demos / quick tests        Production code
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Quick prototypes/demos where you need a thread in 3 lines.
 * - Custom thread subclasses that override interrupt(), getName(), or carry per-thread state.
 * - In practice, prefer Runnable/Callable + ExecutorService for real applications.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class ExtendsThreadExample {
    public static void main(String[] args) {
        Thread one = new Thread1();
        Thread two = new Thread2();

        one.start();
        two.start();
        System.out.println("Done executing the threads!");

        for (int i = 0; i < 1000; i++) {
            if(i == 999) {
                System.out.println("999");
            }

        }
    }
}

class Thread1 extends Thread {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread one: " + i);
        }
    }
}

class Thread2 extends Thread {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread two: " + i);
        }
    }
}

/**
 * Done executing the threads!
 * Thread one: 0
 * Thread one: 1
 * Thread two: 0
 * Thread one: 2
 * Thread two: 1
 * Thread two: 2
 * Thread two: 3
 * Thread one: 3
 * Thread one: 4
 * Thread two: 4
 */
