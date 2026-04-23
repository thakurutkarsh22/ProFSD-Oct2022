# Search & Indexing

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

Every user-facing product eventually grows a search box — and the moment
it does, the team discovers that `WHERE name LIKE '%shoe%'` will not
save them. Search is its own sub-system with its own data structures,
its own scoring math, its own indexing pipeline, and its own failure
modes. This document walks through the full stack: from the **inverted
index** that powers every full-text search engine, through **Lucene
segments** and **Elasticsearch** cluster architecture, through
**BM25** relevance scoring, and up to modern **hybrid search** that
combines keyword and vector retrieval for RAG and semantic search.

---

## Table of Contents

1. [Why Search Is Its Own Problem](#1-why-search-is-its-own-problem)
2. [The Inverted Index in Depth](#2-the-inverted-index-in-depth)
3. [The Analysis Pipeline](#3-the-analysis-pipeline)
4. [Lucene Segments & the Write Path](#4-lucene-segments--the-write-path)
5. [Elasticsearch Cluster Architecture](#5-elasticsearch-cluster-architecture)
6. [Request Routing: Query-Then-Fetch](#6-request-routing-query-then-fetch)
7. [Relevance Scoring: TF-IDF and BM25](#7-relevance-scoring-tf-idf-and-bm25)
8. [End-to-End Search System Architecture](#8-end-to-end-search-system-architecture)
9. [Autocomplete & Typeahead](#9-autocomplete--typeahead)
10. [Geospatial Indexing](#10-geospatial-indexing)
11. [Vector & Semantic Search](#11-vector--semantic-search)
12. [Hybrid Search & Reciprocal Rank Fusion](#12-hybrid-search--reciprocal-rank-fusion)
13. [Learning to Rank & Personalization](#13-learning-to-rank--personalization)
14. [Real-World Case Studies](#14-real-world-case-studies)
15. [Capacity, Sizing & Operational Pitfalls](#15-capacity-sizing--operational-pitfalls)
16. [Design Checklist](#16-design-checklist)
17. [Interview Q&A](#17-interview-qa)

---

## 1. Why Search Is Its Own Problem

A relational database is optimised for *"fetch row by primary key"* and
*"fetch rows matching a column predicate with a B-tree on it."* Search
has a fundamentally different shape:

```
  RELATIONAL QUERY            FULL-TEXT SEARCH
  ─────────────────           ────────────────
  SELECT * FROM products      "blue running shoes under $80"
  WHERE id = 42;               │
                               ├── any of these words, in any order
  Point lookup.                ├── stemmed ("running" ≈ "run")
  Tree height ~ log N.         ├── synonyms ("shoes" ≈ "sneakers")
                               ├── typo tolerance ("shoos" ≈ "shoes")
                               ├── ranked by relevance, not filtered
                               ├── facets (by brand, size, colour)
                               └── p50 < 50 ms across 100M docs
```

Trying to do this with `LIKE '%...%'` on a SQL database fails three
ways at once:

```
  PROBLEM                              WHY IT BREAKS
  ─────────────────────────────────────────────────────────────
  Leading-wildcard LIKE                Can't use B-tree → full scan
  No ranking                           Results are unordered
  No linguistic handling               "running" ≠ "run" ≠ "runner"
  Multi-word AND/OR                    Multiple scans, huge cost
  Facets over free text                Can't aggregate cheaply
```

The fix is a purpose-built data structure: the **inverted index**, and
a purpose-built engine around it (Lucene / Elasticsearch / OpenSearch /
Solr / Typesense / Meilisearch).

---

## 2. The Inverted Index in Depth

### 2.1 The core idea: flip the mapping

```
  FORWARD INDEX (what a DB does)
  ─────────────────────────────
  Doc 1 → [the, quick, brown, fox]
  Doc 2 → [the, quick, rabbit]
  Doc 3 → [brown, fox, jumps, high]

  INVERTED INDEX (what a search engine does)
  ─────────────────────────────────────────
  Term       Posting List (sorted doc IDs)
  ────────   ─────────────────────────────
  brown   →  [1, 3]
  fox     →  [1, 3]
  high    →  [3]
  jumps   →  [3]
  quick   →  [1, 2]
  rabbit  →  [2]
  the     →  [1, 2]
```

To answer `"brown AND fox"` we intersect two already-sorted posting
lists in linear time — no scanning of documents at all.

### 2.2 What's actually stored per term

A production inverted index (Lucene) stores much more than a doc-id
list. Per term you get a **posting list** of tuples:

```
  TERM "fox"  →   posting list
                ┌─────────────────────────────────────────────┐
                │ (docId=1, tf=1, positions=[3], offsets=...)  │
                │ (docId=3, tf=2, positions=[1,7], offsets=..) │
                └─────────────────────────────────────────────┘
                      │       │          │
                      │       │          └── where in doc (for phrase
                      │       │              queries & highlighting)
                      │       └────── frequency in this doc (for BM25)
                      └──────────────── monotonic → allows delta encoding
```

- **Term Dictionary** — a sorted map of every unique term. In Lucene
  this is held in memory (or mmap'd) as an **FST** (Finite-State
  Transducer), which compresses shared prefixes.
- **Postings** — per-term list of `(docId, tf, positions)` on disk,
  delta + variable-byte encoded so sequential reads are fast.
- **Doc Values** — a *column-oriented* sidecar used for sorting,
  aggregations, and faceting (you do **not** want to scan postings to
  sum a column).
- **Stored Fields** — the original `_source` JSON, returned on hit.
- **Norms** — per-document field-length factor used by BM25.

```
  ONE LUCENE SEGMENT (simplified)
  ┌──────────────────────────────────────────────────┐
  │  .tim  term dictionary  (FST in memory)          │
  │  .doc  postings list    (docIds, tf)             │
  │  .pos  positions        (for phrase queries)     │
  │  .nvd  norms            (doc length per field)   │
  │  .dvd  doc values       (column store for sort)  │
  │  .fdt  stored fields    (the original _source)   │
  └──────────────────────────────────────────────────┘
```

### 2.3 Phrase queries need positions

```
  Query: "brown fox"  (exact phrase)

  fox  → (doc1, pos=[3]) (doc3, pos=[2])
  brown→ (doc1, pos=[2]) (doc3, pos=[1])

  For each common doc, check: position("fox") = position("brown") + 1
   doc1: 3 == 2+1    match
   doc3: 2 != 1+1    match      (also matches!)
```

Without positions you can only do *bag-of-words* matching; with them
you can do `"quick fox"~3` (terms within 3 words of each other) and
highlighting.

### 2.4 Cost model

```
  Build index on N docs, V unique terms, L avg tokens/doc:
     time  ≈ O(N · L)             tokenise + insert
     space ≈ O(V + N · L / comp)  comp = delta/vbyte factor ~5-10×

  Query of k terms over N docs:
     time  ≈ O(k · avg_postings)  with skip-lists → near sub-linear
                                   for rare terms
```

For a web-scale index (billions of docs) the posting list for `"the"`
is huge, but Lucene uses **skip lists** and **WAND / Block-Max WAND**
to prune aggressively by upper-bound BM25 score.

---

## 3. The Analysis Pipeline

Before a token ever reaches the inverted index it passes through an
**analyzer** — the pipeline that turns raw text into indexable terms.
Whatever transformation you apply at index time, you must apply the
*same* transformation at query time, or queries won't match.

### 3.1 The three stages

```
  RAW TEXT: "<p>The Quickest Brown Foxes are RUNNING!</p>"
        │
        ▼
  ┌──────────────────────┐
  │  CHARACTER FILTERS   │  strip HTML, unicode-normalise,
  │                      │  map emoji → text, etc.
  └──────────┬───────────┘
             │ "The Quickest Brown Foxes are RUNNING!"
             ▼
  ┌──────────────────────┐
  │     TOKENIZER        │  split into tokens
  │ (standard/whitespace │  by UAX#29 word boundaries
  │  /ngram/…)           │
  └──────────┬───────────┘
             │ [The, Quickest, Brown, Foxes, are, RUNNING]
             ▼
  ┌──────────────────────┐
  │   TOKEN FILTERS      │  lowercase, stop words, stem,
  │  (chain, in order)   │  synonym expansion, ascii-fold
  └──────────┬───────────┘
             │ [quickest, brown, fox, run]   ← "are", "the" removed
             │                                  "foxes" → "fox"
             ▼                                  "running" → "run"
     STORED IN INVERTED INDEX
```

### 3.2 The transformations that matter

```
  FILTER            INPUT                OUTPUT
  ────────────────  ───────────────────  ───────────────────
  lowercase         "IPhone"             "iphone"
  ascii_folding     "café"               "cafe"
  stop words        "the quick fox"      "quick fox"
  stemming (porter) "running, ran, runs" "run, ran, run"
  lemmatization     "better"             "good"     (heavier, rare)
  synonym           "sneakers"           "sneakers, shoes"
  edge_ngram(1..5)  "fox"                "f","fo","fox" (typeahead)
  shingle (2)       "quick brown fox"    "quick brown","brown fox"
```

### 3.3 Stemming vs lemmatization

```
  STEMMING                           LEMMATIZATION
  ──────────                         ─────────────
  Algorithmic (Porter/Snowball)      Dictionary + POS tagging
  Fast, small footprint              Slower, larger resources
  "studies" → "studi"                "studies" → "study"
  "better"  → "better" (no change)   "better"  → "good"
  Good enough for search             Better for NLP / chatbots
```

Elasticsearch ships language-specific analyzers (`english`, `french`,
`arabic`, `cjk`, …) that pre-wire the appropriate stop-word list,
stemmer, and normaliser. In interviews, call out that you'd pick the
analyzer per-field based on the target language.

### 3.4 Index-time vs query-time: they must match

```
  INDEX TIME:   "Running Shoes"  →  [run, shoe]
  QUERY TIME:   "runners shoe"   →  [run, shoe]      matches
  QUERY TIME:   "runners shoe"   →  [runners, shoe]  DOES NOT MATCH

  Rule: the query analyzer must produce tokens that appear in the
  inverted index. Asymmetric analysis (e.g., edge_ngram at index,
  standard at query) is the usual pattern for autocomplete.
```

---

## 4. Lucene Segments & the Write Path

Lucene is **append-only** at its core. You never mutate a segment in
place — you write new segments and merge old ones. This single
decision explains most of Elasticsearch's tuning knobs.

### 4.1 The write path

```
  Client writes doc
        │
        ▼
  ┌────────────────────────┐
  │  Primary shard node    │
  │                        │
  │  1. Append to translog │   (durability: fsynced per request
  │     (WAL, on disk)     │    by default; survives crash)
  │                        │
  │  2. Add to in-memory   │   (doc is NOT yet searchable)
  │     indexing buffer    │
  └───────────┬────────────┘
              │
              │ every refresh_interval (default 1s)
              ▼
  ┌────────────────────────┐
  │  REFRESH               │   buffer → new in-memory segment,
  │  (makes doc searchable)│   open new searcher ⇒ near-real-time
  └───────────┬────────────┘
              │
              │ when too many segments / by merge policy
              ▼
  ┌────────────────────────┐
  │  MERGE                 │   combine small segments → bigger
  │  (background)          │   one; drop tombstoned deletes
  └───────────┬────────────┘
              │
              │ every flush (translog full / time)
              ▼
  ┌────────────────────────┐
  │  FLUSH                 │   fsync segment files; truncate
  │  (durability commit)   │   translog. Crash-safe point.
  └────────────────────────┘
```

### 4.2 Why updates are expensive

A Lucene segment is **immutable**. There is no "update doc 42":

```
  UPDATE doc 42
    1. Mark old doc 42 as deleted (tombstone bitset in segment)
    2. Index a new doc with the new contents (new segment)
    3. Merge later reclaims the tombstoned space

  Consequence:
   - High-update workloads cause write amplification + merge load
   - Deleting 1% of docs doesn't shrink disk until merge runs
   - "Update-heavy OLTP in ES" is a well-known anti-pattern
```

### 4.3 The refresh / translog / flush trade-off

```
  KNOB                 LOWER                 HIGHER
  ───────────────────  ────────────────────  ─────────────────────
  refresh_interval     Faster visibility,    Less CPU/IO,
                       more tiny segments,   docs visible later
                       more merge pressure   (use for bulk ingest)

  translog durability  request  = safer      async    = faster,
                                             window of data loss

  index.number_of_     more write paralleli  heavy rebalance,
     shards            sm, more overhead      tiny-shard waste
```

For bulk ingestion a common trick is to set `refresh_interval: -1`
and `number_of_replicas: 0` during load, then restore.

---

## 5. Elasticsearch Cluster Architecture

Elasticsearch is a distributed wrapper around Lucene. The distribution
layer adds three things: **sharding** (horizontal partition),
**replication** (HA + read scale), and **coordination** (routing,
merging responses).

### 5.1 The hierarchy of names

```
  CLUSTER           ─── "search-prod"
    │
    ├── Node A  (master-eligible, data, ingest)
    ├── Node B  (data)
    └── Node C  (data)

  INDEX             ─── "products"  (like a DB table)
    │   settings: number_of_shards=3, number_of_replicas=1
    │
    ├── Shard 0     ← a self-contained Lucene index
    ├── Shard 1
    └── Shard 2

  SHARD 0
    ├── Primary    on Node A
    └── Replica    on Node B

  DOCUMENT          ─── one JSON object with an _id
                         lives in exactly one primary shard
```

### 5.2 Routing a doc to a shard

```
  shard_id = hash(_routing) % number_of_primary_shards
  default _routing = _id

  IMPLICATION: you cannot change number_of_shards after creation
               without re-indexing (hash space changes).
```

That's why choosing shard count is a design decision. Rules of thumb:

```
  Rule                                   Why
  ─────────────────────────────────────  ────────────────────────
  Shard size:  10 GB – 50 GB sweet spot  Below: overhead. Above:
                                         slow recovery & merges.
  Total shards per GB heap:  ≤ 20        Memory per shard isn't free
  Primary shards sized for 1–2 yrs of    No online re-shard; plan
     growth                              for the horizon you have
  Use time-based indices for logs        index-per-day/week;
     (ILM + rollover)                    deletes = drop an index
```

### 5.3 Node roles

```
  ROLE            RESPONSIBILITY
  ──────────────  ───────────────────────────────────────────
  master          Cluster state, shard allocation, mapping
                  changes. Small footprint; 3 dedicated for HA.
  data            Holds shards; does the work (CPU + IO).
  ingest          Runs ingest pipelines (enrich, grok, etc.).
  coordinating    No data; fans out queries, gathers results.
                  (Every node can do this; dedicating it helps
                  hot reads.)
  ml              Runs ML inference / anomaly detection.
```

### 5.4 Replication: safety AND throughput

```
  PRIMARY      (accepts writes)
     │
     ├── async forwards to ──► REPLICA 1  (serves reads)
     │                         REPLICA 2  (serves reads)
     │
     └── Response to client once N copies ack
         (default: wait_for_active_shards = 1 = primary only;
          tune up for stronger durability)

  Writes: 1 primary + R replicas = R+1 writes
  Reads : round-robin across primary + replicas
```

---

## 6. Request Routing: Query-Then-Fetch

A search query is **scatter-gather**, not a point lookup. Elasticsearch
runs two phases.

```
  CLIENT                           COORDINATING NODE
    │    "search products for      ┌─────────────────────┐
    │    'running shoes' size 10"  │ 1. Parse query      │
    ├─────────────────────────────►│ 2. Choose ONE copy  │
                                   │    of each shard    │
                                   └──────────┬──────────┘
                                              │ scatter
                                ┌─────────────┼─────────────┐
                                ▼             ▼             ▼
                          ┌──────────┐  ┌──────────┐  ┌──────────┐
                          │ Shard 0  │  │ Shard 1  │  │ Shard 2  │
                          │  QUERY:  │  │  QUERY:  │  │  QUERY:  │
                          │ run BM25 │  │ run BM25 │  │ run BM25 │
                          │ return   │  │ return   │  │ return   │
                          │ top-10   │  │ top-10   │  │ top-10   │
                          │ (id+score│  │   ...    │  │   ...    │
                          │   only)  │  │          │  │          │
                          └────┬─────┘  └────┬─────┘  └────┬─────┘
                               └──────┬──────┴──────┬──────┘
                                      ▼             ▼
                             ┌─────────────────────────────┐
                             │ COORDINATOR (QUERY phase)   │
                             │  merges 3×10 = 30 → top 10  │
                             │  by score                    │
                             └──────────────┬──────────────┘
                                            │
                                            │ FETCH only 10 docs
                                            │ from the shards that
                                            │ own them
                                            ▼
                             ┌─────────────────────────────┐
                             │  Return fully-hydrated docs │
                             └─────────────────────────────┘

  Observation: each shard must return `from + size` docs for
  deep pagination, so `from=10000&size=10` becomes very expensive
  (the "deep paging" problem). Use search_after / point-in-time.
```

### 6.1 Why distributed scoring has a subtle bug

BM25's IDF uses `N` (total docs with the term). Each shard only knows
*its own* document frequency:

```
  True IDF needs global df across all shards.
  Default ES uses per-shard df → scores slightly differ across shards.

  Mitigations:
   - search_type=dfs_query_then_fetch   (extra round trip to
     gather global df first; 2× latency)
   - _routing on a business key so related docs land on one shard
   - Many shards of similar data → statistical convergence;
     in practice the drift is tiny
```

---

## 7. Relevance Scoring: TF-IDF and BM25

Scoring asks: *"how well does this document match this query?"* Every
retrieval engine has a scoring function; Lucene defaults to **BM25**
as of Lucene 6 (2016).

### 7.1 TF-IDF — the intuition

```
  SCORE(doc d, query q) = Σ over terms t in q:
        TF(t in d)   ×   IDF(t)

  TF  = how often  t   appears in  d      → more → more relevant
  IDF = how rare   t   is in corpus       → rarer → more meaningful
```

Sample IDF:

```
  "the"           appears in 90% of docs  → IDF tiny    (near 0)
  "brown"         appears in 10%          → IDF medium
  "elasticsearch" appears in 0.1%         → IDF high
```

So in the query `"the elasticsearch"`, a doc that matches
`"elasticsearch"` scores *much* higher than one that matches `"the"`.

### 7.2 BM25 — the math, explained

```
           Σ        IDF(t)          · (k₁ + 1) · tf(t, d)
  BM25 =  t in q             ────────────────────────────────────
                            tf(t,d) + k₁ · (1 − b + b · |d| / avg_dl)

    k₁   TF saturation    (default 1.2)
    b    length norm      (default 0.75)
    |d|  length of doc d in tokens
    avg_dl  average doc length in the corpus
```

Three behaviours fall out:

```
  BEHAVIOUR                   INTUITION
  ──────────────────────────  ──────────────────────────────────
  Diminishing returns on tf   10 "cats" isn't 10× as relevant
                              as 1 "cat". BM25 saturates.

  Short docs score higher     Matching "cat" in a 5-word title
  for the same tf             beats matching "cat" in a 5000-word
                              article. Controlled by b.

  Rare terms dominate         IDF is logarithmic → long-tail
                              terms drive the score.
```

### 7.3 Saturation curves — why BM25 beats naive TF-IDF

```
  score
    │                                       TF-IDF (linear)
    │                                  /
    │                             /
    │                       /
    │             ___________________  BM25 (saturates)
    │      __/
    │ __/
    │/
    └───────────────────────────────────► term frequency (tf)

  BM25 says: "once you've seen cat 10 times, the 11th doesn't matter."
```

---

## 8. End-to-End Search System Architecture

Putting it all together — what a real product-search service looks
like at a 100M-doc scale:

```
  USER: "blue running shoes under $80"
        │
        ▼
  ┌───────────────────┐
  │  Edge / CDN       │  cache popular result pages (eTag / Vary)
  └─────────┬─────────┘
            ▼
  ┌───────────────────┐
  │  API Gateway      │  auth, rate limit, region routing
  └─────────┬─────────┘
            ▼
  ┌───────────────────────────────────────────────────────┐
  │  QUERY UNDERSTANDING                                   │
  │   • spell-correct  ("shoos" → "shoes")                │
  │   • tokenize + stem                                   │
  │   • synonym expansion  ("sneakers" | "trainers")      │
  │   • entity recognition ("blue" = colour facet)        │
  │   • intent classification (search vs browse vs nav)   │
  └─────────┬─────────────────────────────────────────────┘
            ▼
  ┌───────────────────────────────────────────────────────┐
  │  RETRIEVAL (top-k, recall focus)                      │
  │                                                        │
  │    BM25 / lexical       Vector / semantic             │
  │    (Elasticsearch)      (FAISS / HNSW / Vespa)        │
  │         │                      │                      │
  │         └────── Hybrid (RRF) ──┘                      │
  │                                                        │
  │  Filters applied inside engine:                       │
  │    colour = "blue", price < 80, in_stock = true       │
  └─────────┬─────────────────────────────────────────────┘
            │  ~1000 candidates
            ▼
  ┌───────────────────────────────────────────────────────┐
  │  RANKING (top-k, precision focus)                     │
  │   • Learning-to-Rank model (LambdaMART / XGBoost)     │
  │   • Features: BM25 score, CTR, margin, personalisation│
  │   • Business boosts: sponsored, freshness, in-stock   │
  └─────────┬─────────────────────────────────────────────┘
            │  ~50 ranked docs
            ▼
  ┌───────────────────────────────────────────────────────┐
  │  POST-PROCESSING                                      │
  │   • Diversify by brand / category                     │
  │   • Dedupe near-duplicates                            │
  │   • Sponsored slot injection                          │
  │   • Facet computation (aggregation query)             │
  └─────────┬─────────────────────────────────────────────┘
            ▼
  ┌───────────────────────────────────────────────────────┐
  │  RESPONSE  (results + facets + spell-suggest)         │
  └─────────┬─────────────────────────────────────────────┘
            │
            │         ┌─────────────────────────────────┐
            │         │  TELEMETRY LOOP                 │
            ├─────────┤  clicks / conversions →         │
            │         │  training data for LTR,         │
            │         │  A/B-test evaluation (nDCG)     │
            │         └─────────────────────────────────┘
            ▼
         USER
```

The **retrieval vs ranking** split is the single most important
architectural pattern in modern search:

```
  STAGE       GOAL         # OF DOCS   SPEED CONSTRAINT
  ──────────  ───────────  ──────────  ─────────────────────
  Retrieval   recall       1M → 1000   cheap per doc, massive
  Ranking     precision    1000 → 50   rich features, ML-heavy
  Post-proc   UX/business  50 → 20     easy
```

---

## 9. Autocomplete & Typeahead

Users type `"n"`, then `"ne"`, then `"net"`. Each keystroke is a
query; the whole thing must feel instantaneous (p99 ≤ 50 ms). You do
**not** want to BM25 over 100 M docs on every keystroke.

### 9.1 Trie / FST — the classic

```
  Dictionary: {net, netflix, network, netty, nebula}

              (root)
                │
                n
                │
               ┌e┐
               │ │
               b t
               │ ├─── t ── l ── i ── x   ("netflix")
       u       │ └─── w ── o ── r ── k   ("network")
       │       │
       l       └─── t ── y               ("netty")
       │
       a                                  ("nebula")

  Type "ne" → walk to node "e", DFS all descendants, rank by
              frequency/weight, return top 10.
```

Elasticsearch's **Completion Suggester** stores an **FST** (Finite-
State Transducer) per segment — a trie that also shares *suffixes*,
making it dramatically smaller. FSTs live in memory for O(prefix-
length) lookup.

### 9.2 Edge n-grams — when prefix-only isn't enough

```
  token "elasticsearch"
    edge_ngram(min=2, max=10)
      → ["el","ela","elas","elast","elasti","elastic",
         "elastics","elasticse","elasticsear"]

  All of these go into the inverted index.
  Query "elast" hits via exact-match. Works as normal BM25, so
  you also get ranking + filters — which the completion suggester
  does NOT give you.

  Cost: ~5-10× index-size bloat.
```

### 9.3 Choosing between approaches

```
  NEED                                 USE
  ──────────────────────────────────   ─────────────────────
  Pure prefix, max speed, 1M-item      Completion Suggester (FST)
  lists
  Infix match ("bay" matching          Edge n-gram + match query
  "ebay")
  Typo tolerance                       + fuzzy (edit distance 1-2)
  Rich ranking + filters               search_as_you_type field
  (location, CTR, personalised)
  Cross-lingual                        n-gram on normalised form
```

### 9.4 Twitter-scale: cached prefix → top-K

Beyond in-engine features, big-traffic autocomplete systems store a
pre-computed hash `prefix → top-K suggestions` in Redis, warmed from
query logs and refreshed hourly. Elasticsearch / FST is the fallback
for long-tail prefixes.

---

## 10. Geospatial Indexing

"Find drivers within 2 km" is a *range* query in 2D. B-trees are 1D.
Spatial indexes solve this by mapping 2D to 1D while preserving
locality.

### 10.1 Geohash — Base32-encoded Z-curve

```
  Interleave bits of (lat, lon), encode 5 bits per Base32 char.

     "9q8yy"   →   San Francisco, ~5 km cell
     "9q8yyk"  →   ~600 m cell
     "9q8yyk9" →   ~80 m cell

  Key property: SHARED PREFIX ⇒ SPATIALLY NEAR

  ┌──────┬──────┐
  │9q8yy │9q8yz │   shared prefix "9q8y"
  ├──────┼──────┤   ⇒ neighbours
  │9q8yw │9q8yx │
  └──────┴──────┘

  "Nearby drivers":
   1. user_hash = encode(lat,lon, precision=6)
   2. SQL:  WHERE geohash LIKE '9q8yyk%'            (one cell)
                 OR geohash IN (<8 neighbour cells>)  (edge case)
   3. Compute exact distance on returned rows, filter.

  Caveats:
   - Cells are rectangular & vary by latitude (bad near poles)
   - A user near a cell boundary can be 1m from the edge but
     you must also query neighbours → "kring" of 9 cells
```

### 10.2 Quadtree — adaptive subdivision

```
  Start with one cell = whole world. If cell has > threshold points,
  split into 4 quadrants. Recurse on hot areas only.

     ┌───────────┬───────────┐
     │     NW    │     NE    │
     │           │ ┌──┬──┐   │
     │           │ ├──┼──┤   │   dense city area
     │           │ └──┴──┘   │   → further subdivision
     ├───────────┼───────────┤
     │    SW     │    SE     │
     │           │           │   sparse ocean area
     │           │           │   → stays a single cell
     └───────────┴───────────┘

  Used by:  Uber's dispatch previously; many geo DBs.
  Queries:  walk down the tree only into cells whose bounding box
            intersects the query radius.
```

### 10.3 Google S2 — Hilbert curve on a sphere

```
  - Projects Earth onto 6 faces of a cube.
  - Each face covered by a Hilbert space-filling curve.
  - 31 levels; level 12 ≈ 1 km², level 20 ≈ 5 m².
  - 64-bit integer "CellId".

  Hilbert curve is a better space-filling curve than the Z-order used
  by geohash — successive cells are always edge-adjacent, which makes
  range queries tighter (fewer "false positive" cells to scan).

  Used by Google Maps; supported in CockroachDB, BigQuery, Snowflake.
```

### 10.4 H3 — Uber's hexagonal grid (2018)

```
  - Tiles the globe in HEXAGONS (icosahedron-projected).
  - 15 levels; level 9 ≈ 0.1 km² (city block).
  - 64-bit int H3 index.

  Why hexagons?
    Each cell has exactly 6 neighbours, all at the same distance.
    Squares have 4 edge- + 4 corner-neighbours at DIFFERENT distances
    → makes "radius-k" neighbourhood queries uneven.

          ___              ___   ___
         /   \            /   \_/   \
         \___/   vs.      \___/     \
         /   \            /   \_____/   squares: mixed distances
         \___/            \___/         hexes : uniform distances

  Uber uses H3 for:
    - driver supply indexing
    - surge pricing cells
    - demand heatmaps
```

### 10.5 Choosing

```
  CHOOSE        WHEN
  ────────────  ──────────────────────────────────────
  Geohash       Simple, prefix search in any SQL DB,
                non-critical precision near poles
  Quadtree      Highly non-uniform density, in-memory
                dynamic workloads
  S2            Global accuracy, need good locality,
                library ecosystem (Google, CockroachDB)
  H3            Uniform neighbour distances, analytics
                and visualisation workloads
```

---

## 11. Vector & Semantic Search

Keyword search fails when the user's words aren't in the document:

```
  Query:     "how to fix a car that won't start"
  Document:  "troubleshooting an engine that fails to crank"

  BM25:  0 overlap → score ~0 → no match.
  Vector: both sentences map to nearby points in embedding space.
```

### 11.1 The pipeline

```
  INDEX TIME                        QUERY TIME
  ──────────                        ──────────
  doc text                          user query
     │                                  │
     ▼                                  ▼
  ┌────────────────┐               ┌────────────────┐
  │ Embedding model│               │ Embedding model│
  │ (e.g. 1536-d   │               │ (same model!)  │
  │  OpenAI / BGE) │               │                │
  └───────┬────────┘               └───────┬────────┘
          │ vector v_d                     │ vector v_q
          ▼                                ▼
  ┌────────────────────────────────────────────────┐
  │           VECTOR INDEX  (e.g. HNSW)            │
  │                                                 │
  │   find k nearest v_d to v_q by cosine /        │
  │   dot product, pruning via graph traversal     │
  └─────────────────────┬──────────────────────────┘
                        ▼
                  top-k doc IDs
```

### 11.2 HNSW — Hierarchical Navigable Small-World

```
  Layer 2 (sparse, long-range):   A ────── F ─── K
                                  │        │     │
  Layer 1:                        A ── C ──F─ H ─K ── M
                                  │    │   │ │  │    │
  Layer 0 (all vectors, dense):   A─B─C─D─E─F─G─H─I─J─K─L─M

  Search:
    Start at entry point (top layer, long hops).
    Greedy-descend toward query in each layer until no neighbour is
    closer. Drop down a layer and continue.
    O(log N) nodes touched in practice.

  Params:
    M               graph degree (higher → denser graph, better recall)
    efConstruction  index build effort
    ef              search effort (higher → better recall, slower)
```

### 11.3 ANN trade-off triangle

```
                    RECALL
                     /  \
                    /    \
                   /      \
                  /        \
                 /   ANN    \
                /  algorithms\
               /____________ \
            LATENCY         MEMORY

  - Pick 2, compromise on 3rd.
  - HNSW: recall + latency great; memory hungry.
  - IVF-PQ (Faiss): memory + latency great; recall tunable.
  - Brute force: recall perfect; latency terrible on millions.
```

### 11.4 Where vectors live in the stack

```
  SYSTEM                      NOTE
  ──────────────────────────  ─────────────────────────────────
  Elasticsearch 8+ / OpenSearch dense_vector + kNN (HNSW)
  Pinecone                    managed vector DB
  Weaviate, Qdrant, Milvus    open-source vector DBs
  pgvector (Postgres)         store beside transactional data
  Redis (RedisVL)             vector + BM25 in one store
  Vespa                       retrieval + ranking in one engine
```

---

## 12. Hybrid Search & Reciprocal Rank Fusion

Vector alone misses exact matches. BM25 alone misses semantics. Modern
RAG and e-commerce search run both and **fuse**.

### 12.1 Why hybrid wins

```
  QUERY TYPE                       BM25    VECTOR   HYBRID
  ────────────────────────────────  ──────  ───────  ──────
  exact SKU ("SKU-1234-AB")          WIN    lose      WIN
  error code ("HTTP 503")            WIN    lose      WIN
  natural language                   lose    WIN      WIN
  synonyms / paraphrase              lose    WIN      WIN
  typo                               lose    WIN      WIN
  rare proper noun + context         mixed  mixed     WIN
```

### 12.2 Reciprocal Rank Fusion (RRF)

```
  Parameter-free, score-normalisation-free fusion:

    RRF_score(d) =  Σ   1 / (k + rank_i(d))
                   i

    where i iterates over the retrievers (BM25, vector, …),
    rank_i(d) is d's rank in retriever i's result list,
    k is a smoothing constant (typical: 60).

  Example:

    Doc     BM25 rank   Vector rank     RRF (k=60)
    ────    ─────────   ───────────    ─────────────
    A          1            50         1/61 + 1/110 = 0.0255
    B          3            2          1/63 + 1/62  = 0.0320   ← winner
    C          ∞            1                0 + 1/61 = 0.0164
    D          2            ∞          1/62 +  0    = 0.0161

  Key properties:
    - no score-scale problem   (BM25 ≈ 0-30, cosine ∈ [-1,1])
    - robust to missing lists  (a doc only seen by one retriever
                                still contributes)
    - one knob (k); insensitive to it
```

### 12.3 Architecture

```
  query
    │
    ├──► BM25 top-1000   ─┐
    │                      │
    └──► kNN  top-1000   ─┤   ┌──────────┐      ┌─────────────┐
                          └──►│   RRF    │─────►│ cross-encoder│──► top-20
                          ┌──►│  merge   │      │ rerank (opt)│
    ┌──► sparse (SPLADE) ─┘   └──────────┘      └─────────────┘
    │    top-1000
    └── (optional 3rd leg)
```

A **cross-encoder reranker** (a transformer that takes
`(query, doc)` as input and outputs one relevance score) is often
bolted on top of RRF for the final 50 → 20 squeeze. Expensive, so only
used on the shortlist.

---

## 13. Learning to Rank & Personalization

Once recall is good enough, the problem becomes "sort the 1000 hits
so the best ones are in the top 10". That's a supervised ML problem.

### 13.1 Features

```
  CATEGORY          EXAMPLES
  ────────────────  ────────────────────────────────────
  Query-doc         BM25, cosine(v_q, v_d), # term matches,
                    title match, category match
  Document          popularity, CTR last 30d, price,
                    rating, freshness, in-stock
  Query             length, language, has-brand, intent
                    (nav/informational/commercial)
  User              past clicks, geography, device,
                    logged-in vs anon, LTV bucket
  Session           previous queries, time on site,
                    items in cart
```

### 13.2 Models

```
  MODEL                    NOTES
  ───────────────────────  ─────────────────────────────
  Logistic regression      Baseline, interpretable
  GBDTs (XGBoost, LightGBM Industry standard
     + LambdaMART)
  Neural (DNN / DCN-V2)    More data → more lift
  Cross-encoders (BERT-    Heavy; only on top-K rerank
     rerank, ColBERT)
```

### 13.3 The training data problem

```
  EXPLICIT labels           Human raters, graded 0–4.
                            Expensive, low-bias, few queries.

  IMPLICIT labels           Click logs, conversions.
                            Cheap, many queries, BIASED:
                              - position bias (top = more clicks)
                              - presentation bias (big image = click)
                              - selection bias (you don't see
                                clicks on docs you never showed)

  Counter-measures:          Inverse propensity weighting,
                             click models (PBM, cascade),
                             randomised interleaving tests.
```

### 13.4 Offline vs online metrics

```
  OFFLINE     CALCULATED FROM         USE FOR
  ──────      ─────────────────       ─────────────
  nDCG@10     judgment lists          model selection
  MRR         first relevant rank     navigational queries
  Recall@1K   did retrieval cover it? retrieval stage

  ONLINE       MEASURED IN PROD       USE FOR
  ──────       ────────────────       ───────────
  CTR@1..10    click-through          engagement
  conversion   purchase / signup      business value
  zero-result  % queries with 0 docs  coverage gaps
  p50/p99 lat  ms                     perf regressions
```

---

## 14. Real-World Case Studies

### 14.1 Amazon product search — A9

```
  Retrieval:   ES-like inverted index on titles + bullets + Q&A,
               + vector embeddings for long-tail queries.
  Filters:     category, price, Prime-eligible, in-stock.
  Ranking:     learn-to-rank GBDT, features include CTR,
               conversion rate, margin, review score, delivery ETA.
  Personalisation: user embedding from clicks/orders (session + 30d).
  Scale:       hundreds of millions of docs, multi-region, p99 < 250 ms.
```

### 14.2 GitHub code search (Blackbird, 2023+)

```
  - Custom search engine (not ES) — 50M+ repos.
  - Uses NGRAM-based trigram index over source code for substring
    search (regex-capable).
  - Separate ranking layer for repo popularity, fork-of-fork dedup,
    path weighting (tests < src).
  - Why not ES? Token-level search matches word boundaries; code
    queries often need substrings inside identifiers
    ("camelCaseIdent", operator ~=).
```

### 14.3 Uber driver dispatch

```
  - Driver locations streamed → H3 cell index in Redis / in-memory.
  - "Nearest N drivers" = H3 kring of radius r, rank by ETA model
    (not just distance: traffic, driver rating, surge fairness).
  - Heatmaps + demand forecasting on the SAME H3 cells.
```

### 14.4 Log search — ELK / Grafana Loki

```
  - Time-based indices (one per day / hour) with ILM:
      hot  → SSD, current day
      warm → HDD, last week
      cold → object store, older
      delete at 90d
  - Rollover when an index hits size/age threshold.
  - Operators use Kibana/Grafana to do ad-hoc facet queries:
      "error rate by service by 5-min bucket, last 24h"
  - Loki takes a different tack: INDEX only labels, store logs
    as compressed chunks. Cheap; slower full-text search.
```

### 14.5 Google Search (in broad strokes, from public papers)

```
  - Inverted index sharded by doc (per-shard BM25-like scoring).
  - RankBrain / MUM / BERT re-rank top results with NN models.
  - Knowledge graph answers / featured snippets intercept before
    the 10 blue links.
  - Personalisation + geo + freshness + authority (PageRank + sitewide
    quality signals) all feed the ranker.
  - Spanner-backed index updates, continuous rollout, no "downtime".
```

---

## 15. Capacity, Sizing & Operational Pitfalls

### 15.1 Sizing a cluster (back-of-envelope)

```
  INPUTS                              EXAMPLE
  ────────────────────────            ─────────────
  docs         =  100 M                   100 M
  avg doc size =  1 KB                    100 GB raw
  indexing x   =  ~1.3× (postings,        130 GB
                  doc values, norms)
  replicas     =  1                       260 GB total
  target shard =  30 GB                   ~9 primary shards
  heap per node=  32 GB                    (hard ceiling)
  shards/node  =  ≤ 20 per GB-heap        ~600 shards / node
                    (so 9 primaries fit on 1 node easily)
  CPU          =  ~1 vCPU per 1K qps      @ p95 query cost
  memory       =  2× shard size in OS     fs-cache for hot data
                  page cache ideally
```

### 15.2 The anti-patterns

```
  PROBLEM               SYMPTOM                  FIX
  ────────────────────  ───────────────────────  ─────────────────────
  Too many shards       heap pressure, slow      plan for 10-50 GB
                        cluster state            shards, use ILM
  Hot shard             one node pinned at 100%  pick a better _routing,
                                                 split index
  Update-heavy          merge pressure, CPU      move hot state to
                        spikes, latency noise    DB or stream rebuilds
  Deep pagination       heap OOM at high `from`  use search_after /
                                                 point-in-time
  Mapping explosion     dynamic mapping          use strict mapping;
                        thousands of fields      reject unknown fields
  Wildcard leading *    full index scan per      edge_ngram or reverse
                        shard                    token fields
  Unbounded aggregation 2GB+ in-memory buckets   composite aggs,
                                                 partition, sample
  Missing warmup        cold cache after deploy  pre-warm queries or
                                                 use searchable snapshots
```

### 15.3 High-availability topology

```
  3 dedicated master-eligible nodes   (never 2 → split-brain)
  ≥ 2 data tiers (hot, warm, cold)    (ILM policies move data)
  ≥ 1 replica per shard               (RF=2 = primary + 1)
  Cross-cluster replication (CCR)     for disaster recovery
  Snapshot to S3 / GCS on schedule    point-in-time restore
```

---

## 16. Design Checklist

Before you close your laptop:

```
  DATA & MAPPING
  □  Language analyzer chosen per text field
  □  Strict mapping + explicit types (no dynamic surprises)
  □  ID strategy and routing key decided
  □  Shard count sized for 1-2 yrs growth (no online re-shard)
  □  Replica count reflects HA + read-qps needs

  INDEXING
  □  Write path: translog durability vs throughput
  □  Refresh interval set for visibility SLO
  □  Merge policy reviewed; bulk ingestion plan (refresh off)
  □  Update frequency is sane (not "update 1k docs/sec/doc")

  QUERY
  □  Retrieval stage (BM25 / vector / hybrid) chosen
  □  Filters applied INSIDE the engine, not post-fetch
  □  No deep pagination; search_after / PIT for exports
  □  Query timeout and circuit breakers configured

  RANKING
  □  Default BM25 good enough? Or do we need LTR / rerank?
  □  Business boosts documented (freshness, sponsored)
  □  Offline eval metric (nDCG@10 or similar) baselined

  OPS
  □  3 master-eligibles
  □  ILM / rollover for time-series data
  □  Snapshot schedule + verified restore
  □  Dashboards: p50/p99 latency, indexing rate, GC, heap,
     rejected tasks, queue depth, shard skew
  □  Alerts on cluster RED, heap > 75%, shard skew
  □  Cross-region / cross-cluster replication for DR
```

---

## 17. Interview Q&A

**Q1. Why can't we just use `SELECT * WHERE name LIKE '%query%'`?**
Leading-wildcard LIKE can't use a B-tree, so every query is a full
scan. No ranking, no stemming/synonyms, no phrase or proximity, no
facets. Fine for a toy, disastrous at scale. The inverted index
solves all four in one structure.

**Q2. Walk me through what happens when a doc is indexed in ES.**
Client → primary shard node → append to translog (WAL, fsynced) →
add to in-memory indexing buffer → on refresh (default 1s) a new
immutable Lucene segment is written and the doc becomes searchable
(near-real-time) → segments are merged in the background → flush
fsyncs and truncates the translog. Primary asynchronously
replicates the operation to replica shards.

**Q3. You have 3 shards and search requests a top-10 from shard=0.
How does ES find the global top-10?**
Query-then-fetch. The coordinating node fans out `from+size` to
**every** shard, each shard returns its local top-10 (ids + scores),
the coordinator merges 30 into a global top-10 by score, then
fetches the full `_source` for only those 10 from the owning shards.

**Q4. What's the difference between BM25 and TF-IDF?**
Both multiply TF by IDF, but BM25 adds (a) **TF saturation** via
`k1`, so the 11th occurrence of a term barely adds to the score,
and (b) **field-length normalisation** via `b` and `|d|/avg_dl`, so
a match in a short title beats the same match in a long document.

**Q5. When would you pick vector search over BM25?**
When the user's words don't overlap the doc's words but the
meaning does — paraphrase, synonyms, multilingual, conversational
queries, RAG over documentation. When you need exact tokens
(product codes, error codes, SKUs, identifiers), BM25 still wins.
In production, run both and fuse with RRF.

**Q6. How does Reciprocal Rank Fusion work and why not just add
scores?**
RRF sums `1/(k + rank_i(d))` over retrievers. It sidesteps two
problems: (1) BM25 and cosine are on completely different scales —
simple weighted sums force you to re-normalise after every model
change; (2) docs missing from one list still contribute via the
other. It's parameter-light and robust, hence widely adopted for
hybrid search.

**Q7. You need autocomplete over 50M product names. How?**
Completion Suggester (FST) at index time for pure prefix —
in-memory, ~millisecond response. Add edge n-grams if you need
infix or filter-aware ranking. Cache the top-K per popular prefix
in Redis for the high-QPS long-tail. Fallback to a full search for
uncovered prefixes.

**Q8. Design the storage for "drivers near me" at Uber scale.**
Every driver heartbeats location; encode to **H3 cell id** at two
resolutions (fine for dispatch, coarse for dashboards). Store
`cell → set of driverIds` in Redis. Lookup: compute user H3 cell,
k-ring of radius r (6, 18, 36 hex neighbours...), union sets,
fetch driver details, rank by ETA model (not raw distance — include
traffic, rating, surge). Stream driver updates on Kafka; rebuild
cell sets asynchronously.

**Q9. You index 100M docs but search p99 has degraded to 2s. How
do you debug?**
Check shard skew (is one shard 10× bigger?), segment count (too many
tiny segments = slow query), heap usage (GC pauses?), fielddata vs
doc values usage, slow-log for expensive queries (leading wildcards,
script scores, massive aggregations), hot threads API, and look at
`from+size` deep pagination or unbounded aggregations. Nine times
out of ten it's one bad query pattern or a hot shard.

**Q10. Can Elasticsearch be your primary data store?**
No, as a rule. It's NRT, not real-time; its refresh-then-search
model and lack of cross-shard ACID transactions mean you can lose
or duplicate writes under partitions. It's also bad at heavy
per-doc updates. Keep the source of truth in an OLTP DB (or
Kafka), index to ES asynchronously via CDC, and treat the ES
index as a derived, rebuild-able view.

**Q11. How do you handle "re-indexing the world" when you change
the analyzer?**
Create a new index with the new mapping/analyzer, **reindex** from
the old one (ES has a `_reindex` API; for very large indices, use
a pull-based stream off Kafka / CDC). Use an **alias** so clients
read from `products` (alias) instead of `products_v5`; flip the
alias to `products_v6` atomically when done. Delete the old index.

**Q12. Shard count: what, why, and can you change it?**
Shards are the unit of parallelism and distribution. You pick a
number at index creation and **cannot change primaries without
reindexing** (routing hashes over that count). Pick for 1-2 years
of growth aiming at 10-50 GB per shard. Use **rollover + ILM** for
time-series so you don't need to predict forever — create new
indices instead.

---

## Key Takeaways

1. **Inverted index** — term → posting list of (docId, tf, positions).
   Every transformation you do on indexing (tokenise, lowercase,
   stem) must match on querying.
2. **Lucene segments are immutable** — that's why refresh, merge,
   and translog exist, and why updates are expensive.
3. **Elasticsearch = sharded + replicated Lucene**. Pick shard count
   for the future; you can't reshard live.
4. **Query-then-fetch** is scatter-gather: every shard returns
   `from+size`, coordinator merges.
5. **BM25** beats TF-IDF with TF saturation and doc-length
   normalisation. It's the default and usually the right call.
6. **Retrieval vs Ranking** is the single most important split in a
   modern search system. Retrieval = recall, ranking = precision.
7. **Geohash / Quadtree / S2 / H3** for spatial — pick based on
   uniformity and ecosystem.
8. **Hybrid search (BM25 + vector, fused with RRF)** is the modern
   baseline for anything that feels "semantic" — RAG, docs, FAQ,
   natural-language e-commerce.
9. **ES is NOT your primary data store** — feed it from a source of
   truth and treat the index as a derived, rebuild-able view.
10. **Almost every ES production incident** reduces to: hot shard,
    deep pagination, mapping explosion, update-heavy workload, or
    unbounded aggregation. Recognise them on sight.
