package LLD.Topics.MultiThreading.Locks;

import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                          ReentrantLock                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a ReentrantLock?
 * ============================================================================
 *
 * A ReentrantLock is an explicit, manually-controlled mutual-exclusion lock
 * from java.util.concurrent.locks that replaces the implicit synchronized
 * keyword with more power and flexibility.
 *
 * "Reentrant" means: the SAME thread that already holds the lock can acquire
 * it again without deadlocking itself. Each lock() increments a hold count,
 * each unlock() decrements it. The lock is truly released when count hits 0.
 *
 *   ReentrantLock lock = new ReentrantLock();
 *
 *   lock.lock();       // acquire (blocks until available)
 *   try {
 *       // critical section
 *   } finally {
 *       lock.unlock();  // MUST be in finally to avoid deadlocks on exceptions
 *   }
 *
 * ============================================================================
 * 2. How does this work? (internal diagram)
 * ============================================================================
 *
 *   ReentrantLock internals (simplified):
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ReentrantLock                                       │
 *   │  ┌────────────────┐   ┌────────────────────────┐    │
 *   │  │  state = 0     │   │  owner = null           │    │
 *   │  │  (hold count)  │   │  (which thread holds?)  │    │
 *   │  └────────────────┘   └────────────────────────┘    │
 *   │                                                      │
 *   │  Wait queue (FIFO / CLH queue):                      │
 *   │  ┌─────┐   ┌─────┐   ┌─────┐                       │
 *   │  │ T-3 │──►│ T-2 │──►│ T-1 │──► (lock holder)      │
 *   │  └─────┘   └─────┘   └─────┘                       │
 *   │  (threads waiting to acquire the lock)               │
 *   └──────────────────────────────────────────────────────┘
 *
 *   When Thread-A calls lock.lock():
 *   ┌─────────────────────────────────────────────────────┐
 *   │  Is state == 0 ? (lock is free)                     │
 *   │  ├── YES: CAS state 0 → 1, set owner = Thread-A    │
 *   │  │        Thread-A enters critical section           │
 *   │  │                                                   │
 *   │  └── NO: Is owner == Thread-A ? (reentrant!)        │
 *   │       ├── YES: state++ (e.g. 1 → 2)                │
 *   │       │        Thread-A enters again (nested lock)   │
 *   │       │                                              │
 *   │       └── NO: Thread-A joins wait queue, PARKS      │
 *   └─────────────────────────────────────────────────────┘
 *
 *   When Thread-A calls lock.unlock():
 *   ┌─────────────────────────────────────────────────────┐
 *   │  state-- (e.g. 2 → 1, or 1 → 0)                   │
 *   │  Is state == 0 ?                                    │
 *   │  ├── YES: set owner = null, UNPARK next in queue    │
 *   │  └── NO:  still holding (nested), do nothing more   │
 *   └─────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Reentrant Lock vs Non-Reentrant Lock (with diagram)
 * ============================================================================
 *
 *   "Reentrant" = the same thread can acquire the lock it already holds.
 *   "Non-Reentrant" = if a thread already holds the lock, trying to
 *                      acquire it again DEADLOCKS itself.
 *
 *   ── Reentrant Lock (Java's ReentrantLock / synchronized) ──
 *
 *   Thread-A:
 *   ┌──────────────────────────────────────────────────┐
 *   │  lock.lock()          → state = 1, owner = A    │
 *   │  │                                               │
 *   │  │  lock.lock()       → state = 2, owner = A    │  ← ALLOWED!
 *   │  │  │  // nested critical section                │     same thread,
 *   │  │  lock.unlock()     → state = 1               │     just increments
 *   │  │                                               │     hold count
 *   │  lock.unlock()        → state = 0, owner = null │
 *   │  (lock fully released)                           │
 *   └──────────────────────────────────────────────────┘
 *
 *   ── Non-Reentrant Lock (hypothetical) ──
 *
 *   Thread-A:
 *   ┌──────────────────────────────────────────────────┐
 *   │  lock.lock()          → locked, owner = A       │
 *   │  │                                               │
 *   │  │  lock.lock()       → BLOCKED!                │  ← DEADLOCK!
 *   │  │  │                                            │     Thread-A waits
 *   │  │  │  (waiting for itself to release the lock)  │     for itself...
 *   │  │  │  (but it can't release because it's        │     forever.
 *   │  │  │   waiting here)                            │
 *   │  │  ▼                                            │
 *   │  │  ██████████ DEADLOCK ████████████             │
 *   └──────────────────────────────────────────────────┘
 *
 *   Real-world example of why non-reentrant would break:
 *
 *   class Account {
 *       Lock lock = new NonReentrantLock(); // hypothetical
 *
 *       void transfer(Account to, int amount) {
 *           lock.lock();               // acquires lock
 *           this.debit(amount);        // calls debit() below...
 *       }
 *
 *       void debit(int amount) {
 *           lock.lock();               // DEADLOCK! same lock, same thread
 *           balance -= amount;
 *           lock.unlock();
 *       }
 *   }
 *
 *   With ReentrantLock: debit() just increments hold count → works fine.
 *   With Non-Reentrant: debit() blocks forever → deadlock.
 *
 * ============================================================================
 * 4. When are ReentrantLocks needed? (why not just use synchronized?)
 * ============================================================================
 *
 *   synchronized is simpler but limited. ReentrantLock gives you:
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Capability              │ synchronized │  ReentrantLock        │
 *   │──────────────────────────│──────────────│───────────────────────│
 *   │  Try without blocking    │     NO       │  tryLock()            │
 *   │  Try with timeout        │     NO       │  tryLock(t, unit)     │
 *   │  Interruptible waiting   │     NO       │  lockInterruptibly()  │
 *   │  Fairness (FIFO order)   │     NO       │  new ReentrantLock(   │
 *   │                          │              │    true) // fair       │
 *   │  Multiple conditions     │     NO       │  lock.newCondition()  │
 *   │  Hold count inspection   │     NO       │  getHoldCount()       │
 *   │  Queue length inspection │     NO       │  getQueueLength()     │
 *   │  Check if held by self   │     NO       │  isHeldByCurrentThread│
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   Rule of thumb:
 *   - Use synchronized for simple mutual exclusion.
 *   - Use ReentrantLock when you need ANY of the above capabilities.
 *
 * ============================================================================
 * 5. Lock Fairness
 * ============================================================================
 *
 *   new ReentrantLock()       → NON-FAIR (default)
 *   new ReentrantLock(true)   → FAIR
 *
 *   Non-fair lock (default):
 *   ┌──────────────────────────────────────────────────────┐
 *   │  When lock is released, ANY thread can grab it,      │
 *   │  even one that just arrived (barging).                │
 *   │                                                       │
 *   │  Wait queue: [T-1] → [T-2] → [T-3]                  │
 *   │                                                       │
 *   │  Lock released! T-5 just called lock() at that moment │
 *   │  T-5 STEALS the lock (barge ahead of T-1)            │
 *   │                                                       │
 *   │  + Higher throughput (less overhead)                   │
 *   │  - Possible starvation (T-1 keeps waiting)            │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Fair lock:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  Threads acquire in FIFO order. No barging.           │
 *   │                                                       │
 *   │  Wait queue: [T-1] → [T-2] → [T-3]                  │
 *   │                                                       │
 *   │  Lock released! T-1 gets it (longest waiter)          │
 *   │  T-5 must go to the back of the queue.                │
 *   │                                                       │
 *   │  + No starvation                                      │
 *   │  - Lower throughput (FIFO ordering has overhead)       │
 *   └──────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. Important methods of ReentrantLock (all covered)
 * ============================================================================
 *
 * ── 6a. lock() ──
 *   Acquires the lock. Blocks indefinitely until available.
 *   Always pair with unlock() in a finally block.
 *
 * ── 6b. unlock() ──
 *   Releases the lock. Decrements hold count. When count = 0, lock is freed.
 *   MUST be called in a finally block to prevent deadlocks on exceptions.
 *
 * ── 6c. getHoldCount() ──
 *   Returns the number of times the current thread has locked without unlocking.
 *
 *   lock.lock();                      // holdCount = 1
 *   lock.lock();                      // holdCount = 2  (reentrant)
 *   System.out.println(
 *       lock.getHoldCount());         // prints 2
 *   lock.unlock();                    // holdCount = 1
 *   lock.unlock();                    // holdCount = 0 (released)
 *
 *   Use case: debugging / assertions to verify nesting depth.
 *
 * ── 6d. tryLock() ──
 *   Tries to acquire the lock WITHOUT blocking. Returns immediately.
 *
 *   if (lock.tryLock()) {
 *       try {
 *           // got the lock, do work
 *       } finally {
 *           lock.unlock();
 *       }
 *   } else {
 *       // lock not available, do something else
 *   }
 *
 *   Timeline:
 *   Thread-A          Thread-B
 *   lock.lock() ✓
 *   │ working...      lock.tryLock() → false (not blocked!)
 *   │                 │ does alternative work
 *   lock.unlock()     │
 *
 *   Use case: avoid deadlocks in lock-ordering scenarios.
 *
 * ── 6e. tryLock(timeout, TimeUnit) ──
 *   Tries to acquire but waits UP TO the given timeout.
 *
 *   if (lock.tryLock(2, TimeUnit.SECONDS)) {
 *       try {
 *           // got it within 2 seconds
 *       } finally {
 *           lock.unlock();
 *       }
 *   } else {
 *       // timed out after 2 seconds
 *   }
 *
 *   Timeline:
 *   Thread-A              Thread-B
 *   lock.lock() ✓
 *   │ working...           tryLock(2s) → waiting...
 *   │ (1.5 sec)            │ (still waiting, < 2s)
 *   lock.unlock()          │ → true! got the lock
 *   ────────── OR ──────────────────────────────
 *   │ (3 sec, still held)  │ (2s elapsed) → false (timed out)
 *
 *   Use case: SLA-bound services that can't wait forever for a resource.
 *
 * ── 6f. tryLock() has a problem ──
 *
 *   tryLock() does NOT respect fairness! Even on a fair lock, tryLock()
 *   barges ahead of waiting threads.
 *
 *   Fair lock queue: [T-1] → [T-2] → [T-3]
 *   Lock released!
 *   T-5 calls tryLock() → SUCCEEDS (barges ahead of T-1!)
 *
 *   This is by design (for performance), but it breaks fairness guarantees.
 *   If you need fairness with tryLock, use tryLock(0, TimeUnit.SECONDS)
 *   which DOES respect the fair ordering.
 *
 * ── 6g. lockInterruptibly() ──
 *   Like lock(), but the waiting thread can be interrupted.
 *
 *   try {
 *       lock.lockInterruptibly();
 *       try {
 *           // critical section
 *       } finally {
 *           lock.unlock();
 *       }
 *   } catch (InterruptedException e) {
 *       // thread was interrupted while waiting for the lock
 *       // can clean up and exit gracefully
 *   }
 *
 *   With lock(): thread waits forever, cannot be interrupted.
 *   With lockInterruptibly(): thread can be woken up by interrupt.
 *
 *   Use case: shutdown hooks that need to cancel threads waiting for locks.
 *
 * ── 6h. isHeldByCurrentThread() ──
 *   Returns true if the calling thread currently holds this lock.
 *
 *   assert lock.isHeldByCurrentThread();  // defensive check
 *
 *   Use case: assertions in methods that require the caller to already hold the lock.
 *
 * ── 6i. getQueueLength() ──
 *   Returns an estimate of how many threads are waiting to acquire the lock.
 *
 *   System.out.println("Threads waiting: " + lock.getQueueLength());
 *
 *   Use case: monitoring / metrics dashboards to detect lock contention.
 *
 * ── 6j. newCondition() ──
 *   Creates a Condition object tied to this lock (see ConditionDemo.java).
 *
 *   Condition notFull  = lock.newCondition();
 *   Condition notEmpty = lock.newCondition();
 *
 *   This is the BIG advantage over synchronized:
 *   synchronized has ONE wait set (wait/notify).
 *   ReentrantLock can have MANY conditions (await/signal on each).
 *
 *   Use case: producer-consumer where producers and consumers need separate wait sets.
 *
 * ============================================================================
 * 7. Full method summary diagram
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │                      ReentrantLock                               │
 *   │                                                                  │
 *   │  Acquire methods:                                                │
 *   │  ┌────────────────────────┬─────────────────────────────────┐   │
 *   │  │  lock()                │ block forever until acquired     │   │
 *   │  │  tryLock()             │ try once, return immediately     │   │
 *   │  │  tryLock(t, unit)      │ try with timeout                 │   │
 *   │  │  lockInterruptibly()   │ block, but can be interrupted    │   │
 *   │  └────────────────────────┴─────────────────────────────────┘   │
 *   │                                                                  │
 *   │  Release:                                                        │
 *   │  ┌────────────────────────┬─────────────────────────────────┐   │
 *   │  │  unlock()              │ decrement hold count, free at 0  │   │
 *   │  └────────────────────────┴─────────────────────────────────┘   │
 *   │                                                                  │
 *   │  Inspection:                                                     │
 *   │  ┌────────────────────────┬─────────────────────────────────┐   │
 *   │  │  getHoldCount()        │ how many times current thread    │   │
 *   │  │                        │ has locked (nesting depth)        │   │
 *   │  │  isHeldByCurrentThread │ does current thread hold it?     │   │
 *   │  │  getQueueLength()      │ how many threads waiting?        │   │
 *   │  └────────────────────────┴─────────────────────────────────┘   │
 *   │                                                                  │
 *   │  Condition factory:                                              │
 *   │  ┌────────────────────────┬─────────────────────────────────┐   │
 *   │  │  newCondition()        │ create a separate wait/signal set│   │
 *   │  └────────────────────────┴─────────────────────────────────┘   │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 8. Practical uses (one-liners)
 * ============================================================================
 *
 * - Recursive/nested locking: a method acquires a lock, then calls another method that also needs the same lock (this demo).
 * - tryLock for deadlock avoidance: two threads try to lock two resources; if one tryLock fails, release and retry.
 * - Timed lock in microservices: HTTP handler uses tryLock(500ms) to fail fast instead of queuing indefinitely.
 * - Fair lock for ticket booking: ensures first-come-first-served access to limited seats.
 * - lockInterruptibly for graceful shutdown: daemon threads can be interrupted out of lock waits during app shutdown.
 * - Condition-based coordination: producer-consumer with separate "not full" / "not empty" conditions (see ConditionDemo.java).
 * - Monitoring hot locks: use getQueueLength() to publish lock contention metrics to a dashboard.
 *
 * ============================================================================
 * 9. Interview one-liner
 * ============================================================================
 *
 * ReentrantLock is an explicit lock that lets the same thread re-acquire it
 * (incrementing a hold count), supports tryLock, timed waits, fairness, and
 * multiple Conditions -- everything synchronized cannot do.
 *
 * ============================================================================
 * 10. Code demo below
 * ============================================================================
 * methodA() locks, increments sharedData, then calls methodB() which also
 * locks the SAME lock. Because ReentrantLock is reentrant, methodB() does NOT
 * deadlock -- it just increments hold count to 2. Each unlock decrements it.
 * Five threads run methodA() concurrently; the lock ensures mutual exclusion.
 */
public class ReentrantLockDemo {
    private final ReentrantLock lock = new ReentrantLock();
    private int sharedData = 0;

    private void methodA() {
        lock.lock();

        try {
            sharedData++;
            System.out.println("Method A: sharedData = " + sharedData);

            // calling methodB which also requires the lock, even when methodA is holding a lock
            // if we do not have ReentrantLock we would have encountered the blocking scenario (deadlock)
            methodB();
        } finally {
            lock.unlock();
        }
    }

    private void methodB() {
        lock.lock();

        try {
            sharedData--;
            System.out.println("Method B: sharedData = " + sharedData);
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        ReentrantLockDemo demo = new ReentrantLockDemo();
        for (int i = 0; i < 5; i++) {
            new Thread(demo::methodA).start();
        }
    }
}

/**
 * Method A: sharedData = 1
 * Method B: sharedData = 0
 * Method A: sharedData = 1
 * Method B: sharedData = 0
 * Method A: sharedData = 1
 * Method B: sharedData = 0
 * Method A: sharedData = 1
 * Method B: sharedData = 0
 * Method A: sharedData = 1
 * Method B: sharedData = 0
 */
