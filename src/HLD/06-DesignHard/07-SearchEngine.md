# Design Search Engine (Google-like)

> **Difficulty:** Hard | **Frequency:** ★★★★☆ | **Companies:** Google, Microsoft (Bing), Amazon

---

## 1. Requirements

### Functional
- Crawl the web and build an index
- Given a search query, return ranked results
- Autocomplete suggestions (covered separately)
- Spell correction
- Snippet generation

### Non-Functional
- Low latency (< 500ms for search results)
- Fresh index (new content indexed within hours)
- Billions of web pages indexed

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                      SEARCH ENGINE                                    │
│                                                                      │
│  OFFLINE PIPELINE (Indexing):                                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐    │
│  │  Web     │──►│  Content │──►│  Index   │──►│  Inverted    │    │
│  │ Crawler  │   │  Parser  │   │ Builder  │   │  Index Store │    │
│  │          │   │          │   │          │   │  (sharded)   │    │
│  │ BFS,     │   │ Extract  │   │ Tokenize │   └──────────────┘    │
│  │ frontier │   │ text,    │   │ Stem     │                        │
│  │          │   │ links,   │   │ Build    │   ┌──────────────┐    │
│  │          │   │ metadata │   │ inverted │──►│  PageRank    │    │
│  └──────────┘   └──────────┘   │ index    │   │  Scores      │    │
│                                └──────────┘   └──────────────┘    │
│                                                                      │
│  ONLINE PIPELINE (Serving):                                         │
│  ┌──────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐           │
│  │User  │──►│  Query   │──►│  Index   │──►│ Ranking  │──►Results │
│  │Query │   │ Parser   │   │ Lookup   │   │ Engine   │           │
│  └──────┘   │          │   │          │   │          │           │
│             │-Tokenize │   │-Search   │   │-PageRank │           │
│             │-Spell chk│   │ inverted │   │-Freshness│           │
│             │-Synonyms │   │ index    │   │-Relevance│           │
│             │-Intent   │   │-Score    │   │-Quality  │           │
│             └──────────┘   │ BM25     │   │-Personal │           │
│                            └──────────┘   └──────────┘           │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Ranking Signals

```
RANKING ALGORITHM (simplified):

  Score = f(relevance, authority, freshness, personalization, quality)

  RELEVANCE (BM25):
  - How well does the document match the query?
  - Term frequency, inverse document frequency
  
  AUTHORITY (PageRank):
  - How many quality pages link to this page?
  - Page A has links from NYT, BBC → high authority
  - Page B has links from spam sites → low authority
  
  PageRank formula (simplified):
  PR(A) = (1-d) + d × Σ(PR(linking_page) / outbound_links)
  d = damping factor (0.85)
  
  FRESHNESS:
  - When was the page last updated?
  - News articles boost for recency
  
  USER SIGNALS:
  - Click-through rate on search results
  - Dwell time (how long user stays on page)
  - Bounce rate

  Modern: ML models (Learning to Rank) combine 200+ signals
```

---

## 4. Index Sharding

```
  Billions of documents → single machine can't hold entire index

  DOCUMENT SHARDING:
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Shard 1  │  │ Shard 2  │  │ Shard 3  │
  │ Docs 1-1B│  │Docs 1B-2B│  │Docs 2B-3B│
  │          │  │          │  │          │
  │ Full     │  │ Full     │  │ Full     │
  │ inverted │  │ inverted │  │ inverted │
  │ index    │  │ index    │  │ index    │
  └──────────┘  └──────────┘  └──────────┘

  Query "best restaurants":
  1. Scatter: Send query to ALL shards in parallel
  2. Each shard returns top-K results with scores
  3. Gather: Merge results, re-rank globally
  4. Return final top-K to user
```

---

## 5. Key Points for Interview

1. **Inverted index** is the core data structure (word → document list)
2. **PageRank** for authority scoring (link analysis)
3. **BM25** for text relevance scoring
4. **Scatter-gather** across index shards for parallel search
5. **Web crawler** feeds the offline indexing pipeline
6. **Learning to Rank (ML)** for modern ranking with 200+ signals
7. **Freshness** via incremental index updates (not full rebuild every time)
