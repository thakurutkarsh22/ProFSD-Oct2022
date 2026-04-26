# Cell-Based Architecture and Shuffle Sharding

> **TL;DR.** In a monolithic deployment, a single bug, bad deploy, poison input, or noisy tenant can bring down *everyone*. **Cell-based architecture** partitions the system into independent, self-contained cells — each with its own compute, data, and caches — so a failure is contained to one cell. **Shuffle sharding** goes further: it assigns each tenant a *pseudo-random subset* of cells (or workers) such that any two tenants' subsets overlap in very few nodes, shrinking the blast radius of any single bad tenant dramatically. This is how AWS, Slack, Shopify, and others operate multi-tenant services with hard blast-radius guarantees.

---

## 1. The problem cells solve

```
┌───────────────────────────────────────────────────────────────┐
│            MONOLITHIC DEPLOYMENT BLAST RADIUS                  │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   All tenants share one fleet, one DB, one cache, one queue.  │
│                                                               │
│   Failure modes:                                              │
│    • Bad deploy → 100% of tenants affected                    │
│    • Poison input crashes a pod → connection pool exhausts    │
│    • One tenant's abuse saturates the queue                   │
│    • Schema migration goes wrong → 100% down                  │
│                                                               │
│   Blast radius = 100% of customers, always.                   │
└───────────────────────────────────────────────────────────────┘
```

This is unacceptable once you're large enough that outages cost millions. The goal: make blast radius **bounded and small** by design — ideally < 5% of customers for any single failure.

---

## 2. Cell-based architecture

A **cell** is a fully self-contained slice of the system: compute, data, caches, queues, even deployment pipelines.

```
┌──────────────────────────────────────────────────────────────┐
│                     MULTI-CELL DEPLOYMENT                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│   │   Cell 1    │    │   Cell 2    │    │   Cell 3    │      │
│   │ ───────────│    │ ───────────│    │ ───────────│      │
│   │ App tier    │    │ App tier    │    │ App tier    │      │
│   │ DB          │    │ DB          │    │ DB          │      │
│   │ Cache       │    │ Cache       │    │ Cache       │      │
│   │ Queue       │    │ Queue       │    │ Queue       │      │
│   └─────────────┘    └─────────────┘    └─────────────┘      │
│         │                    │                   │           │
│         └────────────────────┼───────────────────┘           │
│                              │                               │
│                    ┌─────────┴─────────┐                     │
│                    │   Cell Router     │  thin layer: maps   │
│                    │   (stateless)     │  tenant → cell      │
│                    └─────────┬─────────┘                     │
│                              │                               │
│                           Clients                            │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 What must be in a cell

Everything whose failure would affect users:

- App / service fleet.
- Database and its replicas.
- Cache.
- Message queues.
- Background workers.
- Config store (optional — often global).
- Deployment pipeline — each cell deployed independently.

### 2.2 What stays outside (global layer)

As minimal as possible. Only things that *must* be global:

- **Cell router / tenant routing.** Which cell handles which tenant. Stateless; replicated; simple lookup.
- **Identity / auth** (often). A single auth service used by all cells.
- **Billing / metering aggregation.**
- **Global metrics / observability.**

Rule: anything in the global layer should be as simple and read-mostly as possible. A complex global layer is a shared-failure-mode re-emerging.

### 2.3 Cell sizing

Sizing a cell is a trade-off:

| Cell size | Blast radius | Efficiency | Complexity |
|-----------|--------------|------------|------------|
| 1 tenant/cell | Perfect isolation | Terrible (spare cap each cell) | Very high (millions of cells) |
| Small cells (10-100 tenants) | < 1% of customers | Moderate | High |
| Medium cells (1K tenants) | Few % | Good | Manageable |
| Large cells (10K+ tenants) | Significant | Great | Low but minimal isolation |

Typical real-world: **50-500 tenants per cell**, 10-100 cells total.

### 2.4 Deploy and rollout

Cells should be deployed in **waves**:

```
Wave 1: 1 canary cell  (e.g., internal users)
Wave 2: 3 small cells
Wave 3: next 10 cells
Wave 4: remaining cells
```

A bad deploy hits Wave 1, trips alarms, auto-rollback. Blast radius: one cell. This is why cells pay off: you have a *natural* staged rollout boundary.

---

## 3. Shuffle sharding

Cell architecture gives each tenant one cell. **Shuffle sharding** gives each tenant a *subset* of N nodes (out of M total), pseudo-randomly assigned by tenant_id.

```
   M = 8 nodes total, each tenant uses N = 2 nodes
   
   Tenant A → nodes {2, 5}
   Tenant B → nodes {1, 6}
   Tenant C → nodes {3, 7}
   Tenant D → nodes {2, 7}    ← overlaps A on {2}, C on {7}
   Tenant E → nodes {4, 8}
```

If tenant A sends poisonous traffic and corrupts *both* its nodes (2 and 5):

- Only tenants sharing those nodes are affected.
- With shuffle sharding, very few tenants share *both* nodes with A.

### 3.1 The math

With M total nodes and N per tenant, the probability a single "unlucky" tenant shares **all N nodes** with another specific tenant is `1 / C(M, N)`. For M=8, N=2 → 1/28 ≈ 3.6%. For M=64, N=4 → 1 / 635 376 ≈ 0.00016%.

Scaling up slightly: with M=100, N=5 nodes → C(100, 5) = 75 million combinations. Two random tenants share all 5 nodes with negligible probability. Meaning: **if one tenant DDoSes itself, only an infinitesimal fraction of other tenants are affected.**

### 3.2 Intuition

```
Without shuffle sharding (regular hashing):
   Tenant A on a bad node → every tenant using that node affected
   Tenant A causes cascade → potentially all hashed to that shard die

With shuffle sharding:
   Tenant A causes cascade on both A's nodes
   Other tenants: each uses N nodes
   Chance another tenant uses the *same* 2 bad nodes is tiny
   → Other tenants' requests still hit their other N-1 healthy nodes
   → They're mostly unaffected
```

This works because clients can **retry** to a different node in their subset. The tenant's bad-node traffic is contained; every other tenant has majority-healthy capacity.

### 3.3 Where it's used

- **AWS Route 53** — name servers assigned via shuffle sharding.
- **AWS DynamoDB / S3** — request routers.
- **CloudFront** — edge node assignment.
- **Slack** — database shards per workspace.

---

## 4. Combining cells and shuffle sharding

```
Global tier
    │
    ▼
Cell Router (stateless)
    │
    ▼
Cells (coarse-grained isolation)
    │
    ▼
Within each cell:
  Shuffle-sharded node assignment (fine-grained isolation)
```

Two layers of blast-radius reduction. An internal bug hits one node → affects one or two tenants on that cell. A bad deploy hits one cell → affects a few hundred tenants. A catastrophic shared-layer failure (rare) affects more.

---

## 5. Cross-cell operations

Mostly you design to *avoid* them. When you need them:

### 5.1 Reads

Cross-cell reads are fine if rare: the global layer can proxy, or the client can query both. Most traffic stays within its cell.

### 5.2 Writes

Cross-cell transactions break the isolation. If you need them, reconsider the partitioning — the business concept probably belongs inside one cell.

### 5.3 Global state (billing, quotas)

Aggregate cell-local state into a central system **asynchronously**. Daily billing roll-up, hourly quota sync. Don't make hot paths cross-cell.

### 5.4 Tenant rebalancing

Moving a tenant between cells is a data migration (see [`11-ZeroDowntimeSchemaMigration.md`](11-ZeroDowntimeSchemaMigration.md)):

1. Dual-write new cell.
2. Backfill.
3. Flip reads.
4. Stop writes to old cell.
5. Reclaim capacity.

Plan for it. Some tenants grow from "fits in shared cell" to "needs a dedicated cell".

---

## 6. Thin global routing layer

The cell router is the **most critical** piece. Keep it:

- **Stateless.** No database. Just an in-memory routing table loaded from a config store.
- **Cached everywhere.** Clients cache the mapping; edge proxies cache it.
- **Read-heavy.** Updates rare (tenant moves).
- **Replicated across all cells** for resilience.
- **Simple.** No business logic. Just `tenant_id → cell_id`.

A common implementation:

```
routing_config.json  (in a config store + CDN + local cache):
{
  "acme":    "cell-3",
  "globex":  "cell-7",
  "initech": "cell-1",
  ...
}
```

CDN-cached with a 60 s TTL. Tenant moves? Publish new config, wait a minute, done.

---

## 7. Observability implications

- **Dashboards per cell.** You want to compare cell health at a glance.
- **Blast-radius metric.** "How many tenants / what fraction of traffic are affected by the current incident?" — the number that proves cells are working.
- **Cross-cell error correlation.** An incident *within* a cell shouldn't leak to others; if it does, you've found a hidden shared resource.
- **Cell capacity metrics.** Each cell has its own saturation.

---

## 8. When cells are *not* worth it

Cells add complexity. Don't do them if:

- You have < 1000 customers / cells would each be tiny and wasteful.
- Your product is single-tenant anyway.
- You don't have SLO drivers — "outages are mildly annoying" isn't enough.
- You can't afford the 2–5× infrastructure cost of spare capacity per cell.

For smaller scale, simpler isolation (per-tenant rate limits + bulkheads, as in [`16-NoisyNeighborIsolation.md`](16-NoisyNeighborIsolation.md)) is often enough.

---

## 9. Migration: monolith to cells

Common path:

```
1. One big deployment (starting point)
2. Add cell router as a no-op in front (everything still goes to one backend)
3. Split compute: create cell 2 with its own app tier (shared DB for now)
4. Split data: move some tenants' data to cell 2's DB
5. Repeat, adding cells, moving tenants
6. Eventually decommission the "cell 0" monolith
```

Years long for large systems. Each step de-risked by the cell router sitting above everything.

---

## 10. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| Cells that share a database | Blast radius is the DB, not the cell. Negates the whole point. |
| Global hot path (e.g., every request hits a central quota service) | The global layer is the SPOF; cells don't help |
| Cells that deploy together | Bad deploy hits all cells; no staged rollout benefit |
| Monitoring without per-cell breakdowns | You can't tell if a cell is the problem |
| Cross-cell transactions | Breaks isolation; usually means the cell boundary is wrong |
| Skipping the cell router abstraction | Hard-coded per-tenant routing in clients; painful to change |
| Equal cell sizing with unequal tenants | A big tenant in a small cell saturates it; big tenants need bigger cells |
| No plan for moving tenants between cells | You'll need it eventually; bolt-on migration is painful |
| Shuffle sharding without enough N | N=1 means any one bad node takes the tenant down — defeats the purpose |
| "Shuffle sharding" by modulo hash | That's not shuffle sharding; it's regular sharding. Needs N ≥ 2 and random subsets. |

---

## 11. Interview talking points

- **Name the pattern.** "Cell-based architecture, as used by AWS and Slack."
- **Blast radius as the KPI.** "What fraction of customers does any single failure affect?"
- **Thin global layer.** Router as stateless, cached, replicated.
- **Cell size as a trade-off.** Smaller = more isolation, more cost.
- **Staged deploys are free.** Canary cell, then waves — cells *are* the deploy boundary.
- **Shuffle sharding for finer granularity.** Mention the combinatorial blast-radius shrinkage.
- **Cross-cell ops are suspicious.** Usually indicate wrong cell boundary.
- **Migration plan.** Tenants need to move cells eventually.
- **Complements noisy-neighbor controls.** Cells give you structural isolation; rate limits give you per-tenant isolation *within* a cell.

---

## 12. Related reading

- [16-NoisyNeighborIsolation.md](16-NoisyNeighborIsolation.md) — quotas and bulkheads within a cell.
- [04-SinglePointOfFailure.md](04-SinglePointOfFailure.md) — cells remove the SPOF at the tenant level.
- [13-MultiRegionFailover.md](13-MultiRegionFailover.md) — cells are the regional equivalent at tenant scale.
- [03-HotKeysAndHotPartitions.md](03-HotKeysAndHotPartitions.md) — within-cell partitioning.
- [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md) — the pattern used when moving tenants between cells.
- AWS [Well-Architected Framework — Reliability Pillar (cell-based architecture)](https://docs.aws.amazon.com/wellarchitected/) — canonical reference.
- Colm MacCárthaigh's talks on [Shuffle Sharding](https://aws.amazon.com/blogs/architecture/shuffle-sharding-massive-and-magical-fault-isolation/) — the seminal write-up.
