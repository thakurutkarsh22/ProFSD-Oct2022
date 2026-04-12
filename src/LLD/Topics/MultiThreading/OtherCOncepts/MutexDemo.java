package LLD.Topics.MultiThreading.OtherCOncepts;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                              Mutex                                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a Mutex?
 * ============================================================================
 *
 * Mutex = MUTual EXclusion.
 *
 * A mutex is a locking mechanism that allows ONLY ONE thread to access
 * a shared resource at a time. If a thread holds the mutex, every other
 * thread that tries to acquire it will BLOCK until the holder releases it.
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Mutex = a key to a room                                        │
 *   │                                                                  │
 *   │  Only ONE person (thread) can hold the key at a time.           │
 *   │  Others must wait outside the door until the key is returned.   │
 *   │                                                                  │
 *   │  Thread 1: "I have the key, I'm inside."                        │
 *   │  Thread 2: "Door is locked, I'll wait."                         │
 *   │  Thread 3: "Door is locked, I'll wait."                         │
 *   │                                                                  │
 *   │  Thread 1 finishes, returns the key.                            │
 *   │  Thread 2: "Got the key! I'm inside now."                       │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 2. We have learnt it earlier! (the connection)
 * ============================================================================
 *
 * You already know mutexes -- you've been using them throughout this course!
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  What you've used            │  It IS a mutex?                   │
 *   │──────────────────────────────│───────────────────────────────────│
 *   │  synchronized keyword        │  YES -- intrinsic mutex (monitor)│
 *   │  ReentrantLock               │  YES -- explicit mutex           │
 *   │  Semaphore(1)                │  Almost -- binary semaphore      │
 *   │                              │  (acts like mutex but no owner)  │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   "Mutex" is the CONCEPT. synchronized and ReentrantLock are Java's
 *   IMPLEMENTATIONS of that concept.
 *
 * ============================================================================
 * 3. Visualizing a Mutex (diagram)
 * ============================================================================
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │                     MUTEX (1 permit)                            │
 *   │                                                                 │
 *   │         ┌───── CRITICAL SECTION ─────┐                         │
 *   │         │                             │                         │
 *   │         │    ┌─────┐                  │                         │
 *   │         │    │ T-1 │  (working)       │                         │
 *   │         │    └─────┘                  │                         │
 *   │         │                             │                         │
 *   │         │    Only 1 thread at a time  │                         │
 *   │         └─────────────────────────────┘                         │
 *   │                     │                                           │
 *   │                  ┌──┴──┐                                        │
 *   │                  │LOCK │  ← door is locked                      │
 *   │                  └──┬──┘                                        │
 *   │                     │                                           │
 *   │         WAITING:  ┌─────┐  ┌─────┐  ┌─────┐                   │
 *   │                   │ T-2 │  │ T-3 │  │ T-4 │  (blocked)        │
 *   │                   └─────┘  └─────┘  └─────┘                   │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 *   Compare this to Semaphore(3) we just learned:
 *
 *   ┌────────────────────────────────┬────────────────────────────────┐
 *   │         MUTEX                  │       SEMAPHORE(3)             │
 *   │                                │                                │
 *   │  ┌────────────────┐           │  ┌────────────────┐            │
 *   │  │    ┌─────┐     │           │  │ ┌───┐ ┌───┐ ┌───┐│          │
 *   │  │    │ T-1 │     │           │  │ │T-1│ │T-2│ │T-3││          │
 *   │  │    └─────┘     │           │  │ └───┘ └───┘ └───┘│          │
 *   │  │  1 thread only │           │  │  3 threads max    │          │
 *   │  └────────────────┘           │  └────────────────┘            │
 *   │                                │                                │
 *   │  T-2, T-3 wait                │  T-4, T-5 wait                 │
 *   └────────────────────────────────┴────────────────────────────────┘
 *
 *   Mutex = Semaphore(1) ... almost. Key difference below.
 *
 * ============================================================================
 * 4. Mutex vs Semaphore(1) -- the OWNERSHIP difference
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Property           │  Mutex                │  Semaphore(1)     │
 *   │─────────────────────│───────────────────────│───────────────────│
 *   │  Max threads inside │  1                    │  1                │
 *   │  Who can release?   │  ONLY the holder      │  ANY thread       │
 *   │  Reentrant?         │  YES (ReentrantLock)  │  NO               │
 *   │  Ownership          │  YES (tracked)        │  NO (no tracking) │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   Mutex (ReentrantLock):
 *   ┌──────────────────────────────────────────────────┐
 *   │  Thread A: lock.lock()      → A owns the mutex   │
 *   │  Thread B: lock.unlock()    → IllegalMonitor      │
 *   │                               Exception!          │
 *   │  Only Thread A can unlock what Thread A locked.   │
 *   └──────────────────────────────────────────────────┘
 *
 *   Semaphore(1):
 *   ┌──────────────────────────────────────────────────┐
 *   │  Thread A: semaphore.acquire()                    │
 *   │  Thread B: semaphore.release()  → WORKS!          │
 *   │                                                   │
 *   │  Any thread can release. No ownership tracking.   │
 *   │  This can lead to bugs if misused.                │
 *   └──────────────────────────────────────────────────┘
 *
 *   Diagram:
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  MUTEX (ownership)               SEMAPHORE(1) (no ownership)    │
 *   │                                                                  │
 *   │  T-A: lock()                     T-A: acquire()                  │
 *   │  T-A: "I own this lock"          T-A: "I have a permit"          │
 *   │                                                                  │
 *   │  T-B: unlock() → ERROR!          T-B: release() → OK (permit++)│
 *   │  "You don't own it!"             "No ownership check"           │
 *   │                                                                  │
 *   │  T-A: unlock() → OK              Now permits = 2!               │
 *   │  "You are the owner"             (accidentally exceeded max)    │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 5. Three ways to implement a Mutex in Java
 * ============================================================================
 *
 * ── 5a. synchronized keyword (intrinsic mutex / monitor) ──
 *
 *   class Counter {
 *       private int count = 0;
 *
 *       synchronized void increment() {     // mutex = this object's monitor
 *           count++;
 *       }
 *
 *       synchronized int getCount() {
 *           return count;
 *       }
 *   }
 *
 *   Flow:
 *   ┌──────────────────────────────────────────────────┐
 *   │  T-1: enters synchronized → acquires monitor     │
 *   │  T-2: tries to enter → BLOCKED (monitor held)    │
 *   │  T-1: exits synchronized → releases monitor      │
 *   │  T-2: acquires monitor → enters                  │
 *   └──────────────────────────────────────────────────┘
 *
 *   + Simplest syntax, automatic acquire/release
 *   - No tryLock, no timeout, no fairness control
 *
 *
 * ── 5b. ReentrantLock (explicit mutex) ──
 *
 *   class Counter {
 *       private int count = 0;
 *       private final ReentrantLock mutex = new ReentrantLock();
 *
 *       void increment() {
 *           mutex.lock();               // explicit acquire
 *           try {
 *               count++;
 *           } finally {
 *               mutex.unlock();          // explicit release (in finally!)
 *           }
 *       }
 *   }
 *
 *   + tryLock, timeout, fairness, Conditions
 *   - More verbose, must remember finally { unlock() }
 *
 *
 * ── 5c. Semaphore(1) (binary semaphore -- acts like a mutex) ──
 *
 *   class Counter {
 *       private int count = 0;
 *       private final Semaphore mutex = new Semaphore(1);
 *
 *       void increment() throws InterruptedException {
 *           mutex.acquire();             // take the 1 permit
 *           try {
 *               count++;
 *           } finally {
 *               mutex.release();          // return the permit
 *           }
 *       }
 *   }
 *
 *   + Simple, can be released by any thread (useful for async patterns)
 *   - No ownership, no reentrancy, risk of permit leak
 *
 * ============================================================================
 * 6. Full comparison table
 * ============================================================================
 *
 *   ┌─────────────────────┬──────────────┬────────────────┬──────────────┐
 *   │  Feature            │ synchronized │ ReentrantLock  │ Semaphore(1) │
 *   │─────────────────────│──────────────│────────────────│──────────────│
 *   │  Mutual exclusion   │     YES      │      YES       │     YES      │
 *   │  Ownership          │     YES      │      YES       │     NO       │
 *   │  Reentrant          │     YES      │      YES       │     NO       │
 *   │  tryLock            │     NO       │      YES       │  tryAcquire  │
 *   │  Timeout            │     NO       │      YES       │     YES      │
 *   │  Fairness           │     NO       │      YES       │     YES      │
 *   │  Conditions         │  wait/notify │  newCondition  │     NO       │
 *   │  Auto release       │     YES      │   NO (finally) │  NO (finally)│
 *   │  Interruptible      │     NO       │      YES       │     YES      │
 *   │  Cross-thread release│    NO       │      NO        │     YES      │
 *   └─────────────────────┴──────────────┴────────────────┴──────────────┘
 *
 * ============================================================================
 * 7. When to use what?
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Need simple mutual exclusion?                                   │
 *   │  └─► synchronized                                                │
 *   │                                                                  │
 *   │  Need tryLock, timeout, fairness, or Conditions?                 │
 *   │  └─► ReentrantLock                                               │
 *   │                                                                  │
 *   │  Need cross-thread release (async callback patterns)?            │
 *   │  └─► Semaphore(1)                                                │
 *   │                                                                  │
 *   │  Need N threads accessing concurrently?                          │
 *   │  └─► Semaphore(N) -- not a mutex anymore                        │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 8. Practical uses (one-liners)
 * ============================================================================
 *
 * - Shared counter: synchronized or ReentrantLock to protect count++ from lost updates.
 * - Database connection: mutex ensures only one thread uses a non-thread-safe connection at a time.
 * - File writer: mutex prevents two threads from writing to the same file simultaneously.
 * - Singleton initialization: double-checked locking uses a mutex to ensure only one instance is created.
 * - Thread-safe lazy cache: mutex around check-then-populate to avoid duplicate computation.
 * - Printer queue: mutex on the print buffer so only one thread modifies it at a time.
 *
 * ============================================================================
 * 9. Interview one-liner
 * ============================================================================
 *
 * A Mutex (mutual exclusion) allows only ONE thread into a critical section
 * at a time; in Java it's implemented via synchronized (intrinsic monitor)
 * or ReentrantLock (explicit), both of which track ownership and support reentrancy
 * -- unlike Semaphore(1) which has no ownership.
 *
 * ============================================================================
 * 10. Code demo below
 * ============================================================================
 * Three implementations of a thread-safe counter using:
 *   1. synchronized (intrinsic mutex)
 *   2. ReentrantLock (explicit mutex)
 *   3. Semaphore(1) (binary semaphore acting as mutex)
 * 5 threads each increment 10,000 times. All three produce 50,000.
 */
public class MutexDemo {

    // ── Implementation 1: synchronized (intrinsic mutex) ──
    static class SyncCounter {
        private int count = 0;
        synchronized void increment() { count++; }
        synchronized int getCount() { return count; }
    }

    // ── Implementation 2: ReentrantLock (explicit mutex) ──
    static class LockCounter {
        private int count = 0;
        private final ReentrantLock mutex = new ReentrantLock();
        void increment() {
            mutex.lock();
            try { count++; } finally { mutex.unlock(); }
        }
        int getCount() {
            mutex.lock();
            try { return count; } finally { mutex.unlock(); }
        }
    }

    // ── Implementation 3: Semaphore(1) (binary semaphore as mutex) ──
    static class SemaphoreCounter {
        private int count = 0;
        private final Semaphore mutex = new Semaphore(1);
        void increment() {
            try {
                mutex.acquire();
                try { count++; } finally { mutex.release(); }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        int getCount() { return count; }
    }

    public static void main(String[] args) throws InterruptedException {
        SyncCounter      sync  = new SyncCounter();
        LockCounter      lock  = new LockCounter();
        SemaphoreCounter sem   = new SemaphoreCounter();

        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    sync.increment();
                    lock.increment();
                    sem.increment();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();

        System.out.println("synchronized counter: " + sync.getCount());   // 50000
        System.out.println("ReentrantLock counter: " + lock.getCount());   // 50000
        System.out.println("Semaphore(1) counter:  " + sem.getCount());    // 50000
    }
}

/**
 * synchronized counter: 50000
 * ReentrantLock counter: 50000
 * Semaphore(1) counter:  50000
 *
 * All three produce the correct result because each provides
 * mutual exclusion -- only one thread increments at a time.
 */
