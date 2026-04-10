package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.CountDownLatch;

/*
 * CountDownLatch — what this file is about
 *
 * Problem (restaurant)
 * - Several chefs each prepare one dish in parallel.
 * - The kitchen manager must not announce “everything is ready” until every dish is done.
 * - CountDownLatch models “N things must complete before we continue.”
 *
 * Mechanics
 * - new CountDownLatch(N) starts with count N.
 * - Each completing party calls countDown() once → count decreases.
 * - Any thread that calls await() blocks until the count reaches 0, then wakes up (and await() returns).
 * - The latch does not reset; it is one-shot for this “round” of work.
 *
 * How THIS program runs
 * 1. numberOfChefs = 3 → latch count starts at 3.
 * 2. Three Thread objects each run a Chef: they print “preparing”, sleep 2s, print “finished”, then
 *    latch.countDown() exactly once.
 * 3. The main thread reaches latch.await() immediately after starting the three threads.
 *    - await() means: “pause here until the internal count is 0.”
 *    - Until all three chefs have called countDown(), main stays blocked and does not print the final line.
 * 4. After the third countDown(), count is 0 → main’s await() returns → prints that all dishes are ready.
 *
 * Role of await() on main
 * - Main plays the “manager”: it must not run the code after await() until every chef has signaled
 *   completion via countDown(). Without await(), main could print “all ready” before chefs finish.
 *
 * latch.await() vs Thread.join() (see BasicMultiThreading/JoinThreadExample)
 * - join(thread): you wait until that specific Thread’s run() finishes and the thread terminates.
 *   It is tied to the lifetime of a Thread object you hold. Example: one.join(); two.join();
 * - latch.await(): you wait until the latch count reaches zero because some code called countDown()
 *   that many times. It is tied to explicit signals, not strictly to “thread died.”
 *   In this file, countDown() happens at the end of Chef.run(), so it looks similar to join — but
 *   you could countDown() in the middle of run(), or from a pool worker, callback, or after several
 *   steps, and await() would still be the right “all N events happened” gate.
 * - One latch can unblock many waiters at once; join is always “wait for this one thread.”
 * - join throws InterruptedException; await() also throws InterruptedException (and has timed overload).
 *
 * Gotchas
 * - If a chef never countDown() (bug or early exit), await() waits forever unless you use await(timeout, unit).
 * - countDown() is cheap and thread-safe; do not confuse with wait() on Object (different API).
 *
 * Contrast (interviews)
 * - CountDownLatch: wait for N events, one-shot.
 * - CyclicBarrier: same parties rendezvous and can reuse the barrier for another round.
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

        // Block until all chefs have called countDown() (count 3 → 0). Then continue.
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
