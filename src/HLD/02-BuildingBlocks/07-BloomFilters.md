# Bloom Filters & Probabilistic Data Structures

> **Difficulty:** Easy-Medium | **Time:** 1.5 hours | **Priority:** Good to Know

---

## 1. Bloom Filter

A space-efficient probabilistic data structure that tells you:
- **"Definitely NOT in set"** — 100% accurate
- **"Probably in set"** — may have false positives

```
HOW IT WORKS:

Bit Array (initially all 0s):
  [0][0][0][0][0][0][0][0][0][0]
   0  1  2  3  4  5  6  7  8  9

Insert "apple": hash1("apple")=2, hash2("apple")=5, hash3("apple")=8
  [0][0][1][0][0][1][0][0][1][0]
   0  1  2  3  4  5  6  7  8  9

Insert "banana": hash1("banana")=1, hash2("banana")=5, hash3("banana")=7
  [0][1][1][0][0][1][0][1][1][0]
   0  1  2  3  4  5  6  7  8  9

Check "apple": positions 2,5,8 → all 1s → "Probably YES" ✓
Check "grape": positions 1,3,6 → position 3 is 0 → "Definitely NO" ✓
Check "cherry": positions 1,5,8 → all 1s → "Probably YES"
               but cherry was never added! → FALSE POSITIVE
```

### Trade-offs
```
┌──────────────────────────────────────────────────┐
│              BLOOM FILTER TRADE-OFFS              │
├──────────────────────────────────────────────────┤
│                                                  │
│  PROS:                                           │
│  ✓ Extremely space efficient                     │
│    (10 bits per element for 1% false positive)   │
│  ✓ O(k) insert and lookup (k = num hash fns)    │
│  ✓ No false negatives ever                       │
│                                                  │
│  CONS:                                           │
│  ✗ False positives possible                      │
│  ✗ Cannot delete elements                        │
│    (use Counting Bloom Filter instead)           │
│  ✗ Cannot enumerate stored elements              │
│                                                  │
│  False Positive Rate:                            │
│  More bits per element → lower false positive    │
│  More hash functions → lower false positive      │
│  (up to optimal k)                               │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Where Bloom Filters Are Used in System Design

```
1. WEB CRAWLER — "Have I visited this URL?"
   ┌──────────┐     ┌──────────────┐
   │ New URL  │────►│ Bloom Filter │──► "Definitely not seen" → Crawl it
   └──────────┘     │ (all URLs)   │──► "Probably seen" → Skip it
                    └──────────────┘
   Saves checking billions of URLs in a database!

2. DATABASE READS — "Does this key exist?"
   ┌──────────┐     ┌──────────────┐
   │ Query    │────►│ Bloom Filter │──► "Definitely no" → Skip disk read!
   │ key=xyz  │     │ (per SSTable)│──► "Maybe" → Read from disk
   └──────────┘     └──────────────┘
   Used by: Cassandra, HBase, LevelDB, RocksDB

3. SPAM FILTER — "Is this email address known spam?"
4. CACHE — Check bloom filter before cache to avoid cache penetration
5. WEAK PASSWORD CHECK — Is this password in a dictionary?
```

---

## 2. Other Useful Data Structures

### Count-Min Sketch (Frequency Estimation)
```
"How many times has this item appeared?"

Similar to Bloom Filter but counts occurrences.
Uses: Trending topics, heavy hitter detection, frequency capping

     h1  h2  h3
    [3] [1] [5]  → estimated count of "item_x" = min(3,1,5) = 1
    [2] [4] [2]
    [7] [3] [1]
```

### HyperLogLog (Cardinality Estimation)
```
"How many UNIQUE items have I seen?"

Counts unique elements using ~12 KB of memory regardless of set size!

Example: Count unique visitors to a website
  - Exact: Store all visitor IDs → huge memory
  - HyperLogLog: 12 KB → estimates with ~2% error

Used by: Redis (PFADD, PFCOUNT commands)
```

---

## 3. Key Takeaways for Interviews

1. **Bloom filter** = space-efficient membership test with no false negatives
2. Mention it in: web crawler (URL dedup), database (skip disk reads), cache penetration
3. **HyperLogLog** for counting unique items (unique visitors, unique searches)
4. **Count-Min Sketch** for frequency estimation (trending topics)
5. These are supporting tools, not main architecture — use them to optimize specific bottlenecks
