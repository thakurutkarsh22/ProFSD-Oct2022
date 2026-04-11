package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║              Thread-Safety of Collections (ArrayList)                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. The problem -- ArrayList is NOT thread-safe
 * ============================================================================
 *
 * ArrayList.add() is not synchronized. When two threads call add() concurrently,
 * they race on the internal array, element count, and resize logic.
 *
 * ============================================================================
 * 2. How the race condition happens -- diagram
 * ============================================================================
 *
 *   ArrayList internals:  Object[] elementData,  int size
 *
 *   Thread 1: add(42)                    Thread 2: add(99)
 *   ──────────────────                   ──────────────────
 *   read size = 5                        read size = 5     (same!)
 *   elementData[5] = 42                  elementData[5] = 99  (overwrites 42!)
 *   size = 6                             size = 6          (should be 7!)
 *
 *   Result: ONE element lost, size = 6 instead of 7.
 *
 *   Possible outcomes with concurrent add():
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  - list.size() < expected (lost updates)                    │
 *   │  - ArrayIndexOutOfBoundsException (resize race)             │
 *   │  - Null elements in the middle of the list                  │
 *   │  - Occasionally correct by luck (do NOT rely on this)       │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. The fix -- synchronized wrappers or concurrent collections
 * ============================================================================
 *
 *   Option 1: Collections.synchronizedList(new ArrayList<>())
 *   - Wraps every method in synchronized(mutex) { ... }
 *   - Simple fix, but ONE lock for ALL operations (low throughput).
 *
 *   Option 2: CopyOnWriteArrayList
 *   - Creates a new array copy on every write. Reads are lock-free.
 *   - Good when reads >> writes (e.g., listener lists).
 *
 *   Option 3: ConcurrentLinkedQueue / ConcurrentHashMap
 *   - Fine-grained locking or lock-free algorithms for high concurrency.
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Shared shopping cart: multiple requests adding items concurrently need a thread-safe list.
 * - Event listeners: CopyOnWriteArrayList for rarely-changing listener registrations.
 * - Aggregating results: multiple threads collecting results into a synchronized list.
 * - Metrics collection: concurrent counters/lists tracking request stats across threads.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class SynchronizedCollection {

    public static void main(String[] args) throws InterruptedException {
//        List<Integer> list = new ArrayList<>();

        List<Integer> list = Collections.synchronizedList(new ArrayList<>()); // list size : 20000

        Thread one = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                list.add(i);
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                list.add(i);
            }
        });

        one.start();
        two.start();

        one.join();
        two.join();

        System.out.println("list size : " + list.size()); // list size : 18033
    }


}
