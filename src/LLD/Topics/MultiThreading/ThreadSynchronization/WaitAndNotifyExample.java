package LLD.Topics.MultiThreading.ThreadSynchronization;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     wait(), notify(), notifyAll()                       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What are wait/notify?
 * ============================================================================
 *
 * Methods on java.lang.Object that allow threads to communicate:
 * - wait()      : release the lock and sleep until notified.
 * - notify()    : wake ONE waiting thread (arbitrary choice).
 * - notifyAll() : wake ALL waiting threads.
 *
 * RULE: Must be called inside synchronized(lock) { ... } on the SAME lock object.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   Thread one (method one)             Thread two (method two)
 *   ────────────────────────            ────────────────────────
 *   synchronized(lock)                  (waiting for lock...)
 *   │  prints "hello one"
 *   │  lock.wait()  ──────────────►  RELEASES lock
 *   │  (sleeping in wait set)          synchronized(lock)  ◄── acquired!
 *   │                                  │  prints "hello two"
 *   │                                  │  lock.notifyAll()  ──► wakes thread one
 *   │                                  │  prints "after notifying"
 *   │                                  release lock
 *   │  ◄── re-acquires lock
 *   │  prints "back again in one"
 *   release lock
 *
 *   Key flow:
 *   ┌─────────┐    wait()     ┌────────────┐   notifyAll()   ┌─────────┐
 *   │ Thread 1 │──────────►   │  WAIT SET   │ ◄──────────── │ Thread 2 │
 *   │ has lock │  releases    │  (parked)   │   wakes up     │ has lock │
 *   └─────────┘   lock       └──────┬─────┘               └─────────┘
 *                                     │
 *                                     ▼ re-acquire lock, then continue
 *
 * ============================================================================
 * 3. notify() vs notifyAll()
 * ============================================================================
 *
 *   notify()     : wakes ONE arbitrary waiter. Risk: might wake the "wrong"
 *                  thread if multiple threads wait on the same lock for
 *                  different conditions.
 *   notifyAll()  : wakes ALL waiters. Each must re-acquire the lock and
 *                  re-check its condition. Safer for most use cases.
 *
 *   Best practice pattern:
 *     synchronized (lock) {
 *         while (!conditionHolds()) { lock.wait(); }
 *         // proceed
 *     }
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Producer-consumer: producer notifies consumer when data is ready.
 * - Thread pool: idle workers wait(); dispatcher notifies when task arrives.
 * - Barrier/gate: threads wait() until a condition is met, then notifyAll() releases all.
 * - Legacy code before java.util.concurrent (Condition, BlockingQueue replaced most uses).
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class WaitAndNotifyExample {
    private static final Object lock = new Object();
    public static void main(String[] args) {
        Thread one = new Thread(() -> {
            try {
                one();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread two = new Thread(() -> {
            try {
                two();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        one.start();
        two.start();
    }

    public static void one() throws InterruptedException {
        synchronized (lock) {
            System.out.println("hello from method one ...");
            lock.wait();
            System.out.println("back again in the method one");
        }
    }

    public static void two() throws InterruptedException {
        synchronized (lock) {
            System.out.println("hello from method two ...");

            // use notify all instead of notify
            lock.notifyAll();
            System.out.println("hello from method two after notifying");
        }
    }
}

/**
 * Sample output (happy path: thread one reaches wait() before thread two):
 *
 * hello from method one ...
 * hello from method two ...
 * hello from method two after notifying
 * back again in the method one
 *
 * Ordering caveat: if thread two runs first and calls notifyAll() before
 * thread one has called wait(), the signal is lost and thread one waits forever.
 */
