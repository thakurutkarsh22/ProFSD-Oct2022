# Search & Indexing

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Good to Know

---

## 1. Inverted Index

The foundation of full-text search. Maps words to documents containing them.

```
DOCUMENTS:
  Doc 1: "The quick brown fox"
  Doc 2: "The quick rabbit"
  Doc 3: "Brown fox jumps"

FORWARD INDEX (what we normally think):
  Doc 1 → [the, quick, brown, fox]
  Doc 2 → [the, quick, rabbit]
  Doc 3 → [brown, fox, jumps]

INVERTED INDEX (what search engines use):
  ┌──────────┬───────────────┐
  │ Term     │ Document IDs  │
  ├──────────┼───────────────┤
  │ the      │ [1, 2]        │
  │ quick    │ [1, 2]        │
  │ brown    │ [1, 3]        │
  │ fox      │ [1, 3]        │
  │ rabbit   │ [2]           │
  │ jumps    │ [3]           │
  └──────────┴───────────────┘

Search "brown fox" → brown ∩ fox = [1, 3] → Doc 1, Doc 3
```

---

## 2. Elasticsearch

The most commonly used search engine in system design interviews.

```
ELASTICSEARCH ARCHITECTURE:

  ┌────────────────────────────────────────────────────────┐
  │                 ELASTICSEARCH CLUSTER                    │
  │                                                        │
  │  Index: "products" (like a database table)             │
  │  ┌───────────────────────────────────────────────┐    │
  │  │ Shard 0 (Primary)  │  Shard 1 (Primary)      │    │
  │  │ on Node A          │  on Node B               │    │
  │  │ [doc1, doc4, doc7] │  [doc2, doc5, doc8]      │    │
  │  ├────────────────────┼──────────────────────────┤    │
  │  │ Shard 0 (Replica)  │  Shard 1 (Replica)      │    │
  │  │ on Node B          │  on Node C               │    │
  │  └───────────────────────────────────────────────┘    │
  │                                                        │
  │  Node A          Node B          Node C                │
  │  (Master)        (Data)          (Data)                │
  └────────────────────────────────────────────────────────┘

  Concepts:
  - Index = Collection of documents (like a DB table)
  - Document = JSON object (like a row)
  - Shard = Horizontal partition of an index
  - Replica = Copy of a shard for HA
```

### When to Use Elasticsearch
```
USE ELASTICSEARCH FOR:                 DON'T USE FOR:
✓ Full-text search                     ✗ Primary data store (no ACID)
✓ Log aggregation (ELK stack)          ✗ Frequent updates to documents
✓ Auto-complete / typeahead            ✗ Relational queries / joins
✓ Faceted search (filters)             ✗ Real-time analytics (use ClickHouse)
✓ Geo-spatial search                   ✗ Transactions

Systems that use search:
- E-commerce product search (Amazon)
- Log analysis (ELK: Elasticsearch + Logstash + Kibana)
- Social media search (Twitter)
- Autocomplete suggestions
```

---

## 3. Search System Architecture

```
TYPICAL SEARCH ARCHITECTURE:

  User: "blue running shoes"
    │
    ▼
  ┌──────────────┐
  │  API Server  │
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐     1. Tokenize: ["blue", "running", "shoes"]
  │ Query Parser │     2. Remove stop words
  │              │     3. Stemming: "running" → "run"
  └──────┬───────┘     4. Synonym expansion: "shoes" → "shoes", "sneakers"
         │
         ▼
  ┌──────────────┐
  │  Search      │     Query inverted index
  │  Engine      │     Apply filters (size, price, brand)
  │ (Elastic-    │     Score by relevance (TF-IDF / BM25)
  │  search)     │
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐
  │  Ranking     │     Boost by: popularity, recency, personalization
  │  Service     │     A/B test different ranking algorithms
  └──────┬───────┘
         │
         ▼
  Results: [{shoe1, score:0.95}, {shoe2, score:0.87}, ...]
```

### Relevance Scoring
```
TF-IDF (Term Frequency - Inverse Document Frequency):

  TF  = How often the term appears in THIS document
  IDF = How rare the term is across ALL documents
  
  Score = TF × IDF
  
  "the" → TF high, IDF very low (appears everywhere) → low score
  "elasticsearch" → TF moderate, IDF high (rare) → high score

BM25 (improved TF-IDF):
  Used by Elasticsearch by default.
  Adds document length normalization.
  Short documents with matching terms score higher.
```

---

## 4. Geospatial Indexing

Used for "find nearby" queries (restaurants, drivers, friends).

```
GEOHASH:
  Divides the world into grid cells, each with a string code.
  Longer string = smaller (more precise) area.

  ┌────────────┬────────────┐
  │   9q8yy    │   9q8yz    │
  │            │            │
  ├────────────┼────────────┤    Geohash "9q8y" covers
  │   9q8yw    │   9q8yx    │    a city-block-sized area
  │     ● User │            │
  ├────────────┼────────────┤
  │   9q8yq    │   9q8yr    │
  │            │            │
  └────────────┴────────────┘

  "Find restaurants near me":
  1. Get user's geohash: "9q8yw"
  2. Get neighboring cells: "9q8yy", "9q8yx", "9q8yq", ...
  3. Query all restaurants in those cells
  4. Calculate exact distance, sort by proximity


QUADTREE:
  Recursively divide space into 4 quadrants.
  Dense areas get more subdivisions.

       ┌──────┬──────┐
       │  NW  │  NE  │
       │      │●●●●  │ ← Dense area,
       ├──────┼──┬──┤│    subdivide further
       │  SW  │  │  ││
       │   ●  │──┼──┘│
       └──────┴──┴────┘

  Used by: Uber (finding nearby drivers)
```

---

## 5. Key Takeaways for Interviews

1. **Inverted index** is the core of any text search — know how it works
2. **Elasticsearch** is the default answer for search in system design
3. **ES is NOT a primary data store** — use it alongside your main DB
4. **Geohash or Quadtree** for location-based queries
5. **BM25** for relevance scoring (just know it exists, no need for math)
6. **Search pipeline**: Parse → Tokenize → Search index → Rank → Return
