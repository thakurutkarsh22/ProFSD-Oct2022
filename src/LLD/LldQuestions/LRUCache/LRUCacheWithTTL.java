package LLD.LldQuestions.LRUCache;

import java.util.HashMap;
import java.util.Map;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║              LRU Cache with TTL (Time-To-Live) Support                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * This builds on top of the basic LRU Cache (see LRUCache.java).
 * Read that file first — this one only covers what's NEW.
 *
 * ============================================================================
 * 1. WHAT CHANGES WITH TTL?
 * ============================================================================
 *
 * Basic LRU Cache:
 *   Entry dies ONLY when evicted (cache full + it's the least recently used).
 *
 * LRU Cache with TTL:
 *   Entry dies in TWO ways:
 *     1. Evicted (same as before — LRU when cache is full)
 *     2. Expired (entry's TTL has passed — it's stale, remove it)
 *
 * Real-world example:
 *   - DNS cache: each record has a TTL (e.g., 300 seconds). After that,
 *     even if you haven't hit capacity, the record is stale and must be
 *     re-fetched from the authoritative DNS server.
 *   - Session cache: user sessions expire after 30 minutes of inactivity.
 *   - API response cache: cached response is valid for 60 seconds.
 *
 * ============================================================================
 * 2. WHAT CHANGES IN THE NODE?
 * ============================================================================
 *
 *   Basic Node:                     TTL Node:
 *   ┌──────────────────┐            ┌──────────────────────────┐
 *   │ key              │            │ key                      │
 *   │ value            │            │ value                    │
 *   │ prev             │            │ prev                     │
 *   │ next             │            │ next                     │
 *   └──────────────────┘            │ expiryTime  ← NEW!      │
 *                                   └──────────────────────────┘
 *
 *   expiryTime = System.currentTimeMillis() + ttlMillis
 *   If current time > expiryTime → node is expired / stale.
 *
 * ============================================================================
 * 3. TWO STRATEGIES FOR HANDLING EXPIRED ENTRIES
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                     │
 *   │  Strategy A: LAZY EVICTION (we use this — simpler)                  │
 *   │  ─────────────────────────────────────────────────                  │
 *   │  On every get() → check if the node is expired.                    │
 *   │    - If expired → remove it from DLL + map, return -1.             │
 *   │    - If alive   → move to head, return value (normal LRU).         │
 *   │                                                                     │
 *   │  Pros: No extra threads, simple code, O(1) per operation.          │
 *   │  Cons: Expired entries stay in cache until someone tries to         │
 *   │        access them → wastes memory.                                 │
 *   │                                                                     │
 *   │  ────────────────────────────────────────────────────               │
 *   │                                                                     │
 *   │  Strategy B: BACKGROUND CLEANUP THREAD                              │
 *   │  ─────────────────────────────────────────                          │
 *   │  A separate thread runs periodically (e.g., every 1 second)        │
 *   │  and walks the DLL from TAIL towards HEAD, removing any            │
 *   │  expired nodes.                                                     │
 *   │                                                                     │
 *   │  Pros: Memory is reclaimed promptly.                                │
 *   │  Cons: Needs thread safety (locks / synchronization).              │
 *   │        More complex code.                                           │
 *   │                                                                     │
 *   │  ────────────────────────────────────────────────────               │
 *   │                                                                     │
 *   │  Strategy C: HYBRID (production systems like Caffeine / Guava)     │
 *   │  ──────────────────────────────────────────────────────             │
 *   │  Lazy eviction on every get/put + periodic background cleanup.     │
 *   │  Best of both worlds.                                               │
 *   │                                                                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 4. FLOW DIAGRAMS (only showing what's DIFFERENT from basic LRU)
 * ============================================================================
 *
 * ── get(key) Flow WITH TTL ──
 *
 *   get(key)
 *      │
 *      ▼
 *   Is key in HashMap? ─── NO ──→ return -1
 *      │
 *     YES
 *      │
 *      ▼
 *   Fetch Node from HashMap
 *      │
 *      ▼
 *   ┌─────────────────────────────┐
 *   │ Is node EXPIRED?            │   ← NEW STEP
 *   │ (currentTime > expiryTime)  │
 *   └──────────┬──────────────────┘
 *              │
 *      ┌───── YES ─────┐
 *      │               │
 *      ▼               │
 *   Remove node        │
 *   from DLL + map     │
 *      │               │
 *      ▼               │
 *   return -1          │
 *                      │
 *      ┌───── NO ──────┘
 *      │
 *      ▼
 *   Move node to HEAD (normal LRU behavior)
 *      │
 *      ▼
 *   return node.value
 *
 *
 * ── put(key, value) Flow WITH TTL ──
 *
 *   put(key, value, ttlMillis)      ← now takes a TTL parameter
 *      │
 *      ▼
 *   Is key in HashMap?
 *      │
 *      ├── YES
 *      │    │
 *      │    ▼
 *      │   Update value + RESET expiryTime   ← NEW: refresh the TTL
 *      │    │
 *      │    ▼
 *      │   moveToHead (same as before)
 *      │
 *      └── NO
 *           │
 *           ▼
 *      Cache full? ── YES ──→ evictLRU (same as before)
 *           │
 *           ▼
 *      Create Node(key, value, expiryTime)   ← NEW: set expiry
 *           │
 *           ▼
 *      addAfterHead + put in HashMap (same as before)
 *
 *
 * ============================================================================
 * 5. VISUAL DRY RUN (capacity = 3, TTL = 5 seconds)
 * ============================================================================
 *
 *   Time(s)  Operation        │  DLL State                         │  Notes
 *   ───────  ─────────────────┼────────────────────────────────────┼──────────────────
 *   t=0      put(1,"A",5000)  │  HEAD ⟷ [1:A exp@5s] ⟷ TAIL      │  expires at t=5
 *   t=1      put(2,"B",5000)  │  HEAD ⟷ [2:B@6s] ⟷ [1:A@5s] ⟷ T │  expires at t=6
 *   t=2      put(3,"C",5000)  │  HEAD ⟷ [3:C@7s] ⟷ [2:B@6s]     │  FULL
 *            		          │   ⟷ [1:A@5s] ⟷ TAIL              │
 *   t=3      get(1) → "A"    │  HEAD ⟷ [1:A@5s] ⟷ [3:C@7s]     │  still alive
 *            		          │   ⟷ [2:B@6s] ⟷ TAIL              │  (t=3 < 5)
 *   t=6      get(1) → -1     │  HEAD ⟷ [3:C@7s] ⟷ [2:B@6s] ⟷ T │  EXPIRED!
 *            		          │                                    │  (t=6 > 5)
 *            		          │                                    │  removed from
 *            		          │                                    │  DLL + map
 *   t=7      get(2) → -1     │  HEAD ⟷ [3:C@7s] ⟷ TAIL          │  EXPIRED at t=6
 *   t=7      get(3) → -1     │  HEAD ⟷ TAIL                      │  EXPIRED at t=7
 *
 * ============================================================================
 * 6. CODE IMPLEMENTATION
 * ============================================================================
 */
public class LRUCacheWithTTL {

    // ─── Node now includes expiryTime ─────────────────────────────────────
    //
    //       ┌──────────────────────────────┐
    //       │         TTL Node             │
    //       │  ┌──────┬───────────────┐    │
    //       │  │ key  │ value         │    │
    //       │  ├──────┴───────────────┤    │
    //       │  │ expiryTime (millis)  │    │
    //       │  ├──────────────────────┤    │
    //       │  │ prev    ←  →   next  │    │
    //       │  └──────────────────────┘    │
    //       └──────────────────────────────┘

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

    // ─── Fields (same as basic LRU + default TTL) ─────────────────────────

    private final int capacity;
    private final long defaultTTLMillis;
    private final Map<Integer, Node> map;
    private final Node head;
    private final Node tail;

    // ─── Constructor ──────────────────────────────────────────────────────

    public LRUCacheWithTTL(int capacity, long defaultTTLMillis) {
        this.capacity = capacity;
        this.defaultTTLMillis = defaultTTLMillis;
        this.map = new HashMap<>();

        this.head = new Node(0, 0, 0);
        this.tail = new Node(0, 0, 0);
        head.next = tail;
        tail.prev = head;
    }

    // ─── Helper: check if a node is expired ───────────────────────────────

    private boolean isExpired(Node node) {
        return System.currentTimeMillis() > node.expiryTime;
    }

    // ─── get(key) — now checks expiry before returning ────────────────────

    public int get(int key) {
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
    }

    // ─── put(key, value) — uses default TTL ───────────────────────────────

    public void put(int key, int value) {
        put(key, value, defaultTTLMillis);
    }

    // ─── put(key, value, ttlMillis) — with custom TTL per entry ───────────

    public void put(int key, int value, long ttlMillis) {
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
    }

    // ─── DLL Helpers (identical to basic LRU) ─────────────────────────────

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

    // ─── Utility ──────────────────────────────────────────────────────────

    public int size() {
        return map.size();
    }

    public void printState() {
        long now = System.currentTimeMillis();
        System.out.print("  Cache: HEAD ⟷ ");
        Node curr = head.next;
        while (curr != tail) {
            long remainingMs = curr.expiryTime - now;
            String status = remainingMs > 0 ? (remainingMs + "ms left") : "EXPIRED";
            System.out.print("[" + curr.key + ":" + curr.value + " (" + status + ")] ⟷ ");
            curr = curr.next;
        }
        System.out.println("TAIL");
    }

    // ============================================================================
    // 7. DEMO
    // ============================================================================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     LRU Cache with TTL — Demo (capacity=3, TTL=2s)     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        LRUCacheWithTTL cache = new LRUCacheWithTTL(3, 2000);

        // ── Insert 3 entries (all expire in 2 seconds) ──
        System.out.println("Step 1: put(1, 10)  — TTL = 2 seconds");
        cache.put(1, 10);
        cache.printState();

        System.out.println("Step 2: put(2, 20)  — TTL = 2 seconds");
        cache.put(2, 20);
        cache.printState();

        System.out.println("Step 3: put(3, 30)  — TTL = 2 seconds");
        cache.put(3, 30);
        cache.printState();
        System.out.println();

        // ── Immediately get → should work ──
        System.out.println("Step 4: get(1) immediately → " + cache.get(1));
        System.out.println("  → Entry is alive (TTL hasn't passed)");
        System.out.println();

        // ── Wait for entries to expire ──
        System.out.println("⏳ Sleeping 3 seconds (entries will expire after 2s)...");
        Thread.sleep(3000);
        System.out.println();

        // ── Now get → should return -1 (expired) ──
        System.out.println("Step 5: get(1) after 3s → " + cache.get(1));
        System.out.println("  → EXPIRED! TTL was 2s, 3s have passed. Returns -1.");
        System.out.println("  → Node removed from DLL and HashMap (lazy eviction).");
        System.out.println();

        System.out.println("Step 6: get(2) after 3s → " + cache.get(2));
        System.out.println("  → EXPIRED! Same reason.");
        System.out.println();

        System.out.println("  Current cache size: " + cache.size());
        cache.printState();
        System.out.println("  → Only key 3 remains in the map (will be removed on next get).");
        System.out.println();

        // ── Insert new entry with a longer TTL ──
        System.out.println("Step 7: put(4, 40, 10000)  — TTL = 10 seconds");
        cache.put(4, 40, 10000);
        cache.printState();
        System.out.println();

        System.out.println("Step 8: get(4) → " + cache.get(4));
        System.out.println("  → Alive! 10 seconds haven't passed yet.");
        System.out.println();

        // ── Update existing entry → resets TTL ──
        System.out.println("Step 9: put(4, 400, 10000) → update value + reset TTL");
        cache.put(4, 400, 10000);
        cache.printState();
        System.out.println("  → Value updated to 400, TTL refreshed to 10 more seconds.");
    }
}

/*
 * ============================================================================
 * 8. EXPECTED OUTPUT
 * ============================================================================
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║     LRU Cache with TTL — Demo (capacity=3, TTL=2s)                    ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Step 1: put(1, 10)  — TTL = 2 seconds
 *   Cache: HEAD ⟷ [1:10 (~2000ms left)] ⟷ TAIL
 * Step 2: put(2, 20)  — TTL = 2 seconds
 *   Cache: HEAD ⟷ [2:20 (~2000ms left)] ⟷ [1:10 (~2000ms left)] ⟷ TAIL
 * Step 3: put(3, 30)  — TTL = 2 seconds
 *   Cache: HEAD ⟷ [3:30 (~2000ms)] ⟷ [2:20 (~2000ms)] ⟷ [1:10 (~2000ms)] ⟷ TAIL
 *
 * Step 4: get(1) immediately → 10
 *   → Entry is alive (TTL hasn't passed)
 *
 * ⏳ Sleeping 3 seconds (entries will expire after 2s)...
 *
 * Step 5: get(1) after 3s → -1
 *   → EXPIRED! TTL was 2s, 3s have passed. Returns -1.
 *   → Node removed from DLL and HashMap (lazy eviction).
 *
 * Step 6: get(2) after 3s → -1
 *   → EXPIRED! Same reason.
 *
 *   Current cache size: 1
 *   Cache: HEAD ⟷ [3:30 (EXPIRED)] ⟷ TAIL
 *   → Only key 3 remains in the map (will be removed on next get).
 *
 * Step 7: put(4, 40, 10000)  — TTL = 10 seconds
 *   Cache: HEAD ⟷ [4:40 (~10000ms left)] ⟷ [3:30 (EXPIRED)] ⟷ TAIL
 *
 * Step 8: get(4) → 40
 *   → Alive! 10 seconds haven't passed yet.
 *
 * Step 9: put(4, 400, 10000) → update value + reset TTL
 *   Cache: HEAD ⟷ [4:400 (~10000ms left)] ⟷ [3:30 (EXPIRED)] ⟷ TAIL
 *   → Value updated to 400, TTL refreshed to 10 more seconds.
 *
 * ============================================================================
 * 9. WHAT'S DIFFERENT — BASIC LRU vs LRU + TTL (Summary)
 * ============================================================================
 *
 *   Aspect             Basic LRU           LRU + TTL
 *   ─────────────────  ──────────────────  ──────────────────────────
 *   Node fields        key, value,         key, value, expiryTime,
 *                      prev, next          prev, next
 *
 *   Entry removed?     Only when evicted   When evicted OR expired
 *                      (cache full)        (whichever comes first)
 *
 *   get() extra step   —                   Check if expired → remove
 *                                          if stale, return -1
 *
 *   put() extra step   —                   Set expiryTime on create
 *                                          Reset expiryTime on update
 *
 *   Complexity          O(1) get/put       O(1) get/put (same!)
 *
 * ============================================================================
 * 10. INTERVIEW TIP
 * ============================================================================
 *
 * If asked "how would you add TTL?", walk through this:
 *
 *   1. "I'd add an expiryTime field to the Node."
 *   2. "On put(), I set expiryTime = now + ttl."
 *   3. "On get(), before returning, I check if the node is expired.
 *       If yes, I remove it and return -1. This is lazy eviction."
 *   4. "For production, I'd add a background cleanup thread that
 *       periodically scans and removes expired entries to reclaim memory."
 *
 * That's 4 sentences. Simple, clear, and shows you know the trade-offs.
 */
