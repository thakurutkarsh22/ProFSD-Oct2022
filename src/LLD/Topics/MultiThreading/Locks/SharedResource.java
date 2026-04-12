package LLD.Topics.MultiThreading.Locks;


import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║              ReadWriteLock / ReentrantReadWriteLock                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a ReadWriteLock?
 * ============================================================================
 *
 * A ReadWriteLock splits a single lock into TWO cooperating locks:
 *
 *   ReadWriteLock rwLock = new ReentrantReadWriteLock();
 *   Lock readLock  = rwLock.readLock();   // shared lock
 *   Lock writeLock = rwLock.writeLock();  // exclusive lock
 *
 * Rules:
 *   - Multiple threads can hold the READ lock at the same time  (shared).
 *   - Only ONE thread can hold the WRITE lock at a time         (exclusive).
 *   - Read lock and write lock are MUTUALLY EXCLUSIVE:
 *       If any thread holds the write lock, no one can read.
 *       If any thread holds a read lock, no one can write.
 *
 * Why? Most real-world data is read far more often than written.
 * A plain ReentrantLock or synchronized blocks ALL threads -- even
 * readers that could safely run in parallel. ReadWriteLock lets
 * readers proceed concurrently while only blocking for writes.
 *
 * ============================================================================
 * 2. How does it work? (diagram)
 * ============================================================================
 *
 *   ┌────────────────────────────────────────────────────────────────────┐
 *   │                 ReentrantReadWriteLock                             │
 *   │                                                                    │
 *   │   readLock  (shared)            writeLock  (exclusive)             │
 *   │   ┌──────────────────┐          ┌──────────────────┐              │
 *   │   │  Multiple readers │          │  Only ONE writer  │              │
 *   │   │  can hold this    │          │  can hold this    │              │
 *   │   │  simultaneously   │          │  at a time        │              │
 *   │   └──────────────────┘          └──────────────────┘              │
 *   │                                                                    │
 *   └────────────────────────────────────────────────────────────────────┘
 *
 *   Compatibility matrix:
 *   ┌───────────────────┬──────────────────┬──────────────────┐
 *   │                   │  Reader holds    │  Writer holds    │
 *   │───────────────────│──────────────────│──────────────────│
 *   │  New Reader wants │     ALLOWED      │     BLOCKED      │
 *   │  New Writer wants │     BLOCKED      │     BLOCKED      │
 *   └───────────────────┴──────────────────┴──────────────────┘
 *
 *   Read-Read   → parallel (the whole point!)
 *   Read-Write  → blocked
 *   Write-Read  → blocked
 *   Write-Write → blocked
 *
 * ============================================================================
 * 3. Concurrency comparison -- why ReadWriteLock wins
 * ============================================================================
 *
 *   Scenario: 5 readers + 1 writer, using a plain ReentrantLock
 *
 *   ReentrantLock (all access serialized):
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  R1──►R2──►R3──►W1──►R4──►R5                               │
 *   │  ───────────────────────────►  time                         │
 *   │  Every operation waits for the previous one. Slow.          │
 *   └──────────────────────────────────────────────────────────────┘
 *
 *   ReadWriteLock (readers run in parallel):
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  R1─┐                                                       │
 *   │  R2─┤ (parallel!)    W1──►   R4─┐                           │
 *   │  R3─┘                        R5─┘ (parallel!)               │
 *   │  ──────────────────────────────►  time                      │
 *   │  Readers overlap, only writer serializes. Much faster!      │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 4. Wait queue internals (how threads queue up)
 * ============================================================================
 *
 *   ReentrantReadWriteLock uses a SINGLE AQS (AbstractQueuedSynchronizer)
 *   wait queue for BOTH readers and writers. The 32-bit state int is split:
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  state (32 bits)                                     │
 *   │  ┌─────────────────┬─────────────────┐              │
 *   │  │  upper 16 bits  │  lower 16 bits  │              │
 *   │  │  = read count   │  = write count  │              │
 *   │  │  (shared holds) │  (exclusive)    │              │
 *   │  └─────────────────┴─────────────────┘              │
 *   └──────────────────────────────────────────────────────┘
 *
 *   The wait queue is a FIFO linked list of threads:
 *
 *   ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐
 *   │ W-2 │──►│ R-3 │──►│ R-4 │──►│ W-1 │──► [lock holder(s)]
 *   └─────┘   └─────┘   └─────┘   └─────┘
 *     (waiting)                      (next to acquire)
 *
 *   When the lock is released, the queue decides who goes next:
 *
 *   Case 1: Writer at the head
 *   ┌──────────────────────────────────────────────────────┐
 *   │  W-1 at head → only W-1 is woken (exclusive)        │
 *   │  R-3, R-4, W-2 keep waiting                         │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Case 2: Reader at the head
 *   ┌──────────────────────────────────────────────────────┐
 *   │  R-3 at head → R-3 is woken                         │
 *   │  R-4 is right behind and also a reader → R-4 is     │
 *   │  woken too! (consecutive readers batch-wake)         │
 *   │  W-2 is a writer → STOP. W-2 waits.                 │
 *   │                                                      │
 *   │  Result: R-3 and R-4 read in parallel, W-2 waits    │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Timeline example with the queue above:
 *   ┌───────────────────────────────────────────────────────────────┐
 *   │  Time ──►                                                     │
 *   │                                                               │
 *   │  [W-1 writing...]  [R-3 + R-4 reading...]  [W-2 writing...]  │
 *   │  ├─────────────────├──────────────────────├─────────────────  │
 *   │  (exclusive)        (shared, parallel)      (exclusive)       │
 *   └───────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 5. Fair vs Non-fair ReadWriteLock
 * ============================================================================
 *
 *   new ReentrantReadWriteLock()       → NON-FAIR (default)
 *   new ReentrantReadWriteLock(true)   → FAIR
 *
 *   Non-fair (default):
 *   ┌──────────────────────────────────────────────────────┐
 *   │  Readers can barge ahead of a queued writer.          │
 *   │                                                       │
 *   │  Queue: [W-1 waiting]                                 │
 *   │  R-5 arrives → barges, reads immediately!             │
 *   │  R-6 arrives → barges again!                          │
 *   │  W-1 starves because new readers keep arriving.       │
 *   │                                                       │
 *   │  + Higher read throughput                              │
 *   │  - Writer starvation possible                          │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Fair:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  If a writer is queued, new readers go behind it.     │
 *   │                                                       │
 *   │  Queue: [W-1 waiting]                                 │
 *   │  R-5 arrives → goes BEHIND W-1 in queue               │
 *   │  W-1 gets its turn. No starvation.                    │
 *   │                                                       │
 *   │  + No starvation for writers                           │
 *   │  - Lower read throughput (less barging)                │
 *   └──────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. Lock downgrade (write → read)
 * ============================================================================
 *
 *   A thread holding the WRITE lock can acquire the READ lock before
 *   releasing the write lock. This is called "lock downgrade":
 *
 *   writeLock.lock();
 *   try {
 *       // modify data
 *       readLock.lock();       // acquire read lock while holding write lock
 *   } finally {
 *       writeLock.unlock();    // release write lock, still hold read lock
 *   }
 *   try {
 *       // now holding only read lock -- other readers can join
 *   } finally {
 *       readLock.unlock();
 *   }
 *
 *   Flow:
 *   ┌────────────────────────────────────────────────────────┐
 *   │  [WRITE lock held]                                     │
 *   │       │                                                │
 *   │       ▼  readLock.lock()  (allowed -- downgrade)       │
 *   │  [WRITE + READ held]                                   │
 *   │       │                                                │
 *   │       ▼  writeLock.unlock()                            │
 *   │  [READ lock held]  ← other readers can now join!       │
 *   │       │                                                │
 *   │       ▼  readLock.unlock()                             │
 *   │  [no lock held]                                        │
 *   └────────────────────────────────────────────────────────┘
 *
 *   NOTE: Lock UPGRADE (read → write) is NOT supported!
 *   If you try readLock.lock() then writeLock.lock(), you will DEADLOCK.
 *
 *   ┌────────────────────────────────────────────────────┐
 *   │  readLock.lock()                                   │
 *   │       │                                            │
 *   │       ▼  writeLock.lock()                          │
 *   │       DEADLOCK! Write lock waits for all readers   │
 *   │       to release, but this thread IS a reader      │
 *   │       and can't release because it's blocked here. │
 *   └────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 7. ReadWriteLock vs ReentrantLock vs synchronized
 * ============================================================================
 *
 *   ┌────────────────────────┬──────────────┬────────────────┬──────────────────┐
 *   │  Feature               │ synchronized │ ReentrantLock  │ ReadWriteLock    │
 *   │────────────────────────│──────────────│────────────────│──────────────────│
 *   │  Read-read parallel    │     NO       │      NO        │     YES          │
 *   │  Write exclusion       │     YES      │      YES       │     YES          │
 *   │  tryLock               │     NO       │      YES       │     YES          │
 *   │  Fairness              │     NO       │      YES       │     YES          │
 *   │  Conditions            │     NO       │      YES       │  on writeLock    │
 *   │  Lock downgrade        │     N/A      │      N/A       │     YES          │
 *   │  Best when             │  simple      │  need tryLock/ │  reads >> writes │
 *   │                        │  exclusion   │  fairness/cond │                  │
 *   └────────────────────────┴──────────────┴────────────────┴──────────────────┘
 *
 * ============================================================================
 * 8. How THIS program works -- diagram
 * ============================================================================
 *
 *   2 reader threads (each reads 3 times) + 1 writer thread (writes 5 times)
 *
 *   Reader-1   Reader-2   Writer
 *   ────────   ────────   ──────
 *   readLock   readLock
 *   reads: 0   reads: 0   (waiting for readers to release)
 *   unlock     unlock
 *                          writeLock
 *                          writes: 1
 *                          writes: 2
 *                          writes: 3
 *                          writes: 4
 *                          writes: 5
 *                          unlock
 *   readLock   readLock
 *   reads: 5   reads: 5
 *   unlock     unlock
 *   readLock   readLock
 *   reads: 5   reads: 5
 *   unlock     unlock
 *
 *   Key observation: Both readers read simultaneously (parallel),
 *   but writer waits until all readers release, and readers wait
 *   while writer holds the write lock.
 *
 * ============================================================================
 * 9. Practical uses (one-liners)
 * ============================================================================
 *
 * - In-memory cache: many threads read cached values concurrently, only one thread refreshes the cache.
 * - Configuration store: app reads config on every request, admin updates config rarely.
 * - DNS lookup table: thousands of lookups/sec (reads) vs rare record updates (writes).
 * - User session store: read session on every HTTP request, write only on login/logout.
 * - Feature flag service: all requests read flags concurrently, flag changes are rare writes.
 * - ConcurrentHashMap alternative: when you need explicit read/write lock semantics over a plain HashMap.
 *
 * ============================================================================
 * 10. Interview one-liner
 * ============================================================================
 *
 * ReentrantReadWriteLock splits a lock into a shared read-lock (multiple
 * readers in parallel) and an exclusive write-lock (one writer, no readers),
 * giving much better throughput than a plain lock when reads vastly outnumber writes.
 *
 * ============================================================================
 * 11. Code demo below
 * ============================================================================
 * SharedResource has a counter guarded by a ReadWriteLock.
 * increment() acquires writeLock (exclusive), getValue() acquires readLock (shared).
 * 2 reader threads and 1 writer thread run concurrently -- readers overlap,
 * but writer blocks everyone and vice versa.
 */
public class SharedResource {
    private int counter = 0;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();


    public void increment() {
        lock.writeLock().lock();

        try {
            counter++;
            System.out.println(Thread.currentThread().getName() + " writes: " + counter);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void getValue() {
        lock.readLock().lock();
        try {
            System.out.println(Thread.currentThread().getName() + " reads: " + counter);
        } finally {
            lock.readLock().unlock();
        }
    }
}

class ReadWriteLockDemo {
    public static void main(String[] args) {
        SharedResource sharedResource = new SharedResource();

        // creating 2 reader thread
        for (int i = 0; i < 2; i++) {
            Thread readerThread = new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    sharedResource.getValue();
                }
            });
            readerThread.setName("Reader thread " + (i+ 1));
            readerThread.start();
        }

        // creating writer thread
        Thread writerThread = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                sharedResource.increment();
            }
        });
        writerThread.setName("writerThread thread ");
        writerThread.start();
    }
}

/**
 * Reader thread 1 reads: 0
 * Reader thread 2 reads: 0
 * writerThread thread  writes: 1
 * writerThread thread  writes: 2
 * writerThread thread  writes: 3
 * writerThread thread  writes: 4
 * writerThread thread  writes: 5
 * Reader thread 1 reads: 5
 * Reader thread 2 reads: 5
 * Reader thread 1 reads: 5
 * Reader thread 2 reads: 5
 *
 *
 * Reader thread 2 reads: 0
 * Reader thread 1 reads: 0
 * writerThread thread  writes: 1
 * writerThread thread  writes: 2
 * writerThread thread  writes: 3
 * writerThread thread  writes: 4
 * Reader thread 2 reads: 4
 * Reader thread 1 reads: 4
 * writerThread thread  writes: 5
 * Reader thread 2 reads: 5
 * Reader thread 1 reads: 5
 */
