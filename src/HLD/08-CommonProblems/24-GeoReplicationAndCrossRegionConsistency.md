# Geo-Replication and Cross-Region Consistency

> **TL;DR.** Going multi-region is mostly not about *failover* (that's [`13-MultiRegionFailover.md`](13-MultiRegionFailover.md)) — it's about where writes live in steady state. You have four architectures: **single-region writes + read replicas**, **region-partitioned writes** (EU users → EU; US users → US), **active-active with conflict resolution**, and **globally consistent writes** (Spanner-class). Each trades latency, availability, and correctness differently. The senior-engineer move is to *not* default to "active-active" for the prestige — it is the hardest of the four and the one most teams implement incorrectly.

---

## 1. The three fundamental truths

1. **Speed of light is the hard limit.** US ↔ EU round trip ≈ 80 ms, US ↔ Asia ≈ 150–200 ms. Any cross-region sync write eats this on *every* commit.
2. **You can't have low-latency, global-strong consistency, and partition tolerance.** This is CAP rephrased for geography. Real systems pick two.
3. **Most data doesn't need to be globally consistent.** A user's own posts need low latency for them; a stranger's posts can be seconds stale. Design accordingly.

---

## 2. The four steady-state architectures

```
┌────────────────────────────────────────────────────────────────┐
│                 FOUR WAYS TO GO MULTI-REGION                    │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  A. Single-region writes + global read replicas                │
│     Writes → 1 region; replicas elsewhere for reads            │
│                                                                │
│  B. Region-partitioned writes (geo-sharding)                   │
│     EU user's data in EU; US user's data in US                 │
│                                                                │
│  C. Active-active with conflict resolution                     │
│     Any region accepts writes; replicate async; resolve        │
│                                                                │
│  D. Globally-consistent writes (Spanner / Cockroach class)     │
│     Paxos/Raft groups spanning regions                         │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Quick picker

| Requirement | Pick |
|-------------|------|
| Low write latency per region, OK with stale reads elsewhere | A or B |
| Low read/write latency globally + data boundary per user (EU stays in EU) | B |
| Accept any regional write; tolerate eventual reconciliation | C |
| Strong consistency globally, pay the latency | D |

---

## 3. Architecture A — Single-region writes + read replicas

```
┌───────────────────────────────────────────────────────────────┐
│              SINGLE-WRITE, MULTI-READ                          │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│   │  US-EAST     │  │  US-WEST     │  │  EU          │        │
│   │              │  │              │  │              │        │
│   │  writes ─►   │  │  writes ──►  │  │  writes ──►  │        │
│   │  local DB    │  │              │  │              │        │
│   │     │        │  │              │  │              │        │
│   │     │ async  │  │              │  │              │        │
│   │     ├────────┼──► replica      │  │              │        │
│   │     └────────┼──┼──────────────┼──► replica      │        │
│   │              │  │              │  │              │        │
│   │  reads       │  │  reads       │  │  reads       │        │
│   │  (hot)       │  │  (stale)     │  │  (stale)     │        │
│   └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### Pros
- Simplest operationally. One truth. No conflicts.
- Reads are local and fast.
- Failover is a known problem ([`13`](13-MultiRegionFailover.md)).

### Cons
- Write latency varies by user location (EU user writing to US-EAST pays 80 ms per write).
- Writes are a SPOF at the region level.
- Read-after-write across regions is stale (the user reads their own post from a replica that hasn't replicated yet).

### Mitigations
- **Sticky reads** — after a write, route that user's reads to the primary for a short window (or read-your-writes tokens / version checks).
- **Edge compute** for read paths; write paths remain centralised.
- **Async write queues** at the edge — acknowledge fast, replicate centrally later (but beware: this is effectively active-active in disguise).

**When to use:** most SaaS products until they're big enough to need B or C.

---

## 4. Architecture B — Region-partitioned writes (geo-sharding)

```
┌───────────────────────────────────────────────────────────────┐
│                 REGION-PARTITIONED WRITES                      │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   ┌──────────────┐     ┌──────────────┐    ┌──────────────┐   │
│   │    US        │     │     EU       │    │    Asia      │   │
│   │              │     │              │    │              │   │
│   │  US users'   │     │  EU users'   │    │  Asia users' │   │
│   │  writes + DB │     │  writes + DB │    │  writes + DB │   │
│   │              │     │              │    │              │   │
│   │  async ──────┤     │              │    │              │   │
│   │  replicate to│     │              │    │              │   │
│   │  other region│     │              │    │              │   │
│   │  for failover│     │              │    │              │   │
│   └──────────────┘     └──────────────┘    └──────────────┘   │
│                                                               │
│   Users routed to their "home" region by some attribute.      │
│   Each region is effectively independent at steady state.     │
└───────────────────────────────────────────────────────────────┘
```

### The routing key

You must be able to decide "which region owns this user/tenant" from the request:

- **User profile attribute** — `home_region = "EU"`. Simple; stored in identity service; propagated in auth tokens.
- **Geolocation at signup** — guess once, store.
- **Tenant → region mapping** — B2B SaaS: each tenant's company picks / is placed in a region.
- **Data residency** — legal (GDPR) often forces EU users to EU.

### Pros
- **Low latency for writes AND reads** (everything local).
- **Regulatory alignment** — data residency naturally enforced.
- **Linear scalability** — add a region, add capacity.
- **Blast radius reduced** — a region-level outage affects only that region's users.

### Cons
- **Cross-region operations are awkward.** An EU user messaging a US user: whose region stores the message? One canonical choice; lookups across regions.
- **Travel / mobility.** A user who moves between regions — move their data or live with higher latency.
- **Global features** (global leaderboard, global search) need a secondary aggregation path.

### Patterns inside
- Each region has its own full stack (app + DB + cache).
- Cross-region reads are rare and served by on-demand queries to the owning region (with caching).
- Asynchronous replication to a peer region as *failover standby* only; not active reads.

**This is the architecture most large global SaaS products converge toward** (Shopify, GitHub, Salesforce, Slack's enterprise tier).

---

## 5. Architecture C — Active-active with conflict resolution

```
┌───────────────────────────────────────────────────────────────┐
│                    ACTIVE-ACTIVE                               │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   US region writes   ◄───────── async replication ───────►   EU│
│                                                               │
│   Any user, any region. All regions accept writes for all    │
│   records.                                                   │
│                                                               │
│   Consequence: the same record can be written simultaneously │
│   in US and EU → CONFLICT on replication.                    │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

This is the **most celebrated and most-often-botched** design. It's what DynamoDB Global Tables, Cassandra multi-DC, and Couchbase XDCR do.

### 5.1 The conflict resolution menu

| Strategy | Semantics | Suitable for |
|----------|-----------|--------------|
| **Last-Writer-Wins (LWW)** by timestamp | Whichever write has the larger timestamp wins; earlier is silently lost | Session tokens, soft preferences |
| **Conflict-free Replicated Data Types (CRDTs)** | Mathematically guaranteed merge | Counters, sets, collaborative text (Yjs, Automerge) |
| **Version vectors / causal merge** | Keep all versions, let app decide | Shopping carts (Dynamo paper) |
| **Application-level merge** | App code resolves (e.g., "take union") | Domain-specific merges |
| **Designated owner per key** | Only one region can write this key at a time | "My profile" kinds of fields |
| **Quorum writes (R+W>N)** | Strong consistency at cost of latency | Cassandra strict mode |

### 5.2 Why LWW is usually wrong

LWW silently loses data. A classic failure:

```
 Alice (in EU):  updates profile pic at T=100    (ts stored as 100)
 Bob (in US):   updates Alice's pic     T=101    (ts stored as 101)
 EU replicates Alice's → US:  T=100 < T=101 → ignored
 US replicates Bob's → EU:    T=101 > T=100 → overwrites Alice's
 
 Alice's update is gone. Nobody notices.
```

Add clock skew and it becomes non-deterministic which "wins". **LWW is acceptable only when losing the loser is acceptable.**

### 5.3 Where CRDTs shine

- **Counters** (likes, views, metrics) — G-counter, PN-counter.
- **Sets** (tags on a post) — OR-set, 2P-set.
- **Text** (collaborative editing) — RGA, Yjs.
- **Maps** of the above.

CRDTs give you *provably* conflict-free merge with no coordination. The cost: the structure is constrained (can't represent arbitrary objects), and some CRDTs grow metadata over time.

### 5.4 Pros of active-active
- Low latency for writes anywhere.
- Regional outage ≠ data loss (other regions keep serving).
- High write availability.

### 5.5 Cons
- **Conflict resolution is intrinsically lossy or complex.**
- **Reads are eventually consistent.** "I wrote it here; my teammate in another region doesn't see it for 200 ms."
- **Debugging** is orders of magnitude harder. Lost updates are silent.
- **Invariants across regions** can't be enforced (no "only one user with this email" if both regions can accept registrations).

See also [`../03-AdvancedConcepts/DistributedSystems/09-ConflictResolution.md`](../03-AdvancedConcepts/DistributedSystems/09-ConflictResolution.md).

**When to use:** high-availability workloads where eventual consistency is clearly acceptable, OR workloads dominated by CRDT-friendly operations (counters, sets, collaborative docs).

---

## 6. Architecture D — Globally-consistent writes (Spanner class)

```
┌───────────────────────────────────────────────────────────────┐
│                GLOBAL STRONG CONSISTENCY                       │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   Paxos / Raft replica group spans regions.                   │
│   Every write = cross-region consensus round.                 │
│                                                               │
│   latency = 1 RTT (to nearest majority)                       │
│            ≈ 50-100 ms for US regions; 150+ for global       │
│                                                               │
│   Reads can be fast (local leader or bounded-staleness)      │
│   Writes are slow but SERIALIZABLE globally.                 │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

Implementations: **Google Spanner**, **CockroachDB**, **YugabyteDB**, **Amazon Aurora DSQL** (new), **FoundationDB**.

### Pros
- Strongest guarantees you can get (external consistency, serializable).
- No conflicts — the DB decides write order via consensus.
- Works across regions *correctly* if painfully.

### Cons
- Latency cost on every write (~100 ms for geographic majority).
- Complex to operate; expensive; limited ecosystem.
- Not a panacea — you still need to place replicas wisely (leader in the region doing most writes).

**When to use:** financial ledgers, regulator-facing systems, inventory where overselling is catastrophic, global coordination.

---

## 7. Hybrid architectures

Real products mix architectures **per data type**:

| Data | Architecture |
|------|--------------|
| User profile (PII, EU vs US data residency) | B (region-partitioned) |
| Session state | C (active-active, LWW is fine — it's a session) |
| Financial transactions | A or D (single-region, or Spanner) |
| Notifications | C (CRDT set) |
| Comments / posts | A (single-region with global read replicas) |
| Feature flags / config | A (single-writer, globally cached) |
| Analytics events | A (all writes → central, asynchronous) |

Most "multi-region" products are *not* uniformly one of A–D; they mix. The senior-engineer move is to articulate **per data type** which architecture is used.

---

## 8. Global catalogs vs local data

One recurring pattern:

```
   Local-write data:
      user.profile     (my data — local region)
      order.history    (my orders — local region)

   Global catalog:
      product.catalog  (same for everyone)
      feature.flags    (same for everyone)
      price.book       (same for everyone)
```

Local-write: Architecture B. Global catalog: Architecture A with global caching / replication. Cleanest pattern when you can decompose this way.

---

## 9. Cross-region coordination — when you truly need it

Sometimes two regions *must* coordinate:

- Global unique ID / username reservation.
- Global counters that must never go negative.
- Payment settlements.

**Designs:**

1. **Central coordinator in one region.** All these calls go there. Accept latency. Simplest.
2. **Leased ranges.** Region A owns IDs 1M-2M, Region B owns 2M-3M. Each region allocates locally; coordinator hands out ranges. Used by Snowflake IDs, Shopify IDs.
3. **Escrow / reservation.** Each region has a local quota; central "refills" periodically. Bounded inconsistency (the quota may be slightly stale).
4. **Strong-consistency DB** for that data (Architecture D).

---

## 10. Observability and operational concerns

- **Replication lag per region pair**. Seconds of lag = seconds of stale reads.
- **Conflict rate** (for active-active). Spikes reveal design issues or clock skew.
- **Cross-region query rate**. High = your partitioning key is wrong.
- **Per-region error budget**. Region independence = region-scoped SLOs.
- **Clock skew across regions**. NTP minimum; for serializability, PTP or TrueTime-class.
- **Bandwidth cost across regions**. Egress is expensive; monitor it.

---

## 11. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| "Active-active because it sounds better" | Conflict resolution > engineers; data loss you can't see |
| LWW everywhere | Silent data loss on concurrent writes |
| Synchronous cross-region writes "just in case" | Every write pays 100+ ms; user experience dies |
| Single region with "we'll go multi-region later" and no isolation boundary | When you're forced into it, the refactor is brutal |
| Reads in region A, writes in region B by default | Every read is RAW-stale for that user's own writes |
| No data residency plan | GDPR / data sovereignty compliance surprise |
| Active-active without idempotent operations | Replayed cross-region writes duplicate |
| Ignoring clock skew in active-active | Deterministic-sounding rules (LWW) are actually coin flips |
| Mixing global and regional data on the same shard | One gets wrong semantics |
| Assuming multi-region == disaster recovery | DR requires tested failover, not just replication |

---

## 12. Interview talking points

- **Four architectures.** Name A–D explicitly; discuss trade-offs before picking.
- **Not all data is the same.** Per-data-type architecture choice.
- **Speed of light is the budget.** RTTs matter.
- **LWW is usually wrong.** Use CRDTs or application-level merge.
- **Region-partitioning is often best** for global products with independent user bases.
- **Spanner-class is right for financial / regulatory data**, wrong for user posts.
- **Data residency** is a first-class requirement in 2024+ (GDPR, China, Russia, India).
- **Cross-region coordination** is an explicit design: central coordinator, leases, escrow, or strong DB.
- **Replication lag is a metric, not an assumption.**
- **Failover is a separate concern** ([`13`](13-MultiRegionFailover.md)) from steady-state multi-region.

---

## 13. Related reading

- [13-MultiRegionFailover.md](13-MultiRegionFailover.md) — failover specifics.
- [20-CrossShardAndDistributedTransactions.md](20-CrossShardAndDistributedTransactions.md) — transactions across regions.
- [21-CellBasedAndShuffleSharding.md](21-CellBasedAndShuffleSharding.md) — cells may be regional boundaries.
- [22-GrayFailuresAndHealthChecks.md](22-GrayFailuresAndHealthChecks.md) — gray failures in cross-region links are brutal.
- [../03-AdvancedConcepts/DistributedSystems/09-ConflictResolution.md](../03-AdvancedConcepts/DistributedSystems/09-ConflictResolution.md) — LWW, CRDTs, vector clocks.
- [../03-AdvancedConcepts/DistributedSystems/](../03-AdvancedConcepts/DistributedSystems/) — CAP, quorum, clocks.
- Google Spanner paper, Amazon Dynamo paper, Jepsen reports on Cassandra/Mongo/Cockroach — primary sources.
