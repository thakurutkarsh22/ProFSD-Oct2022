package LLD.LldQuestions.LRUCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║     LRU Cache — Hybrid Strategy (Lazy + Background Cleanup)            ║
 * ║                    with Thread Safety                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Prerequisites: Read LRUCache.java (basic) and LRUCacheWithTTL.java first.
 * This file builds on both and only explains what's NEW.
 *
 * ============================================================================
 * 1. WHAT IS THE HYBRID STRATEGY?
 * ============================================================================
 *
 * The TTL version used LAZY eviction only — expired entries linger in memory
 * until someone tries to get() them. The Hybrid approach adds a BACKGROUND
 * CLEANUP THREAD that periodically sweeps the cache and removes stale entries.
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                     │
 *   │  Lazy Eviction (from LRUCacheWithTTL)                               │
 *   │    On every get() → check if expired → remove if stale              │
 *   │    ✅ Catches stale entries the moment they're accessed              │
 *   │    ❌ Entries nobody accesses sit forever, wasting memory            │
 *   │                                                                     │
 *   │                        +                                            │
 *   │                                                                     │
 *   │  Background Cleanup Thread (NEW)                                    │
 *   │    Every N seconds → walk DLL from TAIL → remove all expired        │
 *   │    ✅ Reclaims memory from entries nobody is accessing              │
 *   │    ❌ Needs thread safety (multiple threads touch the DLL/map)      │
 *   │                                                                     │
 *   │                        =                                            │
 *   │                                                                     │
 *   │  HYBRID: Best of both worlds                                        │
 *   │    - get/put never return stale data (lazy check)                   │
 *   │    - Memory is reclaimed even for forgotten keys (background)       │
 *   │    - Thread-safe via ReentrantReadWriteLock                         │
 *   │                                                                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 2. WHY DO WE NEED THREAD SAFETY NOW?
 * ============================================================================
 *
 *   In LRUCacheWithTTL, only the caller's thread touches the DLL and map.
 *   Now we have TWO threads accessing the same data:
 *
 *     Thread 1 (Caller):     get() / put()  → reads/writes DLL + map
 *     Thread 2 (Background): cleanup()      → reads/writes DLL + map
 *
 *   Without synchronization, they can corrupt the DLL pointers:
 *
 *     Thread 1: removeNode(A)        Thread 2: removeNode(B)
 *         │                              │
 *         ▼                              ▼
 *     A.prev.next = A.next          B.prev.next = B.next
 *         │                              │
 *     If A and B are neighbors, both threads are re-linking
 *     the SAME pointers at the same time → CORRUPTION!
 *
 * ============================================================================
 * 3. ReentrantReadWriteLock — HOW IT WORKS
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                     │
 *   │  ReentrantReadWriteLock has TWO locks inside:                       │
 *   │                                                                     │
 *   │  READ LOCK  (shared)                                                │
 *   │    - Multiple threads can hold the read lock at the same time       │
 *   │    - Blocks only if a writer is active                              │
 *   │    - Use for: operations that ONLY read (e.g., get with no move)    │
 *   │                                                                     │
 *   │  WRITE LOCK (exclusive)                                             │
 *   │    - Only ONE thread can hold the write lock                        │
 *   │    - Blocks all readers and other writers                           │
 *   │    - Use for: get() (reads + moves node), put(), cleanup()          │
 *   │                                                                     │
 *   │  ────────────────────────────────────────────────────               │
 *   │                                                                     │
 *   │  Why not just use synchronized?                                     │
 *   │    - synchronized is simpler but allows ONLY ONE thread at a time   │
 *   │    - ReadWriteLock allows multiple concurrent readers               │
 *   │    - In a cache, reads are far more frequent than writes            │
 *   │    - ReadWriteLock gives better throughput under read-heavy load    │
 *   │                                                                     │
 *   │  NOTE: In our LRU Cache, get() also WRITES (moves node to head),  │
 *   │  so we use the WRITE lock for get() too. If we skipped the move    │
 *   │  (like a peek), we could use the read lock for that.               │
 *   │                                                                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 4. WHAT'S NEW vs LRUCacheWithTTL
 * ============================================================================
 *
 *   LRUCacheWithTTL              LRUCacheHybrid (this file)
 *   ──────────────────────────   ──────────────────────────────────────
 *   No threads                   ScheduledExecutorService runs cleanup
 *   No locks                     ReentrantReadWriteLock on every method
 *   Expired entries linger       Background thread sweeps them out
 *   No shutdown needed           Must call shutdown() to stop thread
 *   ~50 lines of logic           ~80 lines of logic (still simple)
 *
 * ============================================================================
 * 5. ARCHITECTURE DIAGRAM
 * ============================================================================
 *
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │                        LRUCacheHybrid                              │
 *   │                                                                     │
 *   │  ┌─────────────────────────────────────────────────────────┐       │
 *   │  │              ReentrantReadWriteLock                      │       │
 *   │  │         (guards all access to DLL + map)                │       │
 *   │  └─────────────────────────────────────────────────────────┘       │
 *   │          ▲                                    ▲                     │
 *   │          │                                    │                     │
 *   │   Caller Thread                      Background Thread             │
 *   │   ┌──────────────┐                  ┌───────────────────┐          │
 *   │   │  get() / put()│                 │  cleanup() every   │         │
 *   │   │  writeLock()  │                  │  N seconds         │         │
 *   │   │  lazy check   │                  │  writeLock()       │         │
 *   │   └──────────────┘                  │  walk DLL, remove  │         │
 *   │          │                           │  expired nodes     │         │
 *   │          ▼                           └───────────────────┘          │
 *   │  ┌──────────────────────────────────────────┐                      │
 *   │  │   HashMap<Key, Node>  +  Doubly LL       │                      │
 *   │  │   HEAD ⟷ [MRU] ⟷ [..] ⟷ [LRU] ⟷ TAIL  │                      │
 *   │  └──────────────────────────────────────────┘                      │
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. FLOW DIAGRAM — BACKGROUND CLEANUP THREAD
 * ============================================================================
 *
 *   cleanup() — runs every N seconds
 *      │
 *      ▼
 *   Acquire WRITE lock
 *      │
 *      ▼
 *   Start at TAIL.prev (the oldest / LRU end)
 *      │
 *      ▼
 *   ┌─────────────────────────┐
 *   │ Is current node == HEAD? │──── YES ──→ Done (reached the start)
 *   └─────────┬───────────────┘                  │
 *             NO                                  ▼
 *              │                            Release WRITE lock
 *              ▼
 *   ┌──────────────────────┐
 *   │ Is node expired?     │──── NO ──→ Move to node.prev (keep walking)
 *   └─────────┬────────────┘                     │
 *            YES                                  │
 *              │                                  │
 *              ▼                                  │
 *   Save node.prev (before removing)             │
 *              │                                  │
 *              ▼                                  │
 *   removeNode(node) from DLL                    │
 *              │                                  │
 *              ▼                                  │
 *   map.remove(node.key)                         │
 *              │                                  │
 *              ▼                                  │
 *   Log: "Cleaned up key X"                      │
 *              │                                  │
 *              └──────────── continue ────────────┘
 *
 *   Why walk from TAIL towards HEAD?
 *   → Oldest entries are near the TAIL. They're most likely to be expired.
 *     We get the best bang-for-buck by starting there.
 *
 * ============================================================================
 * 7. CODE IMPLEMENTATION
 * ============================================================================
 */
public class LRUCacheHybrid {

    // ─── Node (same as LRUCacheWithTTL) ───────────────────────────────────

    static class Node {
        int key;
        int value;
        long expiryTime;
        Node prev;
        Node next;

        Node(int key, int value, long expiryTime) {
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────

    private final int capacity;
    private final long defaultTTLMillis;
    private final Map<Integer, Node> map;
    private final Node head;
    private final Node tail;

    // NEW: Thread safety + background cleanup
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService cleanupExecutor;

    // ─── Constructor ──────────────────────────────────────────────────────

    public LRUCacheHybrid(int capacity, long defaultTTLMillis, long cleanupIntervalMs) {
        this.capacity = capacity;
        this.defaultTTLMillis = defaultTTLMillis;
        this.map = new HashMap<>();

        this.head = new Node(0, 0, 0);
        this.tail = new Node(0, 0, 0);
        head.next = tail;
        tail.prev = head;

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LRU-Cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpired,
                cleanupIntervalMs,
                cleanupIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    // ─── Helper ───────────────────────────────────────────────────────────

    private boolean isExpired(Node node) {
        return System.currentTimeMillis() > node.expiryTime;
    }

    // ─── get(key) — write lock because we move the node ──────────────────

    public int get(int key) {
        lock.writeLock().lock();
        try {
            if (!map.containsKey(key)) {
                return -1;
            }

            Node node = map.get(key);

            if (isExpired(node)) {
                removeNode(node);
                map.remove(key);
                return -1;
            }

            moveToHead(node);
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── put(key, value) ─────────────────────────────────────────────────

    public void put(int key, int value) {
        put(key, value, defaultTTLMillis);
    }

    public void put(int key, int value, long ttlMillis) {
        lock.writeLock().lock();
        try {
            long expiryTime = System.currentTimeMillis() + ttlMillis;

            if (map.containsKey(key)) {
                Node node = map.get(key);
                node.value = value;
                node.expiryTime = expiryTime;
                moveToHead(node);
            } else {
                if (map.size() == capacity) {
                    evictLRU();
                }

                Node newNode = new Node(key, value, expiryTime);
                addAfterHead(newNode);
                map.put(key, newNode);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Background cleanup — walks DLL from TAIL, removes expired ────────
    //
    //  This is the KEY NEW METHOD. Runs on a background thread every N ms.
    //  Walks from TAIL towards HEAD (oldest entries first) and removes
    //  any expired nodes from both the DLL and the HashMap.

    private void cleanupExpired() {
        lock.writeLock().lock();
        try {
            int removed = 0;
            Node current = tail.prev;

            while (current != head) {
                Node prev = current.prev;

                if (isExpired(current)) {
                    map.remove(current.key);
                    removeNode(current);
                    removed++;
                    System.out.println("    [Cleanup Thread] Removed expired key: "
                            + current.key + " (was " + current.value + ")");
                }

                current = prev;
            }

            if (removed > 0) {
                System.out.println("    [Cleanup Thread] Swept " + removed
                        + " expired entries. Cache size: " + map.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── DLL Helpers (identical to previous versions) ─────────────────────

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void addAfterHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addAfterHead(node);
    }

    private void evictLRU() {
        Node lruNode = tail.prev;
        removeNode(lruNode);
        map.remove(lruNode.key);
    }

    // ─── Shutdown — MUST call this to stop the background thread ──────────

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────

    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void printState() {
        lock.readLock().lock();
        try {
            long now = System.currentTimeMillis();
            System.out.print("  Cache: HEAD ⟷ ");
            Node curr = head.next;
            while (curr != tail) {
                long remaining = curr.expiryTime - now;
                String status = remaining > 0 ? (remaining + "ms left") : "EXPIRED";
                System.out.print("[" + curr.key + ":" + curr.value
                        + " (" + status + ")] ⟷ ");
                curr = curr.next;
            }
            System.out.println("TAIL");
        } finally {
            lock.readLock().unlock();
        }
    }

    // ============================================================================
    // 8. DEMO
    // ============================================================================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  LRU Cache Hybrid — Lazy + Background Cleanup (capacity=3)  ║");
        System.out.println("║  TTL = 2s, Cleanup runs every 1s                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        //                          capacity=3, TTL=2s, cleanup every 1s
        LRUCacheHybrid cache = new LRUCacheHybrid(3, 2000, 1000);

        // ── Fill the cache ──
        System.out.println("Step 1: Insert 3 entries (TTL = 2s each)");
        cache.put(1, 10);
        cache.put(2, 20);
        cache.put(3, 30);
        cache.printState();
        System.out.println();

        // ── Immediate access works ──
        System.out.println("Step 2: get(1) immediately → " + cache.get(1));
        System.out.println("  → Alive! TTL hasn't passed.");
        System.out.println();

        // ── Wait for entries to expire + cleanup to run ──
        System.out.println("Step 3: Sleeping 3 seconds...");
        System.out.println("  → Entries expire after 2s");
        System.out.println("  → Cleanup thread runs at 1s, 2s, 3s...");
        System.out.println("  (Watch the cleanup thread logs below)");
        System.out.println();
        Thread.sleep(3000);
        System.out.println();

        // ── After cleanup, cache should be empty ──
        System.out.println("Step 4: After 3s — cache size: " + cache.size());
        cache.printState();
        System.out.println("  → Background thread cleaned up all expired entries!");
        System.out.println("  → Unlike LRUCacheWithTTL, we didn't need to call get()");
        System.out.println("    for them to be removed. The cleanup thread did it.");
        System.out.println();

        // ── Insert with longer TTL to show it still works as normal LRU ──
        System.out.println("Step 5: put(4, 40, 10s) + put(5, 50, 10s) + put(6, 60, 10s)");
        cache.put(4, 40, 10000);
        cache.put(5, 50, 10000);
        cache.put(6, 60, 10000);
        cache.printState();
        System.out.println();

        System.out.println("Step 6: put(7, 70, 10s) → cache full, LRU eviction kicks in");
        cache.put(7, 70, 10000);
        cache.printState();
        System.out.println("  → key 4 was evicted (LRU), NOT because of TTL");
        System.out.println();

        System.out.println("Step 7: get(4) → " + cache.get(4));
        System.out.println("  → key 4 was LRU-evicted, returns -1");
        System.out.println();

        // ── Lazy eviction still works too ──
        System.out.println("Step 8: put(8, 80, 500) → insert with very short TTL (0.5s)");
        cache.put(8, 80, 500);
        cache.printState();
        System.out.println();

        Thread.sleep(600);
        System.out.println("Step 9: get(8) after 0.6s → " + cache.get(8));
        System.out.println("  → EXPIRED via lazy eviction (checked on get, before cleanup ran)");
        System.out.println();

        cache.printState();
        System.out.println("  Cache size: " + cache.size());

        // ── Clean shutdown ──
        cache.shutdown();
        System.out.println();
        System.out.println("Cache shut down. Background thread stopped.");
    }
}

/*
 * ============================================================================
 * 9. EVOLUTION OF ALL THREE VERSIONS (Summary)
 * ============================================================================
 *
 *   Feature              LRUCache      LRUCacheWithTTL     LRUCacheHybrid
 *   ───────────────────  ────────────  ──────────────────  ──────────────────
 *   Data structures      HashMap+DLL   HashMap+DLL         HashMap+DLL
 *   Eviction trigger     Cache full    Cache full + TTL    Cache full + TTL
 *   Lazy TTL check       ✗             ✓ on get()          ✓ on get()
 *   Background cleanup   ✗             ✗                   ✓ every N seconds
 *   Thread safe          ✗             ✗                   ✓ ReadWriteLock
 *   Needs shutdown()     ✗             ✗                   ✓ (stops thread)
 *   Complexity           O(1)          O(1)                O(1) amortized
 *   Interview level      Easy          Easy-Medium         Medium
 *
 * ============================================================================
 * 10. DESIGN PATTERNS USED (cumulative across all 3 files)
 * ============================================================================
 *
 *   Pattern                Where                    Why
 *   ─────────────────────  ─────────────────────    ──────────────────────────
 *   Sentinel/Dummy Nodes   HEAD + TAIL in DLL       No null checks
 *   Composition            HashMap + DLL            Each does what it's best at
 *   Facade                 get() + put() API        Hides internals
 *   Strategy (extension)   EvictionPolicy iface     Swap LRU/LFU/FIFO
 *   ── NEW in this file ──────────────────────────────────────────────────────
 *   Producer-Consumer      Cleanup thread + lock    Background work on shared data
 *   Resource Management    shutdown() method        Graceful thread lifecycle
 *   Read-Write Lock        RW lock on all ops       Concurrent reads, exclusive writes
 *
 * ============================================================================
 * 11. INTERVIEW TIP — HOW TO EXPLAIN THE HYBRID APPROACH
 * ============================================================================
 *
 * "The hybrid approach combines lazy eviction — checking TTL on every get() —
 *  with a background ScheduledExecutorService that sweeps expired entries every
 *  N seconds. This ensures stale data is never returned AND memory is reclaimed
 *  even for keys nobody accesses. I use a ReentrantReadWriteLock so the cleanup
 *  thread and caller threads don't corrupt the shared DLL. The read lock is used
 *  for size() and printState(), the write lock for get(), put(), and cleanup()."
 *
 * ============================================================================
 * 12. WHAT CAN STILL BE IMPROVED
 * ============================================================================
 *
 *   (a) Lock-free cleanup using ConcurrentLinkedDeque + ConcurrentHashMap
 *       → Avoids blocking get/put during cleanup. Much harder to implement.
 *
 *   (b) Segmented locking (like ConcurrentHashMap's approach)
 *       → Partition the cache into N segments, each with its own lock.
 *       → Reduces contention under very high concurrency.
 *
 *   (c) Cleanup only walks the expired tail portion, not the entire DLL
 *       → If entries are inserted in TTL order, once you hit a non-expired
 *         node from the tail side, you can stop — everything before it is alive.
 *       → Our implementation already walks from tail, but it checks all nodes
 *         since per-entry TTLs can be different.
 *
 *   (d) Metrics: track cleanup count, average cleanup duration, lock wait time.
 */
