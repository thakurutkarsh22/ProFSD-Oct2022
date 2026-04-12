package LLD.Topics.MultiThreading.Locks;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║               The Visibility Problem & volatile keyword                ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. CPU Architecture -- Core, Register & Cache (why visibility matters)
 * ============================================================================
 *
 *   Modern CPUs have multiple cores, each with its own registers and caches.
 *   Variables live in RAM, but for speed, each core copies them closer:
 *
 *                           More Size ─────►
 *                      ◄───── More Latency
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │                                                                 │
 *   │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
 *   │  │ Core 1 │  │ Core 2 │  │ Core 3 │  │ Core 4 │  │ Core 5 │  │
 *   │  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │
 *   │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
 *   │  │Register│  │Register│  │Register│  │Register│  │Register│  │  ← fastest
 *   │  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │    (~1 ns)
 *   │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
 *   │  │L1 Cache│  │L1 Cache│  │L1 Cache│  │L1 Cache│  │L1 Cache│  │  ← ~2 ns
 *   │  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │
 *   │  ┌───────────────────┐  ┌──────────────────┐  ┌────────────┐  │
 *   │  │     L2 Cache      │  │    L2 Cache       │  │  L2 Cache  │  │  ← ~10 ns
 *   │  └───────────────────┘  └──────────────────┘  └────────────┘  │
 *   │  ┌─────────────────────────────────────────────────────────┐   │
 *   │  │                      L3 Cache (shared)                  │   │  ← ~40 ns
 *   │  └─────────────────────────────────────────────────────────┘   │
 *   │  ┌─────────────────────────────────────────────────────────┐   │
 *   │  │                      RAM (Main Memory)                  │   │  ← ~100 ns
 *   │  └─────────────────────────────────────────────────────────┘   │
 *   │                                                                 │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 *   Key point: each core has its OWN register and L1 cache.
 *   A variable updated in Core 1's register may NOT be visible to Core 2
 *   until it is flushed back to main memory.
 *
 * ============================================================================
 * 2. The Visibility Problem -- what is it?
 * ============================================================================
 *
 *   When Thread 1 (on Core 1) writes a variable but Thread 2 (on Core 2)
 *   still reads the OLD value from its own cache/register.
 *
 *   class Example {
 *       int count = 0;
 *
 *       // Writer Thread
 *       public void write() {
 *           count = 1;
 *       }
 *
 *       // Reader Thread
 *       public void read() {
 *           int val = count;    // might read 0 even AFTER write() ran!
 *       }
 *   }
 *
 *   How this goes wrong:
 *
 *   Thread 1 (Core 1)           Thread 2 (Core 2)
 *   ───────────────             ───────────────
 *        │                           │
 *        ▼                           ▼
 *   ┌─────────┐                ┌─────────┐
 *   │ Core 1  │                │ Core 2  │
 *   │Register │                │Register │
 *   │count = 1│                │ val = 0 │  ← stale!
 *   └────┬────┘                └────┬────┘
 *        │                          │
 *   count = 1                  count = 0       ← Core 2's cache
 *   (in Core 1's               still has old
 *    cache only)                value!
 *        │                          │
 *   ┌────┴──────────────────────────┴────┐
 *   │           Shared Cache             │
 *   │           count = 0                │  ← NOT yet flushed!
 *   └───────────────────────────────────┘
 *
 *   Thread 1 set count = 1, but this value is stuck in Core 1's register.
 *   Thread 2 reads count from its own cache and gets 0 (the stale value).
 *   Thread 2 NEVER sees the update. This is the VISIBILITY PROBLEM.
 *
 * ============================================================================
 * 3. Why does this happen?
 * ============================================================================
 *
 *   Three reasons:
 *
 *   a) CPU Caching:
 *      Each core caches variables locally for speed. Without explicit
 *      flush/invalidate, other cores see stale copies.
 *
 *   b) Compiler Reordering:
 *      The JIT compiler can reorder instructions for optimization.
 *      It might move a read BEFORE a write if it thinks they're independent.
 *
 *   c) CPU Instruction Reordering:
 *      Modern CPUs execute instructions out-of-order for pipeline efficiency.
 *      A write might be buffered in a store buffer and not yet visible.
 *
 *   All three are invisible to a single thread (it sees its own writes)
 *   but devastating across threads.
 *
 * ============================================================================
 * 4. A classic real-world bug -- infinite loop without volatile
 * ============================================================================
 *
 *   class StoppableTask implements Runnable {
 *       boolean running = true;          // NOT volatile
 *
 *       public void run() {
 *           while (running) {            // may NEVER see false!
 *               // do work
 *           }
 *       }
 *
 *       public void stop() {
 *           running = false;             // written by another thread
 *       }
 *   }
 *
 *   What happens:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  Main thread calls stop()  →  running = false in RAM    │
 *   │                                                          │
 *   │  Worker thread's Core cached running = true              │
 *   │  JIT compiler HOISTS the read out of the loop:          │
 *   │                                                          │
 *   │  Optimized to:                                           │
 *   │    if (running) {                                        │
 *   │        while (true) { // do work }   ← infinite loop!   │
 *   │    }                                                     │
 *   │                                                          │
 *   │  The worker NEVER re-reads running from main memory.     │
 *   └──────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 5. Java's solution: the volatile keyword
 * ============================================================================
 *
 *   volatile boolean running = true;     // forces visibility
 *
 *   What volatile does:
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  WRITE to a volatile variable:                                   │
 *   │  1. Flush the new value from register/cache to MAIN MEMORY      │
 *   │  2. Insert a STORE memory barrier (StoreStore + StoreLoad)      │
 *   │                                                                  │
 *   │  READ of a volatile variable:                                    │
 *   │  1. Invalidate local cache copy                                  │
 *   │  2. Read fresh value directly from MAIN MEMORY                   │
 *   │  3. Insert a LOAD memory barrier (LoadLoad + LoadStore)          │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   With volatile:
 *
 *   Thread 1 (Core 1)            Thread 2 (Core 2)
 *   ───────────────              ───────────────
 *   volatile count = 1
 *        │
 *        ▼  FLUSH to main memory
 *   ┌──────────────────────────────────────┐
 *   │         Main Memory                  │
 *   │         count = 1  ✓                 │  ← guaranteed visible
 *   └──────────────────────────────────────┘
 *        │                           │
 *        │                     INVALIDATE cache
 *        │                     READ from main memory
 *        │                           ▼
 *        │                      val = count
 *        │                      val = 1  ✓   ← sees latest value!
 *
 *   Without volatile:  write stays in Core 1's cache → Core 2 reads stale 0
 *   With volatile:     write flushed to RAM → Core 2 reads fresh 1
 *
 * ============================================================================
 * 6. Happens-before guarantee
 * ============================================================================
 *
 *   volatile gives a "happens-before" relationship:
 *
 *   Everything that happened BEFORE a volatile write in Thread 1
 *   is GUARANTEED to be visible to Thread 2 AFTER it reads that
 *   same volatile variable.
 *
 *   Thread 1:                    Thread 2:
 *   ─────────                    ─────────
 *   a = 42;                      // after reading volatile flag:
 *   b = "hello";                 int x = a;        // guaranteed 42
 *   volatile flag = true;  ───►  String s = b;     // guaranteed "hello"
 *                                boolean f = flag;  // true
 *
 *   The volatile write acts as a memory fence -- it flushes ALL
 *   preceding writes (even non-volatile ones like a and b) to
 *   main memory.
 *
 * ============================================================================
 * 7. Side effects / limitations of volatile
 * ============================================================================
 *
 *   a) NO ATOMICITY for compound operations:
 *      volatile does NOT make count++ atomic!
 *
 *      volatile int count = 0;
 *      count++;    // this is actually: read → increment → write (3 steps)
 *
 *      Thread 1: reads count = 5
 *      Thread 2: reads count = 5          (both read 5)
 *      Thread 1: writes count = 6
 *      Thread 2: writes count = 6         (lost update! should be 7)
 *
 *      ┌──────────────────────────────────────────────────┐
 *      │  volatile guarantees VISIBILITY, not ATOMICITY.  │
 *      │  For count++, use AtomicInteger or synchronized. │
 *      └──────────────────────────────────────────────────┘
 *
 *   b) Performance cost:
 *      Every read goes to main memory (~100ns) instead of L1 cache (~2ns).
 *      Every write flushes to main memory immediately.
 *      Prevents compiler and CPU reordering optimizations.
 *
 *      ┌──────────────────────────────────────────────────┐
 *      │  Normal variable:    read from L1 cache  ~2 ns  │
 *      │  volatile variable:  read from RAM      ~100 ns │
 *      │                                                  │
 *      │  ~50x slower per read! Don't overuse.            │
 *      └──────────────────────────────────────────────────┘
 *
 *   c) Cannot replace locks for mutual exclusion:
 *      volatile only ensures visibility. It does NOT provide a
 *      critical section. Two threads can still interleave operations.
 *
 *   d) No ordering between unrelated volatile variables:
 *      volatile x and volatile y are independently ordered.
 *      There's no guarantee about the order of x write vs y write
 *      across different threads unless they access the SAME variable.
 *
 * ============================================================================
 * 8. When to use volatile vs other options
 * ============================================================================
 *
 *   ┌─────────────────────────────┬──────────────────────────────────────┐
 *   │  Scenario                   │  Use                                 │
 *   │─────────────────────────────│──────────────────────────────────────│
 *   │  Simple flag (stop/start)   │  volatile boolean                    │
 *   │  Read-only after publish    │  volatile reference (immutable obj)  │
 *   │  Counter (increment)        │  AtomicInteger / AtomicLong          │
 *   │  CAS operations             │  AtomicReference / VarHandle         │
 *   │  Compound check-then-act   │  synchronized or ReentrantLock       │
 *   │  Multiple fields together   │  synchronized or ReentrantLock       │
 *   └─────────────────────────────┴──────────────────────────────────────┘
 *
 *   Rule of thumb:
 *   - One writer, many readers, simple read/write → volatile
 *   - Multiple writers OR compound operations     → lock / atomic
 *
 * ============================================================================
 * 9. volatile vs synchronized vs Atomic
 * ============================================================================
 *
 *   ┌──────────────────┬────────────┬──────────────┬───────────────┐
 *   │  Feature         │  volatile  │ synchronized │  AtomicInteger│
 *   │──────────────────│────────────│──────────────│───────────────│
 *   │  Visibility      │    YES     │     YES      │     YES       │
 *   │  Atomicity       │    NO      │     YES      │     YES       │
 *   │  Mutual exclusion│    NO      │     YES      │     NO        │
 *   │  Blocking        │    NO      │     YES      │     NO        │
 *   │  Performance     │   Fast     │    Slowest   │    Fastest    │
 *   │  Use case        │  flags,    │  critical    │  counters,    │
 *   │                  │  one-shot  │  sections    │  CAS ops      │
 *   │                  │  publish   │              │               │
 *   └──────────────────┴────────────┴──────────────┴───────────────┘
 *
 * ============================================================================
 * 10. Practical uses (one-liners)
 * ============================================================================
 *
 * - Shutdown flag: volatile boolean running for graceful thread termination.
 * - Double-checked locking singleton: volatile instance field prevents partially constructed object visibility.
 * - Configuration reload: volatile reference to an immutable config object; readers always see latest config.
 * - Status/progress indicator: one writer thread updates volatile progress, many UI threads read it.
 * - ConcurrentHashMap internals: uses volatile Node.val and Node.next for lock-free reads.
 * - Happens-before trigger: volatile write flushes all prior non-volatile writes to main memory.
 *
 * ============================================================================
 * 11. Interview one-liner
 * ============================================================================
 *
 * volatile guarantees that every read of a variable goes to main memory
 * (not a stale CPU cache), ensuring visibility across threads, but it
 * does NOT provide atomicity for compound operations like count++.
 *
 * ============================================================================
 * 12. Code demo below
 * ============================================================================
 * Two demos:
 *   Demo 1 (BrokenVisibility):  Without volatile, reader thread may NEVER see
 *           the flag change and loops forever.
 *   Demo 2 (FixedVisibility):   With volatile, reader thread sees the update
 *           immediately and exits the loop.
 */
public class VolatileKeywordDemo {

    // ── Demo 1: WITHOUT volatile (visibility problem) ──
    static class BrokenVisibility {
        boolean running = true;  // NOT volatile -- may be cached by core

        void start() {
            Thread reader = new Thread(() -> {
                System.out.println("[Broken] Reader started...");
                while (running) {
                    // busy-wait; JIT may hoist `running` out of loop
                }
                System.out.println("[Broken] Reader stopped.");  // may NEVER print!
            });

            Thread writer = new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) { }
                System.out.println("[Broken] Writer setting running = false");
                running = false;
            });

            reader.start();
            writer.start();
        }
    }

    // ── Demo 2: WITH volatile (visibility guaranteed) ──
    static class FixedVisibility {
        volatile boolean running = true;  // volatile -- always read from main memory

        void start() throws InterruptedException {
            Thread reader = new Thread(() -> {
                System.out.println("[Fixed] Reader started...");
                while (running) {
                    // volatile read: guaranteed to see latest value
                }
                System.out.println("[Fixed] Reader stopped.");  // WILL print
            });

            Thread writer = new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) { }
                System.out.println("[Fixed] Writer setting running = false");
                running = false;  // volatile write: flushed to main memory
            });

            reader.start();
            writer.start();

            reader.join(2000);
            writer.join(2000);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Demo 2: WITH volatile (guaranteed to stop) ===");
        new FixedVisibility().start();

        // NOTE: Demo 1 (BrokenVisibility) is intentionally not run by default
        // because it may hang forever -- the reader thread may never terminate.
        // Uncomment to test (Ctrl+C to kill):
        //
        // System.out.println("=== Demo 1: WITHOUT volatile (may hang forever) ===");
        // new BrokenVisibility().start();
    }
}

/**
 * Expected output:
 *
 * === Demo 2: WITH volatile (guaranteed to stop) ===
 * [Fixed] Reader started...
 * [Fixed] Writer setting running = false
 * [Fixed] Reader stopped.
 */
