# Design Autocomplete / Typeahead

> **Difficulty:** Medium | **Frequency:** ★★★★☆ | **Companies:** Google, Amazon, LinkedIn
> **Source:** Alex Xu Vol 1 Chapter 13

---

## 1. Requirements

### Functional
- As user types, show top 5 search suggestions
- Suggestions based on search popularity / frequency
- Suggestions update as more characters are typed
- Support multi-language

### Non-Functional
- Latency < 100ms (must feel instant)
- High availability
- Scalable to billions of search queries

---

## 2. Data Structure: Trie

```
TRIE (Prefix Tree):

  Stored searches: "tree", "try", "true", "top", "toy", "to"

                    (root)
                   /      \
                  t        ...
                 / \
                r   o
               / \   \  \
              e   u   p  y
             /   /
            e   e
  
  "tr" typed → traverse to 'r' → all descendants are suggestions:
  tree(10), try(8), true(5) → return top 5 by frequency

  Each node stores:
  - Character
  - Is end of word?
  - Top-K suggestions cached (pre-computed!)
  
  With cached top-K at each node:
  Node 'r' (under 't'): top_suggestions = ["tree", "try", "true"]
  
  Lookup: O(prefix_length) — just traverse the prefix, read cached results!
```

---

## 3. Architecture

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│  Client  │────►│ Load Balancer│────►│ Suggestion   │
│  (types  │     └──────────────┘     │ Service      │
│  "tre")  │                          └──────┬───────┘
└──────────┘                                 │
                                    ┌────────┴────────┐
                                    ▼                 ▼
                             ┌──────────┐      ┌──────────┐
                             │  Trie    │      │  Cache   │
                             │  Server  │      │ (Redis)  │
                             │          │      │          │
                             │In-memory │      │ "tre" →  │
                             │  Trie    │      │ [tree,   │
                             │          │      │  trend]  │
                             └──────────┘      └──────────┘


DATA COLLECTION & TRIE UPDATE:

  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
  │ Search Logs  │────►│ Aggregation  │────►│ Trie Builder │
  │ (Kafka)      │     │ Service      │     │ (Offline)    │
  │              │     │ (Spark/Flink)│     │              │
  │ "tree" x 100│     │              │     │ Builds new   │
  │ "trend" x 50│     │ Count freq   │     │ trie weekly  │
  └──────────────┘     │ per prefix   │     │ Replaces old │
                       └──────────────┘     └──────────────┘

  Trie is NOT updated in real-time (too expensive).
  Updated periodically (weekly/daily) via offline pipeline.
  For trending topics: separate real-time trending service.
```

---

## 4. Optimizations

```
1. BROWSER CACHING: Cache suggestions in browser for 1 hour
   "tre" → results cached → no server call on repeat

2. DATA SAMPLING: Don't log every search query
   Log 1 in 10 queries → still statistically accurate

3. FILTER LAYER: Remove offensive/inappropriate suggestions

4. PERSONALIZATION: Boost suggestions based on user's history

5. TRIE SHARDING: Split trie across servers
   Server 1: prefixes a-m
   Server 2: prefixes n-z
   (or by consistent hashing of prefix)
```

---

## 5. Key Points for Interview

1. **Trie** with pre-computed top-K at each node
2. **Offline trie building** — don't update in real-time
3. **< 100ms latency** — serve from memory, add Redis cache
4. **CDN/browser caching** for popular prefixes
5. **Data pipeline**: Search logs → Kafka → Aggregation → Trie rebuild
6. **Shard trie** for scale (by prefix range)
