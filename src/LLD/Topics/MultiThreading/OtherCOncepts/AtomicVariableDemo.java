package LLD.Topics.MultiThreading.OtherCOncepts;

import java.util.concurrent.atomic.AtomicInteger;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                         Atomic Variables                               ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is the Read-Modify-Write cycle? (the root problem)
 * ============================================================================
 *
 * A simple count++ looks like ONE operation in Java, but the CPU actually
 * executes it as THREE separate steps:
 *
 *   count++  is really:
 *   ┌─────────────────────────────────────────────┐
 *   │  Step 1: READ   → load count from memory    │
 *   │  Step 2: MODIFY → add 1 to the value        │
 *   │  Step 3: WRITE  → store result back to memory│
 *   └─────────────────────────────────────────────┘
 *
 * If two threads do count++ at the same time, their steps can interleave:
 *
 *   Thread 1                Thread 2              count in memory
 *   ────────                ────────              ───────────────
 *   READ  count = 5                               5
 *                           READ  count = 5       5
 *   MODIFY 5 + 1 = 6                              5
 *                           MODIFY 5 + 1 = 6      5
 *   WRITE  count = 6                              6
 *                           WRITE  count = 6      6  ← LOST UPDATE!
 *
 *   Both threads read 5, both write 6. We lost one increment!
 *   Expected: 7.  Actual: 6.  This is a RACE CONDITION.
 *
 *   Over 20,000 iterations (this demo), you might get 12,869 instead of 20,000.
 *
 * ============================================================================
 * 2. What are Atomic Variables?
 * ============================================================================
 *
 * Atomic variables perform Read-Modify-Write as a SINGLE, INDIVISIBLE
 * operation at the hardware level using CAS (Compare-And-Swap).
 *
 * No other thread can see a "half-done" state. The entire operation
 * either completes fully or not at all.
 *
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  Normal int count++:                                         │
 *   │  ┌──────┐   ┌────────┐   ┌───────┐                         │
 *   │  │ READ │──►│ MODIFY │──►│ WRITE │   ← 3 steps, can break  │
 *   │  └──────┘   └────────┘   └───────┘                         │
 *   │                                                              │
 *   │  AtomicInteger.incrementAndGet():                            │
 *   │  ┌──────────────────────────────────┐                       │
 *   │  │  READ + MODIFY + WRITE (atomic)  │   ← 1 indivisible op │
 *   │  │  via CAS instruction on CPU      │                       │
 *   │  └──────────────────────────────────┘                       │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. How CAS (Compare-And-Swap) works internally
 * ============================================================================
 *
 * CAS is a CPU instruction that atomically does:
 *   "If the value at this address is what I EXPECT, replace it with NEW.
 *    Otherwise, do nothing and tell me it failed."
 *
 *   CAS(address, expectedValue, newValue)
 *
 *   Pseudocode of incrementAndGet():
 *   ┌──────────────────────────────────────────────────┐
 *   │  do {                                            │
 *   │      int expected = value;        // read        │
 *   │      int newVal = expected + 1;   // modify      │
 *   │  } while (!CAS(value, expected, newVal));        │
 *   │  // if CAS fails (another thread changed value), │
 *   │  // loop and retry with the new value            │
 *   └──────────────────────────────────────────────────┘
 *
 *   CAS success scenario:
 *   Thread 1                   Memory
 *   ────────                   ──────
 *   read value = 5
 *   expected = 5, new = 6
 *   CAS(5, 6) → value is still 5? YES → write 6 ✓
 *
 *   CAS retry scenario (another thread changed the value):
 *   Thread 1                   Thread 2              Memory
 *   ────────                   ────────              ──────
 *   read value = 5                                   5
 *                              read value = 5        5
 *                              CAS(5, 6) → YES ✓    6
 *   CAS(5, 6) → value is 6, not 5 → FAIL!
 *   retry: read value = 6
 *   CAS(6, 7) → value is still 6? YES → write 7 ✓  7
 *
 *   No lost updates! Both increments are counted.
 *
 * ============================================================================
 * 4. Different types of Atomic Variables
 * ============================================================================
 *
 *   ┌──────────────────────────┬───────────────────────────────────────┐
 *   │  Class                   │  What it wraps                        │
 *   │──────────────────────────│───────────────────────────────────────│
 *   │  AtomicInteger           │  int   (counters, indexes)            │
 *   │  AtomicLong              │  long  (timestamps, large counters)   │
 *   │  AtomicBoolean           │  boolean (flags, one-shot triggers)   │
 *   │  AtomicReference<V>      │  Object reference (swap objects)      │
 *   │  AtomicIntegerArray      │  int[] (array of atomic ints)         │
 *   │  AtomicLongArray         │  long[] (array of atomic longs)       │
 *   │  AtomicReferenceArray<V> │  V[] (array of atomic references)     │
 *   │  AtomicStampedReference  │  reference + stamp (solves ABA)       │
 *   │  LongAdder               │  long (high-contention sums, Java 8+)│
 *   └──────────────────────────┴───────────────────────────────────────┘
 *
 * ============================================================================
 * 5. Basic operations of AtomicInteger (all covered)
 * ============================================================================
 *
 * ── 5a. get() ──
 *   Returns the current value (volatile read, always fresh from memory).
 *
 *   AtomicInteger ai = new AtomicInteger(10);
 *   int val = ai.get();   // 10
 *
 * ── 5b. set(int newValue) ──
 *   Sets to the given value (volatile write, immediately visible to all threads).
 *
 *   ai.set(42);           // value is now 42
 *
 * ── 5c. compareAndSet(expected, update) ──
 *   The core CAS operation. Atomically sets to `update` if current value == `expected`.
 *   Returns true if successful, false if the value was changed by another thread.
 *
 *   AtomicInteger ai = new AtomicInteger(5);
 *   boolean success = ai.compareAndSet(5, 10);  // true,  value is now 10
 *   boolean fail    = ai.compareAndSet(5, 20);  // false, value is still 10
 *
 *   This is the building block for ALL other atomic operations.
 *
 *   Diagram:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  compareAndSet(expected=5, update=10)                │
 *   │                                                      │
 *   │  current value == 5?                                 │
 *   │  ├── YES → set to 10, return true                    │
 *   │  └── NO  → do nothing, return false                  │
 *   │                                                      │
 *   │  All three steps in ONE CPU instruction (CMPXCHG)    │
 *   └──────────────────────────────────────────────────────┘
 *
 * ── 5d. getAndIncrement() / incrementAndGet() ──
 *   Atomically increments by 1.
 *
 *   ai.set(5);
 *   int old = ai.getAndIncrement();   // returns 5, value is now 6
 *   int cur = ai.incrementAndGet();   // returns 7, value is now 7
 *
 *   ┌───────────────────────────────────────────────────────┐
 *   │  getAndIncrement()  → returns OLD value, then adds 1  │
 *   │  incrementAndGet()  → adds 1, then returns NEW value   │
 *   │                                                        │
 *   │  Think: "get AND increment" vs "increment AND get"     │
 *   │  (which word comes first = what you get back)          │
 *   └───────────────────────────────────────────────────────┘
 *
 * ── 5e. getAndDecrement() / decrementAndGet() ──
 *   Atomically decrements by 1. Same naming pattern.
 *
 *   ai.set(5);
 *   int old = ai.getAndDecrement();   // returns 5, value is now 4
 *   int cur = ai.decrementAndGet();   // returns 3, value is now 3
 *
 * ── 5f. getAndAdd(int delta) / addAndGet(int delta) ──
 *   Atomically adds any delta.
 *
 *   ai.set(10);
 *   int old = ai.getAndAdd(5);   // returns 10, value is now 15
 *   int cur = ai.addAndGet(5);   // returns 20, value is now 20
 *
 * ── 5g. getAndUpdate(IntUnaryOperator) / updateAndGet(IntUnaryOperator) ──
 *   Atomically applies any function (Java 8+).
 *
 *   ai.set(10);
 *   int val = ai.updateAndGet(x -> x * 2);   // returns 20, value is now 20
 *
 *   Useful for custom atomic logic beyond simple increment/add.
 *
 * ============================================================================
 * 6. Full operations summary diagram
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │                      AtomicInteger                               │
 *   │                                                                  │
 *   │  Read/Write:                                                     │
 *   │  ┌─────────────────────┬────────────────────────────────────┐   │
 *   │  │  get()              │ read current value (volatile)       │   │
 *   │  │  set(newVal)        │ write new value (volatile)          │   │
 *   │  └─────────────────────┴────────────────────────────────────┘   │
 *   │                                                                  │
 *   │  CAS (core):                                                     │
 *   │  ┌─────────────────────┬────────────────────────────────────┐   │
 *   │  │  compareAndSet(e,u) │ if current==e, set to u            │   │
 *   │  └─────────────────────┴────────────────────────────────────┘   │
 *   │                                                                  │
 *   │  Increment/Decrement:                                            │
 *   │  ┌─────────────────────┬────────────────────────────────────┐   │
 *   │  │  incrementAndGet()  │ ++value, return new                 │   │
 *   │  │  getAndIncrement()  │ return old, then ++value            │   │
 *   │  │  decrementAndGet()  │ --value, return new                 │   │
 *   │  │  getAndDecrement()  │ return old, then --value            │   │
 *   │  └─────────────────────┴────────────────────────────────────┘   │
 *   │                                                                  │
 *   │  Add/Update:                                                     │
 *   │  ┌─────────────────────┬────────────────────────────────────┐   │
 *   │  │  addAndGet(delta)   │ value += delta, return new          │   │
 *   │  │  getAndAdd(delta)   │ return old, then value += delta     │   │
 *   │  │  updateAndGet(fn)   │ apply fn, return new                │   │
 *   │  │  getAndUpdate(fn)   │ return old, then apply fn           │   │
 *   │  └─────────────────────┴────────────────────────────────────┘   │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 7. Atomic vs volatile vs synchronized
 * ============================================================================
 *
 *   ┌──────────────────┬────────────┬──────────────┬───────────────┐
 *   │  Feature         │  volatile  │ synchronized │  Atomic       │
 *   │──────────────────│────────────│──────────────│───────────────│
 *   │  Visibility      │    YES     │     YES      │     YES       │
 *   │  Atomicity       │    NO      │     YES      │     YES       │
 *   │  (for count++)   │  (broken!) │  (via lock)  │  (via CAS)    │
 *   │  Blocking        │    NO      │     YES      │     NO        │
 *   │  Performance     │   Fast     │    Slowest   │    Fastest    │
 *   │  Lock-free       │    YES     │     NO       │     YES       │
 *   │  Deadlock risk   │    NO      │     YES      │     NO        │
 *   └──────────────────┴────────────┴──────────────┴───────────────┘
 *
 *   Why Atomic is fastest:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  synchronized: acquire lock → do work → release lock │
 *   │                other threads BLOCK while waiting      │
 *   │                                                      │
 *   │  Atomic (CAS): try CAS → success? done!             │
 *   │                fail? spin and retry                   │
 *   │                NO lock, NO blocking, NO context switch│
 *   └──────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 8. When NOT to use Atomic (limitations)
 * ============================================================================
 *
 *   a) Multiple variables that must change together:
 *      AtomicInteger can only protect ONE variable.
 *      If you need to update x AND y atomically, use synchronized or Lock.
 *
 *   b) High contention on a single counter:
 *      Many threads doing CAS on the same variable → lots of retries.
 *      Use LongAdder instead (stripes across multiple cells, sums on read).
 *
 *      ┌──────────────────────────────────────────────────┐
 *      │  AtomicLong with 64 threads:                     │
 *      │  All 64 CAS on the same memory location          │
 *      │  → many retries, high contention                 │
 *      │                                                  │
 *      │  LongAdder with 64 threads:                      │
 *      │  Each thread increments its own cell              │
 *      │  sum() aggregates all cells on read               │
 *      │  → much less contention                          │
 *      └──────────────────────────────────────────────────┘
 *
 *   c) Complex check-then-act:
 *      "if (value > 0) then decrement" is TWO operations.
 *      Use compareAndSet in a loop, or use synchronized.
 *
 * ============================================================================
 * 9. Practical uses (one-liners)
 * ============================================================================
 *
 * - Request counter: AtomicLong to count HTTP requests across all handler threads.
 * - Sequence generator: AtomicInteger.incrementAndGet() for unique IDs.
 * - Non-blocking stack/queue: AtomicReference for CAS-based push/pop.
 * - Shutdown flag: AtomicBoolean for graceful thread termination.
 * - ConcurrentHashMap internals: uses CAS for bucket initialization and size counting.
 * - Connection pool: AtomicInteger tracks available connections without locking.
 * - Metrics / stats: LongAdder for high-throughput counters in monitoring systems.
 *
 * ============================================================================
 * 10. Interview one-liner
 * ============================================================================
 *
 * Atomic variables use hardware CAS (Compare-And-Swap) to perform
 * read-modify-write as a single indivisible operation without locks,
 * giving thread safety with better performance than synchronized.
 *
 * ============================================================================
 * 11. Code demo below
 * ============================================================================
 * Two threads each increment 10,000 times.
 *   - `count` (plain int): gets a wrong value (~12,000-19,999) due to lost updates.
 *   - `counter` (AtomicInteger): always gets exactly 20,000.
 */
public class AtomicVariableDemo {
    private static int count = 0;
    private static final AtomicInteger counter = new AtomicInteger(0);
    public static void main(String[] args) {
        Thread one = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                count++;
                counter.incrementAndGet();
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                count++;
                counter.incrementAndGet();
            }
        });

        one.start();
        two.start();

        try {
            one.join();
            two.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Count value is : 12869 which is not correct, this is bad
        System.out.println("Count value is : " + count);

        // Counter value is 20000 which is good
        System.out.println("Counter value is : " + counter.get());
    }
}
