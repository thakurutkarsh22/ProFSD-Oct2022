package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.CountDownLatch;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                          CountDownLatch                                ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is CountDownLatch?
 * ============================================================================
 *
 * A one-shot synchronization aid: one or more threads wait (await) until a
 * set of operations in other threads completes (countDown reaches 0).
 *
 *   new CountDownLatch(N)  -> internal count starts at N
 *   countDown()            -> decrements count by 1 (thread-safe, never blocks)
 *   await()                -> blocks the caller until count reaches 0
 *
 * Once the count reaches 0 it stays at 0 -- the latch CANNOT be reused.
 *
 * ============================================================================
 * 2. Restaurant analogy
 * ============================================================================
 *
 *   Kitchen Manager (main thread)  = the one who calls await()
 *   Chefs (worker threads)         = the ones who call countDown()
 *   Latch count                    = number of dishes that must be ready
 *
 * The manager says "I won't serve until ALL 3 dishes are done."
 * Each chef finishes a dish -> calls countDown().
 * When the 3rd chef finishes -> count hits 0 -> manager wakes up -> "All ready!"
 *
 * ============================================================================
 * 3. Diagram -- how CountDownLatch works
 * ============================================================================
 *
 *   ┌──────────┐     ┌──────────┐     ┌──────────┐
 *   │  Chef A  │     │  Chef B  │     │  Chef C  │
 *   │ (Pizza)  │     │ (Pasta)  │     │ (Salad)  │
 *   └────┬─────┘     └────┬─────┘     └────┬─────┘
 *        │                │                │
 *     preparing        preparing        preparing
 *     (~2 sec)         (~2 sec)         (~2 sec)
 *        │                │                │
 *        ▼                │                │
 *   countDown()           │                │          Latch count: 3 -> 2
 *        │                ▼                │
 *        │           countDown()           │          Latch count: 2 -> 1
 *        │                │                ▼
 *        │                │           countDown()     Latch count: 1 -> 0  *
 *        │                │                │
 *   ─────┴────────────────┴────────────────┴──────────────────────────────
 *                                                     │
 *                                                     ▼
 *                              ┌────────────────────────────────────────┐
 *                              │     Main thread (Kitchen Manager)      │
 *                              │                                        │
 *                              │  latch.await()  <-- BLOCKED while > 0  │
 *                              │       ...                              │
 *                              │  count hits 0 -> UNBLOCKED *           │
 *                              │                                        │
 *                              │  "All the dishes are ready !!"         │
 *                              └────────────────────────────────────────┘
 *
 * ============================================================================
 * 4. Timeline for this program
 * ============================================================================
 *
 *   Time     Chef A          Chef B          Chef C          Main (Manager)    Count
 *   ─────    ──────────      ──────────      ──────────      ──────────────    ─────
 *   0.0s     preparing       preparing       preparing       await() BLOCKED     3
 *            Pizza           Pasta           Salad
 *   ~2.0s    finished!       finished!       finished!       still blocked       3
 *            countDown()                                                       -> 2
 *                            countDown()                                       -> 1
 *                                            countDown()                       -> 0 *
 *                                                            UNBLOCKED!
 *                                                            prints "All ready"
 *
 * ============================================================================
 * 5. Step-by-step flow of THIS program
 * ============================================================================
 *
 * 1. numberOfChefs = 3 -> latch count starts at 3.
 * 2. Three Thread objects each run a Chef: they print "preparing", sleep 2s,
 *    print "finished", then latch.countDown() exactly once.
 * 3. The main thread reaches latch.await() immediately after starting the three threads.
 *    - await() means: "pause here until the internal count is 0."
 *    - Until all three chefs have called countDown(), main stays blocked.
 * 4. After the third countDown(), count is 0 -> main's await() returns
 *    -> prints "All the dishes are ready !!"
 *
 * ============================================================================
 * 6. latch.await() vs Thread.join()
 * ============================================================================
 *
 *   Feature           Thread.join()                 CountDownLatch.await()
 *   ───────────────   ──────────────────────────    ──────────────────────────────
 *   Waits for         A specific thread to DIE      The count to reach 0
 *   Tied to           Thread lifecycle               Explicit countDown() signals
 *   Multiple waiters  No (one join per thread)       Yes (many threads can await)
 *   Signal from       Thread termination only        Anywhere: mid-run, callback,
 *                                                    pool worker, different class
 *   Reusable          No                             No (one-shot)
 *
 *   join():  "wait for THIS thread to finish."
 *   await(): "wait for N events to happen (I don't care which threads)."
 *
 *   join():
 *     thread1.join();   <-- waits for thread1 to die
 *     thread2.join();   <-- then waits for thread2 to die (sequential waits)
 *
 *   await():
 *     latch.await();    <-- waits for count -> 0 (any thread can countDown())
 *                          all signals aggregated into one wait call
 *
 * ============================================================================
 * 7. CountDownLatch vs CyclicBarrier (interview contrast)
 * ============================================================================
 *
 *   Feature               CountDownLatch               CyclicBarrier
 *   ───────────────────   ─────────────────────────    ─────────────────────────
 *   Reusable?             NO -- one-shot               YES -- resets after trip
 *   Who waits?            One or more watchers          All N participants
 *   Who counts?           Any thread (countDown)        Same threads that wait
 *   Barrier action?       No                            Yes (runs on last arrival)
 *   Use case              "I wait for N events"         "We all wait for each
 *                                                        other, then go together"
 *
 * ============================================================================
 * 8. Gotchas
 * ============================================================================
 *
 * - If a chef never calls countDown() (bug/crash), await() blocks forever.
 *   Use await(timeout, TimeUnit) in production.
 * - countDown() below zero is a no-op (count stays 0, no exception).
 * - CountDownLatch is one-shot; for reusable barriers, use CyclicBarrier.
 *
 * ============================================================================
 * 9. Practical uses (one-liners)
 * ============================================================================
 *
 * - App startup: main waits for all microservice connections to be ready before serving traffic.
 * - Test harness: start N threads simultaneously (all wait on a latch, then release at once).
 * - Batch processing: wait for all file-parsing threads to finish before merging results.
 * - Health checks: wait for all subsystem probes to report back before declaring "healthy."
 *
 * ============================================================================
 * 10. Interview one-liner
 * ============================================================================
 * CountDownLatch: one or more threads block on await() until N other threads
 * call countDown(), reducing the count to zero. One-shot, not reusable.
 *
 * ============================================================================
 * 11. Code demo below
 * ============================================================================
 */
public class CountDownLatchRestraunt {

    public static void main(String[] args) throws InterruptedException {
        int numberOfChefs = 3;
        CountDownLatch latch = new CountDownLatch(numberOfChefs);

        Thread one = new Thread(new Chef("chef a", "Pizza", latch));
        Thread two = new Thread(new Chef("chef b", "Pasta", latch));
        Thread three = new Thread(new Chef("chef c", "Salad", latch));

        one.start();
        two.start();
        three.start();

        // Block until all chefs have called countDown() (count 3 -> 0). Then continue.
        latch.await();

        System.out.println("All the dishes are ready !!");
    }
}

/**
 * Each chef prepares one dish on its own thread, then signals the latch once when done.
 * The shared {@link CountDownLatch} is the same instance for every chef so the count drops globally.
 */
class Chef implements Runnable {

    private final String name;
    private final String dish;
    private final CountDownLatch latch;

    public Chef(String name, String dish, CountDownLatch latch) {
        this.name = name;
        this.dish = dish;
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            System.out.println(name + " is preparing " + dish);
            Thread.sleep(2000);
            System.out.println(name + " has finished preparing " + dish);
            latch.countDown();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

// all in all this is dynamic join
// when we have dynamic multiple threads and we want to wait for all of them to complete
// countdown latch do not reset the count once it is done
