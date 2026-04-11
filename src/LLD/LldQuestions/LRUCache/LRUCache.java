package LLD.LldQuestions.LRUCache;

import java.util.HashMap;
import java.util.Map;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     LRU Cache — Low Level Design                       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * INTERVIEW WALKTHROUGH (1.5 hours)
 * ============================================================================
 *
 *   Time       Activity
 *   ─────────  ────────────────────────────────────────────────────
 *   0–10 min   Clarify requirements, discuss API (get / put)
 *   10–20 min  Talk through data-structure choice (HashMap + DLL)
 *   20–30 min  Draw the flow diagrams on whiteboard
 *   30–70 min  Code the solution (Node → DLL helpers → LRUCache)
 *   70–80 min  Dry-run with an example
 *   80–90 min  Discuss improvements, trade-offs, follow-ups
 *
 * ============================================================================
 * 1. WHAT IS AN LRU CACHE?
 * ============================================================================
 *
 * LRU = Least Recently Used
 *
 * A fixed-size cache that, when full, evicts the entry that was accessed
 * the LONGEST TIME AGO. Every get() or put() counts as an "access".
 *
 * Real-world examples:
 *   - Browser cache (pages visited least recently get dropped first)
 *   - Database query cache
 *   - DNS cache
 *   - CPU cache page replacement
 *
 * ============================================================================
 * 2. FUNCTIONAL REQUIREMENTS
 * ============================================================================
 *
 * We need only TWO operations, both in O(1) time:
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │  get(key)        → returns value if present, else -1                │
 *   │                     marks the entry as "most recently used"         │
 *   │                                                                     │
 *   │  put(key, value) → inserts or updates the key-value pair            │
 *   │                     marks the entry as "most recently used"         │
 *   │                     if cache is full → evicts the LEAST recently    │
 *   │                     used entry before inserting                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * Constraints:
 *   - capacity ≥ 1
 *   - Both get and put must be O(1) average time
 *
 * ============================================================================
 * 3. WHY HashMap ALONE IS NOT ENOUGH
 * ============================================================================
 *
 *   HashMap gives us O(1) get and put — great!
 *   But how do we know which key is the LEAST recently used?
 *
 *   Option A: Store a timestamp with each entry, scan all entries to find
 *             the oldest → O(n) eviction. Too slow.
 *
 *   Option B: Use a data structure that maintains ACCESS ORDER and allows
 *             O(1) removal + O(1) insertion at both ends.
 *             → That's a DOUBLY LINKED LIST.
 *
 * ============================================================================
 * 4. THE KEY INSIGHT — HashMap + Doubly Linked List
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                     │
 *   │   HashMap<Key, Node>          Doubly Linked List                    │
 *   │   ┌─────┬───────┐            HEAD ⟷ [A] ⟷ [B] ⟷ [C] ⟷ TAIL      │
 *   │   │ "A" │ →Node │──────────────────→ ↑                             │
 *   │   │ "B" │ →Node │────────────────────────→ ↑                       │
 *   │   │ "C" │ →Node │──────────────────────────────→ ↑                 │
 *   │   └─────┴───────┘                                                   │
 *   │                                                                     │
 *   │   HashMap → O(1) lookup by key → gives us the Node pointer         │
 *   │   DLL     → O(1) move/remove/insert → maintains access order       │
 *   │                                                                     │
 *   │   HEAD side = Most Recently Used (MRU)                              │
 *   │   TAIL side = Least Recently Used (LRU) ← this gets evicted        │
 *   │                                                                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 *   Why doubly linked list and not singly?
 *   → We need to remove a node from the MIDDLE of the list in O(1).
 *     With a singly linked list, you'd need the previous node, which
 *     means traversal → O(n). Doubly linked list has node.prev → O(1).
 *
 * ============================================================================
 * 5. DESIGN PATTERNS USED
 * ============================================================================
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │                                                                     │
 *   │  (a) Sentinel / Dummy Node Pattern                                  │
 *   │      We use dummy HEAD and TAIL nodes that never hold real data.    │
 *   │      This eliminates all null-checks and edge cases when the list   │
 *   │      is empty or has one element.                                   │
 *   │                                                                     │
 *   │      Without sentinels:                                             │
 *   │        if (head == null) { ... }                                    │
 *   │        if (node == head) { ... }                                    │
 *   │        if (node == tail) { ... }  ← messy, error-prone             │
 *   │                                                                     │
 *   │      With sentinels:                                                │
 *   │        HEAD ⟷ ... real nodes ... ⟷ TAIL                            │
 *   │        Every real node always has a valid prev and next.            │
 *   │        Insert/remove code is ONE simple path, no branching.         │
 *   │                                                                     │
 *   │  (b) Composition Pattern                                            │
 *   │      LRUCache is COMPOSED of a HashMap and a DLL.                   │
 *   │      Each data structure handles what it's best at:                 │
 *   │        HashMap → fast key lookup                                    │
 *   │        DLL     → fast ordering / eviction                           │
 *   │                                                                     │
 *   │  (c) Facade Pattern                                                 │
 *   │      The LRUCache class exposes just get() and put().               │
 *   │      Internally it coordinates two data structures, but the         │
 *   │      caller doesn't know or care about the DLL.                     │
 *   │                                                                     │
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. FLOW DIAGRAMS
 * ============================================================================
 *
 * ── get(key) Flow ──
 *
 *   get(key)
 *      │
 *      ▼
 *   Is key in HashMap?
 *      │
 *      ├── NO  → return -1
 *      │
 *      └── YES
 *           │
 *           ▼
 *      Fetch Node from HashMap
 *           │
 *           ▼
 *      Remove Node from its current position in DLL
 *           │
 *           ▼
 *      Insert Node right after HEAD (mark as MRU)
 *           │
 *           ▼
 *      return node.value
 *
 *
 * ── put(key, value) Flow ──
 *
 *   put(key, value)
 *      │
 *      ▼
 *   Is key in HashMap?
 *      │
 *      ├── YES (update existing)
 *      │    │
 *      │    ▼
 *      │   Update node.value
 *      │    │
 *      │    ▼
 *      │   Remove Node from its current position in DLL
 *      │    │
 *      │    ▼
 *      │   Insert Node right after HEAD (mark as MRU)
 *      │    │
 *      │    ▼
 *      │   DONE
 *      │
 *      └── NO (new insertion)
 *           │
 *           ▼
 *      Is cache at full capacity?
 *           │
 *           ├── YES
 *           │    │
 *           │    ▼
 *           │   Evict: remove node just before TAIL (the LRU entry)
 *           │    │
 *           │    ▼
 *           │   Remove evicted key from HashMap
 *           │
 *           └── (continue whether YES or NO)
 *                │
 *                ▼
 *           Create new Node(key, value)
 *                │
 *                ▼
 *           Insert Node right after HEAD (mark as MRU)
 *                │
 *                ▼
 *           Put (key → Node) into HashMap
 *                │
 *                ▼
 *           DONE
 *
 *
 * ── Visual Example: capacity = 3 ──
 *
 *   Initial state (empty):
 *     HEAD ⟷ TAIL
 *     HashMap: {}
 *
 *   put(1, "A"):
 *     HEAD ⟷ [1:A] ⟷ TAIL
 *     HashMap: {1→Node}
 *
 *   put(2, "B"):
 *     HEAD ⟷ [2:B] ⟷ [1:A] ⟷ TAIL
 *     HashMap: {1→Node, 2→Node}
 *
 *   put(3, "C"):
 *     HEAD ⟷ [3:C] ⟷ [2:B] ⟷ [1:A] ⟷ TAIL
 *     HashMap: {1→Node, 2→Node, 3→Node}          ← cache is FULL
 *
 *   get(1):  → returns "A", moves [1:A] to front
 *     HEAD ⟷ [1:A] ⟷ [3:C] ⟷ [2:B] ⟷ TAIL
 *     HashMap: {1→Node, 2→Node, 3→Node}
 *                                  ↑
 *                              LRU = key 2
 *
 *   put(4, "D"):  → cache full, evict LRU (key 2)
 *     HEAD ⟷ [4:D] ⟷ [1:A] ⟷ [3:C] ⟷ TAIL
 *     HashMap: {1→Node, 3→Node, 4→Node}          ← key 2 is GONE
 *
 *
 * ============================================================================
 * 7. COMPLEXITY ANALYSIS
 * ============================================================================
 *
 *   Operation    Time      Space
 *   ─────────    ────      ─────
 *   get(key)     O(1)      —
 *   put(key,v)   O(1)      —
 *   Overall      —         O(capacity)   ← HashMap + DLL nodes
 *
 *   Why O(1)?
 *   - HashMap.get / put         → O(1) average
 *   - DLL remove (given node)   → O(1)  (just re-link prev/next)
 *   - DLL insert after HEAD     → O(1)  (just link 2 pointers)
 *
 * ============================================================================
 * 8. CODE IMPLEMENTATION
 * ============================================================================
 */
public class LRUCache {

    // ─── Inner class: Doubly Linked List Node ─────────────────────────────
    //
    //   Each node stores key + value.
    //   We store the KEY in the node so that during eviction we can
    //   remove the entry from the HashMap using that key.
    //
    //       ┌──────────────────────┐
    //       │    Node              │
    //       │  ┌──────┬─────────┐  │
    //       │  │ key  │ value   │  │
    //       │  ├──────┴─────────┤  │
    //       │  │ prev ← → next  │  │
    //       │  └────────────────┘  │
    //       └──────────────────────┘

    static class Node {
        int key;
        int value;
        Node prev;
        Node next;

        Node(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────

    private final int capacity;
    private final Map<Integer, Node> map;
    private final Node head;  // sentinel — always first, never holds data
    private final Node tail;  // sentinel — always last, never holds data

    // ─── Constructor ──────────────────────────────────────────────────────
    //
    //   Sets up: empty HashMap + sentinel-linked DLL
    //
    //     HEAD ⟷ TAIL        (no real nodes yet)
    //

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();

        this.head = new Node(0, 0);
        this.tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    // ─── get(key) ─────────────────────────────────────────────────────────

    public int get(int key) {
        if (!map.containsKey(key)) {
            return -1;
        }

        Node node = map.get(key);
        moveToHead(node);
        return node.value;
    }

    // ─── put(key, value) ──────────────────────────────────────────────────

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.value = value;
            moveToHead(node);
        } else {
            if (map.size() == capacity) {
                evictLRU();
            }

            Node newNode = new Node(key, value);
            addAfterHead(newNode);
            map.put(key, newNode);
        }
    }

    // ─── DLL Helper: remove a node from wherever it is ────────────────────
    //
    //   Before:  ... ⟷ [A] ⟷ [node] ⟷ [B] ⟷ ...
    //   After:   ... ⟷ [A] ⟷ [B] ⟷ ...           (node is detached)
    //

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    // ─── DLL Helper: insert a node right after HEAD ───────────────────────
    //
    //   Before:  HEAD ⟷ [X] ⟷ ...
    //   After:   HEAD ⟷ [node] ⟷ [X] ⟷ ...
    //

    private void addAfterHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    // ─── DLL Helper: move existing node to just after HEAD (mark as MRU) ──

    private void moveToHead(Node node) {
        removeNode(node);
        addAfterHead(node);
    }

    // ─── DLL Helper: evict the LRU entry (node just before TAIL) ──────────
    //
    //   Before:  ... ⟷ [Y] ⟷ [LRU] ⟷ TAIL
    //   After:   ... ⟷ [Y] ⟷ TAIL               (LRU removed from DLL + map)
    //

    private void evictLRU() {
        Node lruNode = tail.prev;
        removeNode(lruNode);
        map.remove(lruNode.key);
    }

    // ─── Utility: print current cache state (for debugging/demo) ──────────

    public void printState() {
        System.out.print("  Cache: HEAD ⟷ ");
        Node curr = head.next;
        while (curr != tail) {
            System.out.print("[" + curr.key + ":" + curr.value + "] ⟷ ");
            curr = curr.next;
        }
        System.out.println("TAIL");
    }

    // ============================================================================
    // 9. DEMO / DRY RUN
    // ============================================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         LRU Cache — Demo (capacity = 3)        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        LRUCache cache = new LRUCache(3);

        // ── Step 1: Fill the cache ──
        System.out.println("Step 1: put(1, 10)");
        cache.put(1, 10);
        cache.printState();

        System.out.println("Step 2: put(2, 20)");
        cache.put(2, 20);
        cache.printState();

        System.out.println("Step 3: put(3, 30)");
        cache.put(3, 30);
        cache.printState();
        System.out.println("  → Cache is now FULL (3/3)");
        System.out.println();

        // ── Step 2: Access key 1 → moves it to front ──
        System.out.println("Step 4: get(1) → returns " + cache.get(1));
        cache.printState();
        System.out.println("  → key 1 moved to front; LRU is now key 2");
        System.out.println();

        // ── Step 3: Insert key 4 → evicts LRU (key 2) ──
        System.out.println("Step 5: put(4, 40)  → cache full, must evict LRU");
        cache.put(4, 40);
        cache.printState();
        System.out.println("  → key 2 was evicted (it was LRU)");
        System.out.println();

        // ── Step 4: Try to get evicted key ──
        System.out.println("Step 6: get(2) → returns " + cache.get(2));
        System.out.println("  → key 2 was evicted, so returns -1");
        System.out.println();

        // ── Step 5: Update existing key ──
        System.out.println("Step 7: put(3, 300)  → update existing key 3");
        cache.put(3, 300);
        cache.printState();
        System.out.println("  → key 3 updated to 300 and moved to front");
        System.out.println();

        // ── Step 6: Add another → evicts LRU (key 1) ──
        System.out.println("Step 8: put(5, 50)  → cache full, must evict LRU");
        cache.put(5, 50);
        cache.printState();
        System.out.println("  → key 1 was evicted (it was LRU)");
        System.out.println();

        System.out.println("Step 9: get(1) → returns " + cache.get(1));
        System.out.println("  → key 1 was evicted, so returns -1");
    }
}

/*
 * ============================================================================
 * 10. EXPECTED OUTPUT
 * ============================================================================
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║         LRU Cache — Demo (capacity = 3)                               ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Step 1: put(1, 10)
 *   Cache: HEAD ⟷ [1:10] ⟷ TAIL
 * Step 2: put(2, 20)
 *   Cache: HEAD ⟷ [2:20] ⟷ [1:10] ⟷ TAIL
 * Step 3: put(3, 30)
 *   Cache: HEAD ⟷ [3:30] ⟷ [2:20] ⟷ [1:10] ⟷ TAIL
 *   → Cache is now FULL (3/3)
 *
 * Step 4: get(1) → returns 10
 *   Cache: HEAD ⟷ [1:10] ⟷ [3:30] ⟷ [2:20] ⟷ TAIL
 *   → key 1 moved to front; LRU is now key 2
 *
 * Step 5: put(4, 40)  → cache full, must evict LRU
 *   Cache: HEAD ⟷ [4:40] ⟷ [1:10] ⟷ [3:30] ⟷ TAIL
 *   → key 2 was evicted (it was LRU)
 *
 * Step 6: get(2) → returns -1
 *   → key 2 was evicted, so returns -1
 *
 * Step 7: put(3, 300)  → update existing key 3
 *   Cache: HEAD ⟷ [3:300] ⟷ [4:40] ⟷ [1:10] ⟷ TAIL
 *   → key 3 updated to 300 and moved to front
 *
 * Step 8: put(5, 50)  → cache full, must evict LRU
 *   Cache: HEAD ⟷ [5:50] ⟷ [3:300] ⟷ [4:40] ⟷ TAIL
 *   → key 1 was evicted (it was LRU)
 *
 * Step 9: get(1) → returns -1
 *   → key 1 was evicted, so returns -1
 *
 * ============================================================================
 * 11. WHAT CAN BE IMPROVED (Interview Follow-ups)
 * ============================================================================
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │                                                                         │
 * │  (a) Thread Safety                                                      │
 * │      Current code is NOT thread-safe. Two threads calling put()         │
 * │      concurrently can corrupt the DLL pointers.                         │
 * │                                                                         │
 * │      Fix options:                                                       │
 * │      1. Wrap get/put in synchronized → simple, but serializes all ops   │
 * │      2. Use ReentrantReadWriteLock → allows concurrent reads            │
 * │      3. Use ConcurrentHashMap + CAS on DLL → complex but high perf     │
 * │                                                                         │
 * │  (b) Generic Types                                                      │
 * │      Current code uses int for key and value.                           │
 * │      Improve: LRUCache<K, V> with generics for any key/value type.     │
 * │                                                                         │
 * │  (c) TTL (Time-To-Live) Support                                        │
 * │      Real caches expire entries after a timeout, not just by access.    │
 * │      Add a timestamp to each Node, and a background cleanup thread      │
 * │      or lazy eviction on get().                                         │
 * │                                                                         │
 * │  (d) Eviction Policy Strategy Pattern                                   │
 * │      Extract the eviction logic behind an interface:                    │
 * │                                                                         │
 * │        interface EvictionPolicy<K> {                                    │
 * │            void keyAccessed(K key);                                     │
 * │            K evict();                                                   │
 * │        }                                                                │
 * │                                                                         │
 * │      Then implement LRUEvictionPolicy, LFUEvictionPolicy, etc.         │
 * │      The Cache class delegates eviction decisions to the policy.        │
 * │                                                                         │
 * │  (e) Size-based vs Count-based Eviction                                │
 * │      Current: evict when count > capacity.                              │
 * │      Better: evict when total byte-size exceeds a memory limit.        │
 * │      Each entry would report its size; evict until under the limit.    │
 * │                                                                         │
 * │  (f) Statistics / Observability                                        │
 * │      Track hit ratio, miss ratio, eviction count.                      │
 * │      Useful for monitoring cache effectiveness in production.           │
 * │                                                                         │
 * │  (g) Java's LinkedHashMap Shortcut                                     │
 * │      Java actually has a built-in one-liner solution:                   │
 * │                                                                         │
 * │      new LinkedHashMap<>(capacity, 0.75f, true) {                      │
 * │          protected boolean removeEldestEntry(Map.Entry e) {            │
 * │              return size() > capacity;                                  │
 * │          }                                                              │
 * │      };                                                                 │
 * │                                                                         │
 * │      But in an interview, they want to see you build it from scratch.  │
 * │                                                                         │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 12. QUICK COMPARISON: LRU vs LFU vs FIFO
 * ============================================================================
 *
 *   Policy   Evicts                  Data Structure             Use When
 *   ───────  ──────────────────────  ─────────────────────────  ──────────────────────
 *   LRU      Least Recently Used     HashMap + Doubly LL        Recency matters most
 *   LFU      Least Frequently Used   HashMap + Frequency Map    Frequency matters most
 *   FIFO     First In, First Out     HashMap + Queue            Simplest; order of arrival
 *
 * ============================================================================
 * 13. INTERVIEW CHEAT-SHEET (say this in 30 seconds)
 * ============================================================================
 *
 * "An LRU Cache uses a HashMap for O(1) key lookup, combined with a Doubly
 *  Linked List to maintain access order. On every get or put, the accessed
 *  node is moved to the head of the list. When the cache is full, we evict
 *  the node at the tail — that's the least recently used entry. Both
 *  operations are O(1). I use dummy head/tail sentinel nodes to simplify
 *  edge cases in the linked list."
 *
 */
