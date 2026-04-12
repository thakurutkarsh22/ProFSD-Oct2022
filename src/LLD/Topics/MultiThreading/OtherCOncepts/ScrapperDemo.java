package LLD.Topics.MultiThreading.OtherCOncepts;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                           Semaphores                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What are Semaphores?
 * ============================================================================
 *
 * A Semaphore is a concurrency primitive that controls access to a shared
 * resource by maintaining a set of PERMITS. A thread must acquire a permit
 * before accessing the resource and release it when done.
 *
 * Unlike a Lock (1 thread at a time), a Semaphore can allow N threads
 * to access a resource concurrently (where N = number of permits).
 *
 *   Semaphore semaphore = new Semaphore(3);  // 3 permits
 *
 *   semaphore.acquire();   // take a permit (blocks if none available)
 *   try {
 *       // access the shared resource
 *   } finally {
 *       semaphore.release();   // return the permit
 *   }
 *
 *   Think of it like a parking lot with 3 spaces:
 *   - Cars (threads) enter if there's a free space (permit).
 *   - If all 3 spaces are taken, new cars wait at the gate.
 *   - When a car leaves, a waiting car can enter.
 *
 * ============================================================================
 * 2. Visualizing Semaphores (diagram)
 * ============================================================================
 *
 *   Semaphore with 3 permits:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │                         GATE                                    │
 *   │                    permits = 3                                  │
 *   │                                                                 │
 *   │  ┌─────────────── INSIDE (resource) ──────────────┐            │
 *   │  │  ┌─────┐   ┌─────┐   ┌─────┐                  │            │
 *   │  │  │ T-1 │   │ T-2 │   │ T-3 │   (3 threads in) │            │
 *   │  │  └─────┘   └─────┘   └─────┘                  │            │
 *   │  │                                                │            │
 *   │  │  permits remaining = 0                         │            │
 *   │  └────────────────────────────────────────────────┘            │
 *   │                                                                 │
 *   │  WAITING QUEUE:                                                 │
 *   │  ┌─────┐  ┌─────┐  ┌─────┐  ... ┌──────┐                     │
 *   │  │ T-4 │  │ T-5 │  │ T-6 │      │ T-15 │  (12 waiting)       │
 *   │  └─────┘  └─────┘  └─────┘      └──────┘                     │
 *   │  (blocked on acquire() until a permit is released)             │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 *   When T-1 finishes and calls release():
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  permits: 0 → 1                                                │
 *   │  T-4 (first in queue) gets the permit → enters                 │
 *   │  permits: 1 → 0                                                │
 *   │                                                                 │
 *   │  INSIDE: [T-2] [T-3] [T-4]    (still max 3)                   │
 *   │  WAITING: [T-5] [T-6] ... [T-15]                              │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. How acquire() and release() work internally
 * ============================================================================
 *
 *   acquire():
 *   ┌──────────────────────────────────────────────────┐
 *   │  permits > 0 ?                                   │
 *   │  ├── YES: permits--, thread proceeds              │
 *   │  └── NO:  thread is BLOCKED, added to wait queue │
 *   └──────────────────────────────────────────────────┘
 *
 *   release():
 *   ┌──────────────────────────────────────────────────┐
 *   │  permits++                                       │
 *   │  Is anyone waiting in the queue?                 │
 *   │  ├── YES: wake the next waiter (FIFO if fair)   │
 *   │  └── NO:  permit just sits there for next caller│
 *   └──────────────────────────────────────────────────┘
 *
 *   Timeline for this program (3 permits, 15 threads):
 *
 *   Time ──────────────────────────────────────────────────►
 *
 *   Batch 1:  [T-1 scraping] [T-2 scraping] [T-3 scraping]    (2 sec)
 *   Batch 2:  [T-4 scraping] [T-5 scraping] [T-6 scraping]    (2 sec)
 *   Batch 3:  [T-7 scraping] [T-8 scraping] [T-9 scraping]    (2 sec)
 *   Batch 4:  [T-10]         [T-11]         [T-12]             (2 sec)
 *   Batch 5:  [T-13]         [T-14]         [T-15]             (2 sec)
 *
 *   Max 3 threads scraping at any moment. Total time ~10 sec.
 *
 * ============================================================================
 * 4. Multiple Permits
 * ============================================================================
 *
 * A thread can acquire/release MORE THAN ONE permit at a time:
 *
 *   semaphore.acquire(2);   // takes 2 permits at once
 *   semaphore.release(2);   // returns 2 permits at once
 *
 * This is useful when a thread needs a "heavier" share of the resource.
 *
 *   Example: connection pool with 10 permits
 *   ┌──────────────────────────────────────────────────────┐
 *   │  Semaphore pool = new Semaphore(10);                 │
 *   │                                                      │
 *   │  Small query:   pool.acquire(1);   // needs 1 conn   │
 *   │  Bulk import:   pool.acquire(5);   // needs 5 conns  │
 *   │  Full backup:   pool.acquire(10);  // needs ALL conns│
 *   │                                                      │
 *   │  Remaining permits determine how many others can run │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Visualization with 5 permits, thread takes 2:
 *
 *   Before:  [■] [■] [■] [■] [■]    permits = 5
 *   acquire(2): takes 2 permits
 *   After:   [□] [□] [■] [■] [■]    permits = 3
 *                                     (only 3 left for other threads)
 *
 * ============================================================================
 * 5. Semaphore vs Lock -- key difference
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │                                                                  │
 *   │  Lock / synchronized:        Semaphore:                          │
 *   │  ┌───────────┐               ┌───────────┐                      │
 *   │  │ 1 thread  │               │ N threads │                      │
 *   │  │ at a time │               │ at a time │                      │
 *   │  └───────────┘               └───────────┘                      │
 *   │  permits = 1                  permits = N                        │
 *   │  (mutual exclusion)           (controlled concurrency)           │
 *   │                                                                  │
 *   │  NOTE: Semaphore(1) behaves like a Lock (binary semaphore)      │
 *   │  but Semaphore has NO ownership -- any thread can release it!   │
 *   │  With Lock, only the thread that locked can unlock.             │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. Methods of Semaphore (all covered)
 * ============================================================================
 *
 * ── 6a. acquire() ──
 *   Takes one permit. Blocks if none available.
 *
 *   semaphore.acquire();      // blocks until a permit is free
 *
 * ── 6b. acquire(int permits) ──
 *   Takes multiple permits at once. Blocks if not enough available.
 *
 *   semaphore.acquire(3);     // needs 3 permits to proceed
 *
 * ── 6c. release() ──
 *   Returns one permit. Wakes a waiting thread if any.
 *
 *   semaphore.release();      // always call in finally block!
 *
 * ── 6d. release(int permits) ──
 *   Returns multiple permits at once.
 *
 *   semaphore.release(3);     // returns 3 permits
 *
 *   NOTE: release() can INCREASE permits beyond the initial count!
 *   If you create Semaphore(3) and call release() without acquire(),
 *   you now have 4 permits. Be careful.
 *
 * ── 6e. tryAcquire() ──
 *   Tries to take a permit WITHOUT blocking. Returns immediately.
 *
 *   if (semaphore.tryAcquire()) {
 *       try {
 *           // got a permit, do work
 *       } finally {
 *           semaphore.release();
 *       }
 *   } else {
 *       // no permit available, do fallback
 *       System.out.println("Resource busy, try again later");
 *   }
 *
 *   Timeline:
 *   ┌──────────────────────────────────────────────┐
 *   │  permits = 0 (all taken)                     │
 *   │  T-5: tryAcquire() → false (not blocked!)   │
 *   │  T-5: does alternative work immediately      │
 *   └──────────────────────────────────────────────┘
 *
 * ── 6f. tryAcquire(long timeout, TimeUnit unit) ──
 *   Tries to take a permit, waits up to the given timeout.
 *
 *   if (semaphore.tryAcquire(2, TimeUnit.SECONDS)) {
 *       try {
 *           // got a permit within 2 seconds
 *       } finally {
 *           semaphore.release();
 *       }
 *   } else {
 *       // timed out after 2 seconds, no permit
 *   }
 *
 * ── 6g. availablePermits() ──
 *   Returns the current number of available permits (snapshot).
 *
 *   int free = semaphore.availablePermits();
 *   System.out.println("Free slots: " + free);
 *
 *   WARNING: this is just a snapshot! By the time you act on it,
 *   another thread may have acquired/released. Use for monitoring only.
 *
 * ── 6h. new Semaphore(int permits, boolean fair) ──
 *   Creates a semaphore with fairness option.
 *
 *   new Semaphore(3);         // non-fair (default), barging allowed
 *   new Semaphore(3, true);   // fair, FIFO order guaranteed
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  Non-fair: a new thread can steal a just-released    │
 *   │  permit ahead of waiting threads (higher throughput) │
 *   │                                                      │
 *   │  Fair: longest-waiting thread gets the permit first  │
 *   │  (no starvation, but lower throughput)               │
 *   └──────────────────────────────────────────────────────┘
 *
 * ── 6i. drainPermits() ──
 *   Acquires ALL available permits at once. Returns the count.
 *
 *   int drained = semaphore.drainPermits();  // takes all, returns count
 *
 *   Use case: temporarily block all access for maintenance.
 *
 * ============================================================================
 * 7. Methods summary diagram
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │                        Semaphore                                 │
 *   │                                                                  │
 *   │  Acquire:                                                        │
 *   │  ┌──────────────────────────┬───────────────────────────────┐   │
 *   │  │  acquire()               │ block until 1 permit free      │   │
 *   │  │  acquire(n)              │ block until n permits free     │   │
 *   │  │  tryAcquire()            │ try once, return immediately   │   │
 *   │  │  tryAcquire(t, unit)     │ try with timeout               │   │
 *   │  │  drainPermits()          │ take ALL available permits     │   │
 *   │  └──────────────────────────┴───────────────────────────────┘   │
 *   │                                                                  │
 *   │  Release:                                                        │
 *   │  ┌──────────────────────────┬───────────────────────────────┐   │
 *   │  │  release()               │ return 1 permit                │   │
 *   │  │  release(n)              │ return n permits               │   │
 *   │  └──────────────────────────┴───────────────────────────────┘   │
 *   │                                                                  │
 *   │  Inspection:                                                     │
 *   │  ┌──────────────────────────┬───────────────────────────────┐   │
 *   │  │  availablePermits()      │ how many permits are free?     │   │
 *   │  │  hasQueuedThreads()      │ any threads waiting?           │   │
 *   │  │  getQueueLength()        │ how many threads waiting?      │   │
 *   │  └──────────────────────────┴───────────────────────────────┘   │
 *   │                                                                  │
 *   │  Constructor:                                                    │
 *   │  ┌──────────────────────────┬───────────────────────────────┐   │
 *   │  │  Semaphore(permits)      │ non-fair (default)             │   │
 *   │  │  Semaphore(permits, true)│ fair (FIFO)                    │   │
 *   │  └──────────────────────────┴───────────────────────────────┘   │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 8. How THIS program works -- diagram
 * ============================================================================
 *
 *   ScrapperDemo:
 *   - CachedThreadPool creates up to 15 threads
 *   - ScrapeService has Semaphore(3) -- only 3 can scrape simultaneously
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  CachedThreadPool (15 threads)                                   │
 *   │  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐│
 *   │  │T1 │T2 │T3 │T4 │T5 │T6 │T7 │T8 │T9 │T10│T11│T12│T13│T14│T15││
 *   │  └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘│
 *   │                          │                                       │
 *   │                          ▼                                       │
 *   │              ┌─── Semaphore(3) GATE ───┐                        │
 *   │              │     permits = 3          │                        │
 *   │              └──────────────────────────┘                        │
 *   │                          │                                       │
 *   │             ┌────────────┼────────────┐                         │
 *   │             ▼            ▼            ▼                         │
 *   │          [T-1]        [T-2]        [T-3]   ← scraping (2 sec)  │
 *   │                                                                  │
 *   │          T-4 through T-15 WAIT at the gate                      │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 9. Practical uses (one-liners)
 * ============================================================================
 *
 * - Rate limiter: Semaphore(maxRequestsPerSecond) to throttle API calls.
 * - Connection pool: Semaphore(poolSize) to limit concurrent DB connections.
 * - Web scraper throttle: limit concurrent HTTP requests to avoid IP ban (this demo).
 * - Thread pool bounded access: limit how many tasks access a slow external service.
 * - Print queue: Semaphore(numPrinters) to limit concurrent print jobs.
 * - Dining philosophers: each fork is a Semaphore(1), philosopher acquires two forks.
 * - Bounded buffer (alternative to BlockingQueue): use two semaphores for empty/full slots.
 *
 * ============================================================================
 * 10. Interview one-liner
 * ============================================================================
 *
 * A Semaphore maintains a set of permits to control how many threads can
 * access a shared resource concurrently -- acquire() takes a permit (blocks
 * if none), release() returns one; unlike a Lock, it allows N concurrent
 * threads and has no ownership (any thread can release).
 *
 * ============================================================================
 * 11. Code demo below
 * ============================================================================
 * 15 threads submit scrape jobs via CachedThreadPool.
 * ScrapeService (singleton enum) has Semaphore(3) -- only 3 threads
 * scrape at a time. Each scrape takes 2 seconds. Result: threads run
 * in batches of 3, total ~10 seconds for all 15.
 */
public class ScrapperDemo {

    public static void main(String[] args) {
        ExecutorService service = Executors.newCachedThreadPool();

        // doesn't matter we have 15 threads -- at a time only 3 will scrape
        for (int i = 0; i < 15; i++) {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    ScrapeService.INSTANCE.scrape();
                }
            });
        }
    }
}


enum ScrapeService {
    INSTANCE;

    private Semaphore semaphore = new Semaphore(3); // 3 permits

    public void scrape() {
        try {
            semaphore.acquire();
            invokeScrapeBot();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    private void invokeScrapeBot() {
        System.out.println("Scraping data");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
