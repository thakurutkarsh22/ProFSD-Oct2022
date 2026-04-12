package LLD.Topics.MultiThreading.ConcurrentCollection;


import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     CopyOnWriteArrayList (COWAL)                       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is CopyOnWriteArrayList?
 * ============================================================================
 *
 * A thread-safe variant of ArrayList where every WRITE operation (add, set,
 * remove) creates a brand-new copy of the underlying array. READ operations
 * (get, iterate) work on the old snapshot -- no locking needed for reads.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   ── Initial state ──
 *
 *   CopyOnWriteArrayList
 *   ┌─────────────────────────────────────────────┐
 *   │  internal array (volatile reference)         │
 *   │  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┐     │
 *   │  │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │     │
 *   │  └───┴───┴───┴───┴───┴───┴───┴───┴───┘     │
 *   │       ▲                                      │
 *   │       │ readers see this snapshot             │
 *   └───────┼──────────────────────────────────────┘
 *           │
 *   Reader threads (lock-free, fast)
 *
 *   ── When a WRITE happens (e.g. set(5, 8)) ──
 *
 *   Writer thread:
 *   1. Acquires internal ReentrantLock
 *   2. Copies entire array -> new array
 *   3. Modifies new array at index 5
 *   4. Swaps the volatile reference to point to new array
 *   5. Releases lock
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │                                                                 │
 *   │  OLD array (readers still using this snapshot)                  │
 *   │  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┐                       │
 *   │  │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │  ◄── existing readers│
 *   │  └───┴───┴───┴───┴───┴───┴───┴───┴───┘       see this         │
 *   │                                                                 │
 *   │  NEW array (copy + modification)                                │
 *   │  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┐                       │
 *   │  │ 0 │ 0 │ 0 │ 0 │ 0 │ 8 │ 0 │ 0 │ 0 │  ◄── new reference   │
 *   │  └───┴───┴───┴───┴───┴───┴───┴───┴───┘       points here      │
 *   │                             ▲                                   │
 *   │                             │ set(5, 8) applied                 │
 *   │                                                                 │
 *   │  Next read will see the new array.                              │
 *   │  Old array is garbage collected when no reader references it.   │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Read vs Write -- contention model
 * ============================================================================
 *
 *   Operation     Locking                    Speed
 *   ───────────   ────────────────────────   ──────────────────────────
 *   get(i)        NO lock (volatile read)    Very fast, O(1)
 *   iterator()    NO lock (snapshot)         Very fast, never throws CME
 *   set(i, val)   ReentrantLock + array copy Slow, O(n) -- copies entire array
 *   add(val)      ReentrantLock + array copy Slow, O(n)
 *   remove(i)     ReentrantLock + array copy Slow, O(n)
 *
 *   Thread 1 (read)               Thread 2 (write)           Thread 3 (read)
 *   ───────────────                ──────────────────         ───────────────
 *   reads old array               lock()                     reads old array
 *   (no lock needed)              copy array                 (no lock needed)
 *                                 modify copy
 *                                 swap reference
 *                                 unlock()
 *   next read sees new array                                 next read sees new array
 *
 *   KEY: Readers NEVER block. Writers block other WRITERS only.
 *
 * ============================================================================
 * 4. When to use vs when NOT to use
 * ============================================================================
 *
 *   GOOD (reads >> writes):                    BAD (frequent writes):
 *   ┌─────────────────────────────┐            ┌─────────────────────────────┐
 *   │ - Event listener lists      │            │ - Frequently updated lists  │
 *   │ - Configuration snapshots   │            │ - Large arrays with writes  │
 *   │ - Observer pattern          │            │ - Producer-consumer queues  │
 *   │ - Routing tables            │            │   (use BlockingQueue)       │
 *   │ - Rare writes, many reads   │            │ - Write-heavy workloads     │
 *   └─────────────────────────────┘            └─────────────────────────────┘
 *
 * ============================================================================
 * 5. CopyOnWriteArrayList vs synchronized ArrayList vs ArrayList
 * ============================================================================
 *
 *   Feature                  ArrayList         synchronizedList      CopyOnWriteArrayList
 *   ──────────────────────   ──────────────    ──────────────────    ─────────────────────
 *   Thread-safe?             No                Yes (one lock)        Yes (copy-on-write)
 *   Read locking             N/A               Acquires lock         No lock (volatile)
 *   Write locking            N/A               Acquires lock         Lock + full array copy
 *   Iterator safety          fail-fast (CME)   fail-fast (CME)       Snapshot (no CME ever)
 *   Best when                Single thread     Mixed reads/writes    Reads >> writes
 *   Write cost               O(1) amortized    O(1) amortized        O(n) -- copies array
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - Event listeners: Swing/JavaFX listener lists that are read on every event but rarely modified.
 * - Observer pattern: list of subscribers iterated on every notification, rarely add/remove.
 * - Configuration: cached config values read by all threads, updated only on reload.
 * - Routing tables: read on every request, updated only when routes change.
 * - Whitelist/blacklist: checked on every request, modified only by admin operations.
 *
 * ============================================================================
 * 7. Interview one-liner
 * ============================================================================
 * CopyOnWriteArrayList: every write copies the entire array (O(n)), but reads are lock-free
 * snapshots (O(1)). Ideal when reads vastly outnumber writes. Iterators never throw
 * ConcurrentModificationException.
 *
 * ============================================================================
 * 8. Code demo below
 * ============================================================================
 * 3 writer threads randomly set values in a 9-element COWAL. 1 reader thread prints
 * the list every second. No ConcurrentModificationException despite concurrent reads
 * and writes -- readers always see a consistent snapshot.
 */
public class COWADemo {
    public static void main(String[] args) {
        Simulation simulation = new Simulation();
        simulation.simulate();
    }
}

class Simulation {
    private final List<Integer> list;

    public Simulation() {
        this.list = new CopyOnWriteArrayList<>();
        this.list.addAll(Arrays.asList(0,0,0,0,0,0,0,0,0));
    }

    public void simulate() {
        Thread one = new Thread(new WriteTask(list));
        Thread two = new Thread(new WriteTask(list));
        Thread three = new Thread(new WriteTask(list));
        Thread four = new Thread(new ReadTask(list));

        one.start();
        two.start();
        three.start();
        four.start();

    }


}

class ReadTask implements Runnable {

    private final List<Integer> list;

    public ReadTask(List<Integer> list) {
        this.list = list;
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println(list);
        }
    }
}

class WriteTask implements Runnable {

    private final List<Integer> list;
    private Random random;

    public WriteTask(List<Integer> list) {
        this.list = list;
        this.random = new Random();
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            list.set(random.nextInt(list.size()), random.nextInt(10));
        }
    }
}

/**
 * [0, 0, 0, 0, 0, 0, 0, 0, 0]
 * [0, 0, 0, 0, 0, 8, 0, 0, 0]
 * [0, 2, 0, 0, 4, 8, 5, 0, 0]
 * [3, 2, 4, 0, 4, 8, 0, 0, 0]
 * [1, 2, 6, 0, 4, 8, 0, 0, 0]
 * [4, 2, 6, 7, 4, 8, 0, 0, 0]
 * [4, 2, 6, 7, 4, 8, 0, 0, 0]
 * [1, 2, 6, 7, 4, 5, 0, 0, 1]
 * [7, 2, 6, 7, 3, 5, 0, 0, 1]
 */
