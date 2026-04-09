package LLD.Topics.MultiThreading.ThreadSynchronization;

/*
 * wait/notify demo: see end-of-file comment for behavior, notify vs notifyAll, ordering caveats.
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

            // use nofity all instead of notify
            lock.notifyAll();
            System.out.println("hello from method two after notifying");
        }
    }
}

/**
 * Explanation: wait(), notify(), and notifyAll() on the shared lock
 *
 * Shared lock
 * - wait() and notify() must be called on an object while the calling thread holds that object's
 *   monitor. Here everything uses the same `lock` instance inside synchronized (lock) { ... }.
 *
 * Method one() (thread one)
 * 1) Enters synchronized (lock) and owns the monitor.
 * 2) Prints the first line.
 * 3) lock.wait(): releases the monitor so another thread can enter synchronized (lock), and this
 *    thread waits in the wait set until notify/notifyAll on lock (or interrupt, etc.).
 * 4) When woken, it must re-acquire the monitor before continuing; then prints "back again...".
 *
 * Method two() (thread two)
 * 1) Can enter synchronized (lock) only after thread one has called wait() (which released the lock).
 * 2) Prints from method two.
 * 3) lock.notifyAll(): wakes every thread waiting on lock (here, just thread one). Each awakened
 *    thread must re-acquire the monitor before wait() returns; only one runs inside synchronized
 *    at a time. notify() would wake only one arbitrary waiter—see below.
 * 4) Prints "after notifying", then exits synchronized and releases lock so thread one proceeds.
 *
 * One-line summary: wait = release lock and pause until notified; notify/notifyAll = wake waiter(s),
 * which run only after the notifier releases the lock.
 *
 * notify() vs notifyAll()
 * Both are called on the same monitor used for wait(), while holding that lock.
 * - notify(): wakes a single thread in the wait set for this object. Which thread is unspecified.
 * - notifyAll(): wakes every thread waiting on this object's wait set.
 * After waking, each thread competes to re-acquire the lock; they still enter the synchronized
 * block one at a time.
 *
 * Why notifyAll() is often safer: with multiple waiters or multiple logical conditions guarded by
 * the same lock, notify() might wake the "wrong" waiter if your code does not re-check state in a
 * loop—risk of missed progress. Typical pattern:
 *   synchronized (lock) {
 *       while (!conditionHolds()) { lock.wait(); }
 *       // use shared state
 *   }
 *   // elsewhere:
 *   synchronized (lock) { updateState(); lock.notifyAll(); }
 * Prefer notify() only when you can argue that any single wakeup is always correct (e.g. exactly
 * one registered waiter for that condition).
 *
 * Sample output (happy path: thread one reaches wait() before thread two calls notify/notifyAll()):
 *
 * hello from method one ...
 * hello from method two ...
 * hello from method two after notifying
 * back again in the method one
 *
 * Ordering caveat
 * If thread two runs first and completes two() before thread one has called wait(), notify/notifyAll
 * has no waiter and thread one may wait forever. This is a minimal API demo; production code usually
 * waits in a while loop on a real condition and may use notifyAll() or java.util.concurrent types.
 */
