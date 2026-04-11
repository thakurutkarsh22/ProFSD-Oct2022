package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                        ConcurrentHashMap                                ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. Introduction
 * ============================================================================
 *
 * What is ConcurrentMap?
 * - A Map that is safe for concurrent access from multiple threads without external synchronization.
 * - Defines atomic compound operations: putIfAbsent, remove(k,v), replace, computeIfAbsent, etc.
 *
 * Why do we need it?
 * - HashMap is not thread-safe; concurrent puts can corrupt the internal table (infinite loops in
 *   Java 7, data loss in Java 8+). Collections.synchronizedMap wraps every call in one big lock —
 *   safe, but serializes all threads (low throughput).
 *
 * ============================================================================
 * 2. ConcurrentMap implementations
 * ============================================================================
 *
 * - ConcurrentHashMap     — the workhorse; hash table with fine-grained locking (see below).
 * - ConcurrentSkipListMap — sorted (NavigableMap), lock-free reads, log(n) put/get.
 * - ConcurrentLinkedHashMap — (Guava/Caffeine) insertion-ordered or access-ordered + eviction.
 * - ConcurrentNavigableMap — interface for sorted concurrent maps (ConcurrentSkipListMap implements it).
 *
 * ============================================================================
 * 3. How ConcurrentHashMap works internally
 * ============================================================================
 *
 * ── High-level structure (Java 8+) ──
 *
 *   ConcurrentHashMap
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │  Node<K,V>[] table    (the bucket array, power-of-two size)   │
 *   │                                                                │
 *   │  bucket 0    bucket 1    bucket 2   ...   bucket N-1          │
 *   │  ┌──────┐   ┌──────┐   ┌──────┐         ┌──────┐            │
 *   │  │ Node │   │ null │   │ Node │         │ Node │            │
 *   │  │  ↓   │   │      │   │  ↓   │         │  ↓   │            │
 *   │  │ Node │   │      │   │ Node │         │ Tree │            │
 *   │  │  ↓   │   │      │   │      │         │ Node │            │
 *   │  │ null │   │      │   │      │         │      │            │
 *   │  └──────┘   └──────┘   └──────┘         └──────┘            │
 *   │                                                                │
 *   │  Each bucket: linked list (< 8 nodes) or red-black tree (≥ 8) │
 *   │  Lock granularity: per-bucket (first node of the bin)          │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * In Java 7, ConcurrentHashMap used "segments" (an array of smaller HashMaps, each with its own
 * ReentrantLock). Java 8+ replaced that with a simpler design: the table is one flat array and
 * each bucket's head node is used as the lock target via synchronized(headNode).
 *
 * ── Adding an element (put) ──
 *
 *   Thread calls put(key, value)
 *       │
 *       ▼
 *   1. Hash and determine bucket
 *      hash = spread(key.hashCode())
 *      bucketIndex = hash & (table.length - 1)
 *       │
 *       ▼
 *   2. Acquire lock
 *      - If the bucket is empty: CAS (compare-and-swap) the new Node into the slot (no lock needed).
 *      - If the bucket is non-empty: synchronized (headNode of that bucket) { ... }
 *      - Other buckets are NOT locked — threads hitting different buckets proceed in parallel.
 *       │
 *       ▼
 *   3. Insertion in bucket
 *      - Walk the linked list (or tree) looking for existing key.
 *      - If found → replace value.
 *      - If not found → append new Node at the end (or insert into tree).
 *      - If chain length ≥ 8 → treeify (convert to red-black tree for O(log n) lookup).
 *       │
 *       ▼
 *   4. Release lock
 *      - Exit synchronized block → only that one bucket was locked.
 *      - Check if table needs resizing (addCount); resize cooperatively if needed.
 *
 * ── Fetching an element (get) ──
 *
 *   Thread calls get(key)
 *       │
 *       ▼
 *   1. Hash and determine bucket
 *      Same hash spread as put.
 *       │
 *       ▼
 *   2. NO lock acquired (volatile read of the table slot)
 *      - Node.val and Node.next are volatile; reads see the latest write.
 *       │
 *       ▼
 *   3. Search in bucket
 *      - Walk the list/tree comparing hash + key.equals().
 *      - Return value if found, null if not.
 *       │
 *       ▼
 *   4. NO lock to release
 *      - get() is entirely lock-free (only volatile reads).
 *
 * ── Concurrency diagram (two threads, different buckets) ──
 *
 *   Thread A: put("foo", 1)             Thread B: put("bar", 2)
 *       │                                    │
 *       ▼                                    ▼
 *   hash("foo") → bucket 3              hash("bar") → bucket 7
 *       │                                    │
 *       ▼                                    ▼
 *   synchronized(bucket 3 head)          synchronized(bucket 7 head)
 *   │  insert "foo"                      │  insert "bar"
 *   │  ← runs in PARALLEL →             │  ← runs in PARALLEL →
 *   unlock bucket 3                      unlock bucket 7
 *
 *   Thread C: get("foo")
 *       │
 *       ▼
 *   volatile read bucket 3  ──── NO lock at all, runs concurrently with A and B
 *
 * ── Concurrency diagram (two threads, SAME bucket) ──
 *
 *   Thread A: put("foo", 1)             Thread B: put("baz", 2)
 *       │                                    │
 *       ▼                                    ▼
 *   hash → bucket 3                     hash → bucket 3  (same bucket!)
 *       │                                    │
 *       ▼                                    ▼
 *   synchronized(bucket 3 head)          BLOCKED — waiting for A to release
 *   │  insert "foo"                          │
 *   unlock bucket 3                          ▼
 *                                        synchronized(bucket 3 head)  ← now A is done
 *                                        │  insert "baz"
 *                                        unlock bucket 3
 *
 * ============================================================================
 * 4. How many locks are there in ConcurrentHashMap?
 * ============================================================================
 *
 * There is NO fixed number of locks. Locking is per-bucket:
 *
 * Java 8+:
 * - The table is a Node[] (e.g. 16 slots by default, grows as needed).
 * - Each non-empty bucket slot's head node is the lock target (synchronized(headNode)).
 * - If the table has 16 buckets → up to 16 independent lock targets.
 * - If the table resizes to 64 buckets → up to 64.
 * - The number of "locks" scales with the table size.
 *
 * Special cases:
 * - Empty bucket  → no lock; CAS (compare-and-swap) atomically places the first node (lock-free).
 * - Non-empty bucket → synchronized on the first node of that bucket. Only threads hitting
 *   that same bucket contend.
 * - get()  → ZERO locks. Uses volatile reads of Node.val and Node.next. Entirely lock-free.
 *
 * Java 7 (for comparison):
 * - Used a fixed number of Segment objects (default 16, configurable via concurrencyLevel).
 * - Each Segment was a mini-HashMap with its own ReentrantLock.
 * - So Java 7 had exactly 16 locks by default (hard ceiling on parallelism).
 * - Java 8+ dropped segments entirely; parallelism scales with the table size.
 *
 * Summary:
 *   Java 7  → fixed 16 Segment locks (configurable)
 *   Java 8+ → one lock per non-empty bucket (grows with table), CAS for empty, no lock for reads
 *
 * ── Can another thread READ from a bucket that is locked for writing? ──
 *
 * YES. get() is entirely lock-free — it uses volatile reads of Node.val and Node.next.
 * Even if Thread A holds synchronized(bucket1_head) while doing a put(), Thread B can
 * get() from the same bucket 1 at the same time without blocking.
 *
 *   Thread A (put "foo" → bucket 1)         Thread B (get "bar" from bucket 1)
 *   ─────────────────────────────            ─────────────────────────────────
 *   synchronized(bucket1_head)               hash("bar") → bucket 1
 *   │  walk chain, insert "foo"              │  walk chain via volatile next
 *   │  set node.val (volatile write)         │  read node.val (volatile read)
 *   unlock                                   return value  ← NO lock needed!
 *
 * ── Can another thread WRITE to a bucket that is already locked for writing? ──
 *
 * NO. put() on a non-empty bucket uses synchronized(headNode). If two threads both
 * hash to the same bucket, the second thread blocks until the first releases the lock.
 *
 *   Thread 1 (put "foo" → bucket 1)         Thread 2 (put "baz" → bucket 1)
 *   ─────────────────────────────            ─────────────────────────────────
 *   synchronized(bucket1_head) ← acquired   synchronized(bucket1_head) ← BLOCKED!
 *   │  walk chain                                │  waiting...
 *   │  insert "foo"                              │  waiting...
 *   unlock bucket 1                              │  waiting...
 *                                            synchronized(bucket1_head) ← now acquired
 *                                            │  walk chain
 *                                            │  insert "baz"
 *                                            unlock bucket 1
 *
 * ── Quick reference: contention matrix ──
 *
 *   Operation        Same bucket              Different buckets
 *   ────────────     ──────────────────────   ──────────────────
 *   put  + put       Serialized (one waits)   Fully parallel
 *   put  + get       Parallel (get lock-free) Fully parallel
 *   get  + get       Fully parallel           Fully parallel
 *
 * This is the key advantage over Hashtable / synchronizedMap — those lock the ENTIRE
 * map for every operation. ConcurrentHashMap only serializes writes to the SAME bucket.
 *
 * ============================================================================
 * 5. Why ConcurrentHashMap vs synchronized HashMap vs Hashtable
 * ============================================================================
 *
 *   Feature                  Hashtable / synchronizedMap    ConcurrentHashMap
 *   ──────────────────────   ────────────────────────────   ──────────────────────
 *   Lock granularity         ONE lock for entire map        per-bucket (Java 8+)
 *   get() locking            acquires lock                  lock-free (volatile)
 *   Throughput               low under contention           high (parallel buckets)
 *   Null keys/values?        Hashtable: no; syncMap: yes    NO (neither key nor value)
 *   Atomic compounds         no                             putIfAbsent, computeIfAbsent, merge, etc.
 *   Iterators                fail-fast (CME)                weakly consistent (no CME)
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - In-memory cache: computeIfAbsent for atomic "get or compute" (e.g., config/metadata cache).
 * - Concurrent counters: merge() or compute() to safely increment per-key metrics.
 * - Session store: web servers store user sessions in a ConcurrentHashMap.
 * - Deduplication: check-and-insert in parallel without external locking.
 * - Connection registry: track active WebSocket/TCP connections by ID.
 *
 * ============================================================================
 * 7. Interview one-liner
 * ============================================================================
 * ConcurrentHashMap (Java 8+): flat Node[] table, per-bucket synchronized + CAS for empty slots,
 * lock-free volatile reads for get(). Multiple threads can put() in parallel as long as they hit
 * different buckets. get() never blocks.
 *
 * ============================================================================
 * 8. Code demo below
 * ============================================================================
 * This demo matches the course video: 10 threads, each hitting the same key 3 times, showing
 * that the first access triggers compute() and subsequent accesses return the cached value.
 *
 * Note: the check-then-act pattern in getCachedValue (get → null check → compute → put) is
 * NOT atomic. Two threads with the same key can both see null and compute twice. For a truly
 * atomic "compute if absent", use: cache.computeIfAbsent(key, k -> compute(k)).
 */
public class ConcurrentCache {
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            final int threadNum = i;

            new Thread(() -> {
                String key = "Key @ " + threadNum;
                for (int j = 0; j < 3; j++) {
                    String value = getCachedValue(key);
                    System.out.println("Thread " + Thread.currentThread().getName()
                            + " : Key = " + key + " value = " + value);
                }
            }).start();
        }
    }

    private static String getCachedValue(final String key) {
        String value = cache.get(key);

        if (value == null) {
            value = compute(key);
            cache.put(key, value);
        }

        return value;
    }

    private static String compute(final String key) {
        System.out.println(key + " not present in the cache, so going to compute!");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "Value for " + key;
    }
}

/**
 * Key @ 4 not present in the cache, so going to compute!
 * Key @ 6 not present in the cache, so going to compute!
 * Key @ 9 not present in the cache, so going to compute!
 * Key @ 8 not present in the cache, so going to compute!
 * Key @ 5 not present in the cache, so going to compute!
 * Key @ 1 not present in the cache, so going to compute!
 * Key @ 7 not present in the cache, so going to compute!
 * Key @ 3 not present in the cache, so going to compute!
 * Key @ 2 not present in the cache, so going to compute!
 * Key @ 0 not present in the cache, so going to compute!
 * Thread Thread-4 : Key = Key @ 4 value = Value for Key @ 4
 * Thread Thread-2 : Key = Key @ 2 value = Value for Key @ 2
 * Thread Thread-1 : Key = Key @ 1 value = Value for Key @ 1
 * Thread Thread-2 : Key = Key @ 2 value = Value for Key @ 2
 * Thread Thread-2 : Key = Key @ 2 value = Value for Key @ 2
 * Thread Thread-4 : Key = Key @ 4 value = Value for Key @ 4
 * Thread Thread-0 : Key = Key @ 0 value = Value for Key @ 0
 * Thread Thread-8 : Key = Key @ 8 value = Value for Key @ 8
 * Thread Thread-5 : Key = Key @ 5 value = Value for Key @ 5
 * Thread Thread-9 : Key = Key @ 9 value = Value for Key @ 9
 * Thread Thread-6 : Key = Key @ 6 value = Value for Key @ 6
 * Thread Thread-9 : Key = Key @ 9 value = Value for Key @ 9
 * Thread Thread-5 : Key = Key @ 5 value = Value for Key @ 5
 * Thread Thread-8 : Key = Key @ 8 value = Value for Key @ 8
 * Thread Thread-8 : Key = Key @ 8 value = Value for Key @ 8
 * Thread Thread-0 : Key = Key @ 0 value = Value for Key @ 0
 * Thread Thread-0 : Key = Key @ 0 value = Value for Key @ 0
 * Thread Thread-4 : Key = Key @ 4 value = Value for Key @ 4
 * Thread Thread-3 : Key = Key @ 3 value = Value for Key @ 3
 * Thread Thread-1 : Key = Key @ 1 value = Value for Key @ 1
 * Thread Thread-3 : Key = Key @ 3 value = Value for Key @ 3
 * Thread Thread-5 : Key = Key @ 5 value = Value for Key @ 5
 * Thread Thread-9 : Key = Key @ 9 value = Value for Key @ 9
 * Thread Thread-7 : Key = Key @ 7 value = Value for Key @ 7
 * Thread Thread-6 : Key = Key @ 6 value = Value for Key @ 6
 * Thread Thread-7 : Key = Key @ 7 value = Value for Key @ 7
 * Thread Thread-3 : Key = Key @ 3 value = Value for Key @ 3
 * Thread Thread-1 : Key = Key @ 1 value = Value for Key @ 1
 * Thread Thread-7 : Key = Key @ 7 value = Value for Key @ 7
 * Thread Thread-6 : Key = Key @ 6 value = Value for Key @ 6
 */