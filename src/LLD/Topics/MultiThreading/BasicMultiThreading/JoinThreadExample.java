package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                          Thread.join()                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is join()?
 * ============================================================================
 *
 * thread.join() makes the CALLING thread wait until the TARGET thread finishes
 * its run() method. Think of it as: "finish your work first, then I continue."
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   Main thread           Thread one             Thread two
 *   ───────────           ──────────             ──────────
 *   one.start()  ---------> runs                 
 *   two.start()  --------------------------------> runs
 *   one.join()            │                       │
 *     │                   │ printing 0-4          │ printing 0-24
 *     │ BLOCKED           │                       │
 *     │ waiting...        │                       │
 *     │                   ▼ (done)                │ (still running!)
 *     │ UNBLOCKED                                 │
 *     ▼                                           │
 *   prints "Done"                                 │ still going...
 *                                                 ▼ (finishes later)
 *
 *   Key: main only waited for thread ONE. Thread two may still be running
 *   when "Done" prints, because we never called two.join().
 *
 * ============================================================================
 * 3. Timeline
 * ============================================================================
 *
 *   Time    Main             Thread 1          Thread 2
 *   ─────   ──────────       ──────────        ──────────
 *   0ms     starts both      printing 0-4      printing 0-24
 *   0ms     one.join()       │                 │
 *           BLOCKED          │                 │
 *   ~Xms   UNBLOCKED        done              still printing
 *           prints "Done"                      │
 *   ~Yms   main exits                         finally done
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Wait for a background computation to finish before using its result.
 * - Coordinate startup: main starts worker threads, joins all, then proceeds.
 * - Simple fork-join: fork N threads, join all, merge results.
 * - Graceful shutdown: join daemon-like worker threads to ensure cleanup.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class JoinThreadExample {
    public static void main(String[] args) throws InterruptedException {
        Thread one = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("Thread 1: " + i);
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 25; i++) {
                System.out.println("Thread 2: " + i);
            }
        });

        one.start();
        two.start();

        // Main waits only for thread one; thread two may still be running when "Done" prints.
        one.join();

        System.out.println("Done executing the threads!");
    }
}

/*
 * Sample output (interleaved; thread 2 continues after "Done" because we only join thread one):
 *
 * Thread 2: 0
 * Thread 1: 0
 * Thread 2: 1
 * Thread 1: 1
 * Thread 2: 2
 * Thread 1: 2
 * Thread 2: 3
 * Thread 1: 3
 * Thread 2: 4
 * Thread 2: 5
 * Thread 2: 6
 * Thread 2: 7
 * Thread 2: 8
 * Thread 2: 9
 * Thread 2: 10
 * Thread 1: 4
 * Thread 2: 11
 * Thread 2: 12
 * Thread 2: 13
 * Thread 2: 14
 * Thread 2: 15
 * Thread 2: 16
 * Thread 2: 17
 * Done executing the threads!
 * Thread 2: 18
 * Thread 2: 19
 * Thread 2: 20
 * Thread 2: 21
 * Thread 2: 22
 * Thread 2: 23
 * Thread 2: 24
 */
