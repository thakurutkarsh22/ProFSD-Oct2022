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
 * 4. Lock (ReentrantLock) vs synchronized -- the big comparison
 * ============================================================================
 *
 * Java offers TWO ways to protect shared state:
 *   A) synchronized keyword (built-in, implicit)
 *   B) Lock interface / ReentrantLock (java.util.concurrent.locks, explicit)
 *
 * ── How synchronized works ──
 *
 *   synchronized (lockObj) {          // implicitly acquires monitor
 *       // critical section
 *   }                                 // implicitly releases monitor (even on exception)
 *
 *   - Lock and unlock are automatic (enter block = lock, exit block = unlock).
 *   - Cannot forget to unlock -- always released, even if an exception is thrown.
 *   - Cannot try to acquire without blocking.
 *   - Cannot interrupt a thread waiting for the lock.
 *
 * ── How ReentrantLock works ──
 *
 *   Lock lock = new ReentrantLock();
 *   lock.lock();                      // explicitly acquire
 *   try {
 *       // critical section
 *   } finally {
 *       lock.unlock();                // explicitly release (MUST be in finally!)
 *   }
 *
 *   - Lock and unlock are manual -- YOU control when.
 *   - If you forget unlock() in finally, the lock is held forever (deadlock).
 *   - Can tryLock() (non-blocking), tryLock(timeout) (timed), lockInterruptibly().
 *   - Supports fairness (FIFO ordering of waiting threads).
 *   - Can have multiple Condition objects (like multiple wait sets).
 *
 * ── Diagram: synchronized vs Lock flow ──
 *
 *   synchronized:                         ReentrantLock:
 *   ─────────────                         ──────────────
 *   ┌──────────────────────┐              ┌──────────────────────┐
 *   │ enter synchronized   │              │ lock.lock()          │
 *   │ block                │              │                      │
 *   │ ┌──────────────────┐ │              │ try {                │
 *   │ │ critical section │ │              │ ┌──────────────────┐ │
 *   │ └──────────────────┘ │              │ │ critical section │ │
 *   │                      │              │ └──────────────────┘ │
 *   │ exit block           │              │ } finally {          │
 *   │ (auto unlock)        │              │   lock.unlock()      │
 *   └──────────────────────┘              │ }                    │
 *                                         └──────────────────────┘
 *   Auto-release: YES                     Auto-release: NO (manual)
 *   Forget unlock: IMPOSSIBLE             Forget unlock: BUG (deadlock)
 *
 * ── Feature comparison table ──
 *
 *   Feature                    synchronized                 ReentrantLock
 *   ────────────────────────   ──────────────────────────   ──────────────────────────
 *   Acquire/release            Automatic (enter/exit block) Manual (lock/unlock)
 *   Exception safety           Always releases               Must use try-finally
 *   Try without blocking       No                            tryLock() returns boolean
 *   Timed wait for lock        No                            tryLock(timeout, unit)
 *   Interruptible wait         No                            lockInterruptibly()
 *   Fairness (FIFO)            No (not guaranteed)           Yes (new ReentrantLock(true))
 *   Multiple conditions        No (one wait set per object)  Yes (lock.newCondition())
 *   Lock across methods        No (must be same block)       Yes (lock in A, unlock in B)
 *   Read/Write separation      No                            Yes (ReentrantReadWriteLock)
 *   Performance                JVM-optimized (biased locks)  Slightly heavier
 *   Simplicity                 Simple, less error-prone      Verbose, easy to misuse
 *
 * ── When to use which? ──
 *
 *   Use synchronized when:
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │ - Simple critical section (enter block, do work, exit)        │
 *   │ - You don't need tryLock, timeout, or interruptibility        │
 *   │ - You want the simplest, safest option                        │
 *   │ - Most Java code: synchronized is the default choice          │
 *   └────────────────────────────────────────────────────────────────┘
 *
 *   Use ReentrantLock when:
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │ - You need tryLock() (e.g., "try to acquire, else skip")      │
 *   │ - You need timed lock attempts (tryLock with timeout)          │
 *   │ - You need interruptible locking (lockInterruptibly)           │
 *   │ - You need fairness guarantees (FIFO order)                    │
 *   │ - You need multiple Condition objects (see ArrayBlockingQueue) │
 *   │ - You need read/write separation (ReentrantReadWriteLock)      │
 *   │ - Lock must span across methods (lock in A, unlock in B)      │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * ── Diagram: tryLock (only possible with ReentrantLock) ──
 *
 *   Thread 1                            Thread 2
 *   ────────                            ────────
 *   lock.lock()  <-- acquired           lock.tryLock() <-- returns FALSE
 *   │  doing work                       │  lock not available
 *   │                                   │  do something else instead!
 *   │                                   │  (no blocking, no waiting)
 *   lock.unlock()
 *
 *   With synchronized: Thread 2 would BLOCK forever until Thread 1 exits.
 *   With tryLock():    Thread 2 checks, sees lock is taken, moves on.
 *
 * ── Diagram: multiple Conditions (only possible with ReentrantLock) ──
 *
 *   ReentrantLock lock = new ReentrantLock();
 *   Condition notFull  = lock.newCondition();   // producers wait here
 *   Condition notEmpty = lock.newCondition();    // consumers wait here
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  synchronized: ONE wait set per object                          │
 *   │  lock.wait()  -- all threads go into the same wait set          │
 *   │  lock.notify() -- wakes ANY waiter (might be wrong one)         │
 *   │                                                                  │
 *   │  ReentrantLock: MULTIPLE conditions                              │
 *   │  notFull.await()   -- only producers wait here                  │
 *   │  notEmpty.await()  -- only consumers wait here                  │
 *   │  notEmpty.signal() -- wakes ONLY a consumer (precise!)          │
 *   │  notFull.signal()  -- wakes ONLY a producer (precise!)          │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   This is exactly how ArrayBlockingQueue works internally
 *   (see ArrayBlockingQueueDemo.java).
 *
 * ── Interview one-liner ──
 * synchronized is simpler and auto-releases; ReentrantLock is more powerful
 * with tryLock, timed waits, fairness, and multiple Conditions. Use synchronized
 * by default; switch to ReentrantLock only when you need its extra features.
 *
 * ============================================================================
 * 5. Practical uses (one-liners)
 * ============================================================================
 *
 * - Protecting shared counters, caches, or collections from concurrent modification.
 * - Implementing thread-safe singleton (double-checked locking uses synchronized).
 * - Guarding critical sections in legacy code before java.util.concurrent existed.
 * - Any read-modify-write operation on shared state (e.g., balance transfer in banking).
 *
 * ============================================================================
 * 6. Code demo below
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
