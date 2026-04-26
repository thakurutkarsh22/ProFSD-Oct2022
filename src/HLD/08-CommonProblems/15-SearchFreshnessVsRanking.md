# Search Index Freshness vs Ranking Quality

> **TL;DR.** Search systems live in a three-way tension between **freshness** (how fast new documents show up in results), **ranking quality** (do the best results surface first?), and **indexing cost**. You can't fully optimise all three simultaneously. The industry pattern is a **two-tier index** — a small, hot, always-fresh index for new documents, and a large, well-ranked, slower-to-update main index, merged at query time.

---

## 1. The three axes of a search system

```
              Freshness
                 ▲
                 │
                 │  (a great search system moves
                 │   all three outward together,
                 │   but every design choice is
                 │   a pull in one direction)
                 │
   ───────────────────► Ranking quality
                 │
                 │
                 ▼
             Cost / Latency
```

- **Freshness.** "If I post a tweet now, is it searchable in 1 s? 10 s? 10 min?"
- **Ranking quality.** Relevance, personalisation, recency, popularity, spam suppression — the signals that make results *good*.
- **Cost & indexing latency.** CPU, memory, disk I/O, network for updates. Indexing costs more than querying at scale.

Adding signals (popularity, CTR, collaborative filtering) raises ranking quality — and lengthens the time between a document's creation and its appearance in well-ranked results.

---

## 2. Why they conflict

Modern ranking uses **batch-computed features**:
- Popularity over the last 24 h.
- User-document click-through rate.
- Learning-to-rank models trained on historical data.
- Link graph / PageRank.

These features are expensive. You don't recompute them on every new document. So the documents enter the **low-signal pool** first; they migrate to the **rich-signal pool** only after batch pipelines churn.

If you insist on fresh + best-ranked, every new document triggers a full feature recomputation — completely impractical at Google/Twitter scale.

---

## 3. The two-tier index (industry standard)

```
┌─────────────────────────────────────────────────────────────────┐
│                    TWO-TIER SEARCH INDEX                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                         Query                                   │
│                           │                                     │
│                           ▼                                     │
│                 ┌───────────────────┐                           │
│                 │   Query Broker    │                           │
│                 └─────────┬─────────┘                           │
│                           │                                     │
│              fan out ─────┴─────                                │
│              │                 │                                │
│              ▼                 ▼                                │
│   ┌──────────────────┐   ┌──────────────────────────┐          │
│   │  REAL-TIME INDEX │   │   MAIN INDEX (cold)      │          │
│   │                  │   │                          │          │
│   │  Lucene NRT seg  │   │  Big, well-ranked index  │          │
│   │  in-memory       │   │  with batch features     │          │
│   │  Last ~24 h      │   │  (PageRank, CTR, etc.)   │          │
│   │                  │   │                          │          │
│   │  Freshness: ~1s  │   │  Freshness: hours        │          │
│   │  Ranking: weak   │   │  Ranking: strong         │          │
│   └────────┬─────────┘   └───────────┬──────────────┘          │
│            │                         │                         │
│            └──────────┬──────────────┘                         │
│                       ▼                                        │
│              Merge + re-rank                                   │
│              (diverse blend)                                   │
│                       │                                        │
│                       ▼                                        │
│                   Results                                      │
│                                                                │
└─────────────────────────────────────────────────────────────────┘
```

- **Real-time index**: fast to write, small, last few hours/day of documents, minimal ranking signals.
- **Main index**: hours- to day-old, well-ranked, contains everything.
- **Query-time merge**: take top-K from each, unify via a simple blend (e.g. 3 fresh + 7 main in the top 10).

At a later batch cycle, documents "graduate" from real-time → main, their features get computed, and the real-time tier evicts them.

This is essentially Twitter's Earlybird architecture, LinkedIn's two-tier search, and Google's Caffeine (roughly).

---

## 4. How fast can "real-time" actually be?

### 4.1 Lucene / Elasticsearch — Near Real Time (NRT)

- Writes land in an in-memory segment.
- Every `refresh_interval` (default 1 s) the segment becomes searchable.
- Segments are periodically merged to larger on-disk segments.

```
Write → Transaction log → In-memory segment → (refresh) → Searchable
                                                │
                                                └── (merge) → Bigger segment on disk
```

- Tuning `refresh_interval` = 1 s gives freshness ≈ 1 s at a cost.
- Setting it to 30 s saves CPU but raises freshness to 30 s.
- Disabling refresh while bulk-loading is a common bulk-import trick.

### 4.2 Sub-second systems

Twitter Earlybird builds its own NRT index data structure in C++/Java; serves fresh in ~10 s. Purpose-built — generic Elasticsearch can't quite match it.

### 4.3 Don't forget query caches

Fresh data + cached query results = stale results. Either invalidate caches on write (hard at write rate) or shorten cache TTL (expensive on hot queries). Most systems accept bounded staleness on cached query results.

---

## 5. Ranking at query time (the knobs)

### 5.1 Cascading ranking

```
Query → Level 1 (lightweight)  top 10K fast features  (BM25, freshness)
      → Level 2 (mid-weight)   top 1K  L2R model       (~20 features)
      → Level 3 (heavy)        top 100 deep model      (neural, personalisation)
      → Results shown
```

Cheap filters cut the candidate set; expensive ranking is only applied to a narrow set. This is how we fit big models into 100 ms budgets.

### 5.2 Personalisation sources

- **User features** (language, interests, past clicks).
- **Session features** (previous queries, device).
- **Social** (friends' activity) — adds a join to the social graph.
- **Inventory** (availability, price).

Personalisation typically lives in Level 2/3 because it requires joining per-user data at query time.

### 5.3 Freshness boost

A simple decay: `final_score = relevance × e^(-lambda × age_in_hours)`. Tuned so 1-day-old documents with strong relevance beat 1-minute-old documents with weak relevance.

---

## 6. Indexing architectures

### 6.1 Push model (most common)

```
writer ── event ──► Kafka ──► indexer ──► index
```

Indexer consumes updates in order, applies to the index.

- Ordering: per-document (partition by doc ID).
- Parallelism: scale indexer horizontally by partition count.
- Failure recovery: replay the log.

### 6.2 Pull model

Indexer periodically queries the source (DB or another store) for new/updated rows and indexes them.

- Simpler, but higher latency; more load on source.
- Fine for daily/hourly rebuilds.

### 6.3 Segment merging & the write amplification problem

Every write inserts into a small segment. Small segments are expensive to query (many files). Background **merge** combines segments into bigger ones.

- Writes are cheap; merges amortise the cost.
- Merging is expensive I/O; hits latency during merge.
- Bigger segments = better query perf but heavier merge cost on update.

This is the LSM-tree trade-off inside the search engine — see [../07-SystemDesignAlgorithms/05-LSMTree.md](../07-SystemDesignAlgorithms/05-LSMTree.md).

### 6.4 Partitioning (sharding the index)

Two styles:
- **Document-sharding**: each shard holds a subset of documents. Query all shards, merge results. Default in Elasticsearch. Scales horizontally; every query hits every shard.
- **Term-sharding**: split the vocabulary; each shard holds a subset of terms' postings. Query touches only shards with the query's terms. Used by some older systems; harder to balance.

Document-sharding dominates because merge is easy and terms are hard to balance (Zipfian term frequency → hot shards).

---

## 7. The "second" index for updates

Search indexes are typically **append-friendly, update-hostile**. An update = delete-old + insert-new. Frequent updates (e.g., a product's price changing hourly) balloon the index with tombstones and force merges.

Solutions:
- **Partition by update frequency.** Static fields in main index; volatile fields in a "side index" joined at query time.
- **External scoring table.** Ranking features for volatile signals (popularity, CTR) stored in a KV store and fetched per-candidate at query time. The index holds only rarely-changing fields.

---

## 8. Reindexing without downtime

You will rebuild your index — schema change, tokenizer change, new analyzer, etc. Safe pattern:

```
1. Build new index in parallel  (new_index_v2)
2. Dual-write application to old + new
3. Backfill old documents into new
4. Verify (shadow queries, parity metric)
5. Switch read traffic via an alias / router
6. Decommission the old
```

Same expand-contract as [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md). Elasticsearch aliases are literally designed for this.

---

## 9. Freshness vs ranking trade-off table

| Use case | Freshness | Ranking | How |
|----------|-----------|---------|-----|
| Breaking news / tweets | < 5 s | medium | Real-time index heavy, main index light; recency boost |
| E-commerce product search | 1-5 min | high | Two-tier; personalised & CTR-ranked main |
| Web search (Google) | minutes to hours | very high | Main index huge, fresh index small, cascade of ranking |
| Job search | 1 hour | high | Daily batch features dominate |
| Legal / academic | days | very high | Quality > freshness; no real-time path needed |
| Customer support tickets internal | 10 s | low | Fresh matters; relevance is simple match |

Match the architecture to the use case, not to "what cool companies do".

---

## 10. Observability

- **Indexing lag** (ingest timestamp → searchable timestamp). p50, p99.
- **Query latency** by tier (real-time vs main vs merged).
- **Merge throughput** — if merges can't keep up, query latency drifts up.
- **Index size** & growth rate.
- **Ranking health**: click-through rate, nDCG, first-click position. Drops = ranking regression.
- **A/B test framework** so ranking changes can be evaluated on real user behaviour.

---

## 11. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| Refresh interval = 0 (force every write to be searchable immediately) | Kills throughput; merge overhead dominates |
| Put every feature in the index | Update rates explode; rebuild cost spikes |
| One index for fresh and ranked | Forces a choice; you lose on both sides |
| Skip staging index for schema changes | Accidental outage during reindex |
| Ignore tombstones after mass deletes | Index bloats; query perf degrades until force merge |
| Query-time JOINs with external stores but no caching | Per-candidate lookups explode query latency |
| Ranking changes without A/B testing | "Feels better" is not measurement; you broke something |

---

## 12. Interview talking points

- **Name the triangle: freshness × ranking × cost.** Senior signal.
- **Prescribe two-tier: real-time + main with query-time merge.** The right answer for any "design Twitter search" question.
- **Indexing vs query: different scaling profiles.** Partition counts, heap size, write vs read traffic.
- **Ranking cascade.** Level 1/2/3 — cheap filters, then expensive models.
- **External scoring table** for volatile signals. Under-appreciated move.
- **Reindex via aliases.** Show you know how to roll changes safely.
- **Measurement.** nDCG or click-through rate for ranking; indexing lag p99 for freshness. Don't just say "we'll look good" — measure.

---

## 13. Related reading

- [../03-AdvancedConcepts/04-SearchAndIndexing.md](../03-AdvancedConcepts/04-SearchAndIndexing.md) — inverted index, BM25, tokenization.
- [../06-DesignHard/07-SearchEngine.md](../06-DesignHard/07-SearchEngine.md) — full system design of a web search engine.
- [../05-DesignMedium/05-Autocomplete.md](../05-DesignMedium/05-Autocomplete.md) — trie/FST + ranking for typeahead.
- [../07-SystemDesignAlgorithms/05-LSMTree.md](../07-SystemDesignAlgorithms/05-LSMTree.md) — the update/merge trade-off underneath.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — CDC feeding the indexer.
- [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md) — reindex without downtime.
