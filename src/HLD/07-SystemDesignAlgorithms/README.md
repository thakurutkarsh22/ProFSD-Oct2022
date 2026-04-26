# Algorithms in Famous System Designs (Interview Map)

> **Purpose:** A compact index of algorithms and data-structure patterns that show up repeatedly in **real large-scale systems** and in **system design interviews**. Each linked note is written for “explain in 5 minutes + trade-offs + where it’s used,” with pointers to deeper material already in this repo.

---

## How to use this folder

1. Skim the **interview checklist** below to see what you must be able to whiteboard or narrate.
2. Open the numbered notes in any order; they intentionally **cross-link** to longer chapters in [`../02-BuildingBlocks/`](../02-BuildingBlocks/), [`../03-AdvancedConcepts/`](../03-AdvancedConcepts/), and [`../03-AdvancedConcepts/DistributedSystems/`](../03-AdvancedConcepts/DistributedSystems/) instead of duplicating full treatments.

---

## Interview checklist (high signal)

| You should be able to explain | Typical designs |
|---|---|
| Token bucket / leaky bucket / fixed & sliding windows | Rate limiter, API gateway, DDoS edge |
| Consistent hashing + virtual nodes | Dynamo-style KV, caches, load balancers |
| Rendezvous (HRW) hashing | CDN origin selection, distributed cache routing |
| Bloom / Count-Min / HyperLogLog | Crawler dedupe, CDN “maybe seen”, cardinality |
| LSM-tree + SSTables + compaction | Cassandra, RocksDB, Bigtable family |
| Merkle trees | Anti-entropy, sync (Drive/Dropbox), Git, blockchains |
| Gossip + failure detection (SWIM-style) | Cassandra cluster state, Consul Serf |
| Raft / Paxos (high level) | etcd, Consul, Kafka KRaft, many control planes |
| Inverted index + ranking basics | Search, Elasticsearch |
| Trie + top-K / min-heap merge | Autocomplete, news feed merging |
| Geohash / quadtree (concept) | Uber, maps, location queries |
| Stream windows + watermarks (concept) | Flink, Kafka Streams, analytics |

---

## Index of notes in this folder

| # | Topic | File |
|---|-------|------|
| 01 | Rate limiting (token bucket, leaky bucket, windows) | [01-RateLimitingAlgorithms.md](./01-RateLimitingAlgorithms.md) |
| 02 | Hashing & partitioning (consistent, virtual nodes, rendezvous, jump) | [02-HashingAndPartitioning.md](./02-HashingAndPartitioning.md) |
| 03 | Probabilistic structures (Bloom, Count-Min, HLL, Cuckoo) | [03-ProbabilisticDataStructures.md](./03-ProbabilisticDataStructures.md) |
| 04 | Gossip protocols & membership | [04-GossipProtocols.md](./04-GossipProtocols.md) |
| 05 | LSM-trees & SSTables | [05-LSMTree.md](./05-LSMTree.md) |
| 06 | Merkle trees & diff sync | [06-MerkleTrees.md](./06-MerkleTrees.md) |
| 07 | Spatial indexing (geohash, quadtree, R-tree) | [07-SpatialIndexing.md](./07-SpatialIndexing.md) |
| 08 | Tries, heaps & feed-style algorithms | [08-TriesHeapsAndFeeds.md](./08-TriesHeapsAndFeeds.md) |
| 09 | Stream processing windows & time semantics | [09-StreamWindowsAndTime.md](./09-StreamWindowsAndTime.md) |
| 10 | Consensus & leader election (interview compression) | [10-ConsensusAndLeadership.md](./10-ConsensusAndLeadership.md) |

---

## Related material elsewhere in `src/HLD`

| Topic | Deep dive |
|---|---|
| Consistent hashing (full) | [`../02-BuildingBlocks/03-ConsistentHashing.md`](../02-BuildingBlocks/03-ConsistentHashing.md) |
| Bloom filters (full) | [`../02-BuildingBlocks/07-BloomFilters.md`](../02-BuildingBlocks/07-BloomFilters.md) |
| Search & inverted indexes | [`../03-AdvancedConcepts/04-SearchAndIndexing.md`](../03-AdvancedConcepts/04-SearchAndIndexing.md) |
| Stream processing systems | [`../03-AdvancedConcepts/05-StreamProcessing.md`](../03-AdvancedConcepts/05-StreamProcessing.md) |
| Consensus (Paxos, Raft, ZAB) | [`../03-AdvancedConcepts/02-Consensus.md`](../03-AdvancedConcepts/02-Consensus.md) |
| Anti-entropy & Merkle sync | [`../03-AdvancedConcepts/DistributedSystems/08-AntiEntropy.md`](../03-AdvancedConcepts/DistributedSystems/08-AntiEntropy.md) |
| Failure detection & gossip | [`../03-AdvancedConcepts/DistributedSystems/04-FailureDetection.md`](../03-AdvancedConcepts/DistributedSystems/04-FailureDetection.md) |
| Unique IDs (Snowflake-style) | [`../04-DesignEasy/04-UniqueIDGenerator.md`](../04-DesignEasy/04-UniqueIDGenerator.md) |
