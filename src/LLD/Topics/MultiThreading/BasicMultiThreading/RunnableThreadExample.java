package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     Runnable (implements Runnable)                      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is Runnable?
 * ============================================================================
 *
 * Runnable is a functional interface with a single method: void run().
 * It represents "the code a thread should execute." You pass it to
 * new Thread(runnable) and call start().
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   ┌──────────────────┐        ┌──────────────────┐
 *   │  ThreadOne        │        │  ThreadTwo        │
 *   │ implements Runnable│        │ implements Runnable│
 *   │  run() { ... }    │        │  run() { ... }    │
 *   └────────┬─────────┘        └────────┬─────────┘
 *            │                           │
 *            ▼                           ▼
 *   new Thread(threadOne)        new Thread(threadTwo)
 *            │                           │
 *            ▼                           ▼
 *        one.start()                 two.start()
 *            │                           │
 *            ▼                           ▼
 *   ┌─── OS Thread 1 ───┐     ┌─── OS Thread 2 ───┐     ┌─── Main Thread ──┐
 *   │ run(): prints 0-4  │     │ run(): prints 0-4  │     │ continues or ends│
 *   └────────────────────┘     └────────────────────┘     └──────────────────┘
 *         ◄─── all three run concurrently (interleaved output) ───►
 *
 * ============================================================================
 * 3. Why use Runnable?
 * ============================================================================
 *
 * 1) Separation of concerns -- "what to run" (Runnable) is separate from
 *    thread machinery (Thread class).
 * 2) Single inheritance -- a class can extend only one superclass. If your
 *    class already extends something else, it cannot extend Thread but CAN
 *    implement Runnable.
 * 3) Reuse across APIs -- same Runnable works with Thread, ExecutorService,
 *    ScheduledExecutorService, etc.
 * 4) Lambdas -- Runnable is a @FunctionalInterface, so you can write:
 *    new Thread(() -> { ... }).start();
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Background file I/O: new Thread(() -> writeLogToDisk()).start();
 * - Offloading heavy computation so the UI thread stays responsive (Android/Swing).
 * - Submitting tasks to an ExecutorService: executor.execute(myRunnable);
 * - Timer/scheduler callbacks: scheduledExecutor.schedule(myRunnable, 5, SECONDS);
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class RunnableThreadExample {
    public static void main(String[] args) {
        Thread one = new Thread(new ThreadOne());
        Thread two = new Thread(new ThreadTwo());

        Thread three = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Thread three: " + i);
                }
            }
        });

        one.start();
        two.start();
        three.start();
    }
}


class ThreadOne implements Runnable {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread One: " + i);
        }
    }
}

class ThreadTwo implements Runnable {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread Two: " + i);
        }
    }
}
