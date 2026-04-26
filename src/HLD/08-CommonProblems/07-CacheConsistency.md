# Cache Consistency — Stale Cache & Read-After-Write Inconsistency

> **TL;DR.** A cache is a *second source of truth* for the same data — and the moment you have two copies, they can disagree. The two failure modes users feel are **stale reads** ("my profile still shows the old name 5 minutes after I edited it") and **read-after-write inconsistency** ("I saved, then reloaded, and my save is *gone*"). Pick the right cache pattern (aside / through / behind / around) and the right invalidation strategy; no single answer covers all workloads.

---

## 1. What the user experiences

### Stale cache

User changed their email 3 minutes ago. The UI still shows the old one because:
- DB is updated.
- Cache still holds the old value (TTL not elapsed, no invalidation sent).

### Read-after-write (RAW) inconsistency

User *just* saved, UI navigates and re-fetches, and the save is missing because:
- Write went to the primary.
- Read went to a replica that hasn't replicated yet.
- Or: the write invalidated the cache, but another reader repopulated it with stale data from a lagging replica *before* replication caught up.

```
timeline                       primary        replica          cache
──────────                     ───────        ───────          ─────
t=0   write A=1                   1              0              —
t=1   cache invalidate            1              0              miss
t=2   some reader misses          1              0              miss
          reads replica                                           └── reads 0 !
t=3   reader fills cache with 0   1              0              0  ← STALE
t=4   replica catches up          1              1              0  ← STILL stale until TTL
```

This specific race — invalidate → read-from-replica → refill-with-old-data — is the most common cache bug in production.

---

## 2. Cache patterns and their consistency characteristics

```
┌──────────────────────────────────────────────────────────────┐
│                   FOUR CANONICAL PATTERNS                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│ CACHE-ASIDE (lazy loading)                                   │
│   App reads: cache → miss → DB → fill cache                  │
│   App writes: DB → invalidate cache                          │
│   Consistency: eventual; suffers from RAW if not careful     │
│                                                              │
│ READ-THROUGH                                                 │
│   App reads: cache; cache fetches from DB on miss            │
│   App writes: go via cache (write-through) or DB directly    │
│   Consistency: same as aside but cleaner API                 │
│                                                              │
│ WRITE-THROUGH                                                │
│   App writes: cache → DB synchronously                       │
│   Consistency: cache = DB always, but every write has        │
│     cache + DB latency                                       │
│                                                              │
│ WRITE-BEHIND / WRITE-BACK                                    │
│   App writes: cache → ack immediately; cache flushes to DB   │
│     asynchronously                                           │
│   Consistency: DB lags the cache. If cache dies, data lost.  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Most production systems are **cache-aside** for reads with **write-through (or explicit invalidation) for writes**. See [../01-Fundamentals/04-Caching.md](../01-Fundamentals/04-Caching.md).

---

## 3. Invalidation strategies (hardest problem in CS, famously)

### 3.1 TTL only

Simplest. Set a TTL; after expiry the value is refetched. Bounded staleness.

- Pros: zero coordination, handles any update source.
- Cons: staleness window = TTL. 60 s TTL ⇒ users can see 60 s old data.

### 3.2 Explicit invalidation after write (the classic bug factory)

```
write A=1 to DB
DELETE cache(A)
```

**Race condition:** between the DB write and the DELETE, a reader might miss, read the *old* value from a replica, and repopulate the cache. Then your DELETE fires too late — the new value already invalidated, but a stale value was re-cached after.

**Fixes:**
1. **Delete, *not* update** — on write, delete the key; readers repopulate from the fresh DB.
2. **Double-delete (Facebook's trick):** delete before *and* after the write, with a small delay between the second delete and the write ack.
3. **Check-and-set with versioned writes** — every cached value carries a version; on fill, if a newer version was written in the meantime, discard.

### 3.3 Versioned cache keys

Embed the resource version in the cache key:

```
cache key:  "user:42:v17"
write path: store user v18 → cache key is now "user:42:v18"
                                 (old v17 ages out via TTL)
```

Writers don't invalidate; readers auto-move to the new version. Great for immutable-by-version data; terrible when you need to enumerate "all cached versions of X".

### 3.4 Change-stream / CDC invalidation

Use a change-data-capture stream (Debezium, DynamoDB Streams, PG logical decoding) to emit a message per row write; a cache-invalidator consumer deletes the matching keys.

```
DB  ──► WAL / binlog ──► CDC ──► invalidator ──► DELETE cache key
```

- Pros: invalidation is **reliable** — it's driven by the actual committed write, not by "hopefully the app remembered to invalidate".
- Cons: adds a pipeline (see [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md)).

### 3.5 Write-through (the "easy mode" option)

Every write goes through the cache, which writes to the DB. Cache and DB always agree — at the cost of the cache becoming a required part of the write path (now a SPOF for writes; see [04-SinglePointOfFailure.md](04-SinglePointOfFailure.md)).

### 3.6 Lease-based invalidation (Memcache at Facebook)

On a miss, the cache issues a *lease* to the reader. If a concurrent write happens, the cache invalidates the lease so the filled value is rejected — preventing the stale-refill race. Described in the Facebook "Scaling Memcache" paper.

---

## 4. Read-after-write consistency — three solutions

### 4.1 Read-your-writes by pinning

After a write, route the same session's reads to the **primary** for a short window (e.g. 10 s).

```
        ┌─── writes ──────► primary
client  │
        └─── reads ───────► replica (normally)
                           primary (if "pinned" after recent write)
```

- Implementation: store a `last_write_at` in the session / cookie; router checks it.
- Cost: primary load spikes while many users are in the "post-write" window.

### 4.2 Monotonic / bounded staleness reads

Instead of pinning to primary, tell the replica: "I've seen token T = LSN 12345. Serve my read at a replica that has replayed ≥ T."

- PostgreSQL: `pg_wal_lsn_diff`, hot_standby_feedback, or `synchronous_commit`.
- MongoDB: `readConcern "majority"` + causal consistency sessions (`$clusterTime`).
- Cassandra: `LOCAL_QUORUM` read/write + driver-level session tokens.

```
client writes → gets back LSN 12345
client reads → replica A at LSN 12300 → wait or redirect to replica B at LSN 12400
```

### 4.3 Write-through cache + read from cache after write

The write populates the cache synchronously. Immediately reading the cache shows the new value regardless of replica lag. Works as long as the cache is the read path.

---

## 5. CAP / PACELC trade-offs

From [../02-BuildingBlocks/01-CAPTheorem.md](../02-BuildingBlocks/01-CAPTheorem.md) — every cache strategy is a point on the CAP triangle.

| Strategy | Consistency | Availability | Latency |
|----------|-------------|--------------|---------|
| TTL only | eventual (bounded by TTL) | high | low |
| Invalidate-on-write | stronger, races possible | high | low-mid |
| CDC invalidation | stronger, reliable | high | mid (pipeline lag) |
| Write-through | strong (cache=DB always) | lower (write requires cache up) | higher |
| Primary-pinned RAW | strong for writer | lower on primary | higher |
| Replica-with-LSN | strong via waiting | a touch lower | higher on reads right after writes |

There's no free lunch. Pick based on **who is inconvenienced** by staleness vs by latency.

---

## 6. Choosing the right strategy — decision tree

```mermaid
flowchart TD
    Start["Choose cache consistency"] --> Q1{"Is stale data<br/>acceptable at all?"}
    Q1 -->|No — money / inventory| WT["Write-through<br/>or no cache"]
    Q1 -->|Yes, bounded| Q2{"What's the<br/>staleness budget?"}
    Q2 -->|> 1 minute| TTL["TTL only"]
    Q2 -->|< 10 seconds| Q3{"Who updates<br/>the data?"}
    Q3 -->|Only our app| INV["Cache-aside +<br/>delete-on-write<br/>+ double-delete"]
    Q3 -->|External<br/>(DB replication,<br/>other services)| CDC["CDC-driven<br/>invalidation"]
    Start --> RAW{"Need read-after-write?"}
    RAW -->|Yes, strict| Pin["Primary-pin<br/>for a short window"]
    RAW -->|Yes, loose| Wait["Wait-for-LSN /<br/>causal sessions"]
    RAW -->|Not strictly| OK["TTL / eventual<br/>is fine"]
```

---

## 7. Worked examples

### 7.1 User profile (moderate consistency)

- Cache: Redis, TTL 5 min.
- On write: `DELETE user:42` + DB update.
- Double-delete with 500 ms delay to absorb replication lag.
- RAW: after update, frontend stays on old primary session for 10 s.

### 7.2 Product catalog (eventual is fine)

- Cache: CDN + Redis.
- TTL: 1 hour.
- Invalidation: CDC pipeline from the catalog DB; at-most 10 s staleness.
- No RAW requirement (edits are rare, admin-only).

### 7.3 Bank account balance (strong)

- **No caching** of the authoritative balance. Or write-through + primary-only reads.
- UI caches a *hint* with TTL 2 s just for rendering smoothness; real balance is re-fetched on any action.

### 7.4 Feed / timeline (complex)

- Precomputed feed in Redis with TTL.
- On new post: fan-out write to followers' feed caches (see [../05-DesignMedium/08-Twitter.md](../05-DesignMedium/08-Twitter.md)).
- RAW for self-posts: "see your own post immediately" implemented by inserting into the session's local cache *before* the async fan-out completes.

---

## 8. Observability

- **Cache hit rate** per key prefix — sudden drops mean invalidation is too aggressive.
- **Age of cached value** on access (a `cached_at` timestamp) — if p99 is close to TTL you might be serving too much stale data.
- **Staleness measurements** — compute `(now - db_updated_at)` on a sample of cached values; alert if > target.
- **Replication lag** per replica — spikes directly cause RAW bugs.
- **Invalidation queue depth** (if using CDC) — growing queue means cache is diverging from truth.

---

## 9. Anti-patterns

| Anti-pattern | Why it's bad |
|--------------|--------------|
| "Update cache on write" | The race between cache and DB update creates lost writes. Always *delete* then let a reader refill. |
| One global TTL | Hot data and cold data have different freshness needs. Tune per namespace. |
| No TTL at all | Entries that were valid yesterday and nobody invalidated them still live. Always have a safety TTL. |
| Cache across deployment | Schema changes mean old cache entries become invalid; version the cache key or flush on deploy. |
| "We'll rely on Redis keyspace events for invalidation" | Keyspace events are best-effort; they're dropped under load. Don't depend on them for correctness. |
| Cache layer in front of a cache layer with different TTLs | The inner is still stale when the outer fetches it — staleness compounds. Flatten the hierarchy. |

---

## 10. Interview talking points

- **Name the specific race.** "Classic lost-invalidation race — writer invalidates cache, then a concurrent reader loads from a *lagging replica* and refills with old value."
- **Prescribe: delete-on-write + double-delete + short TTL safety net.**
- **For RAW: offer three options by cost.** Pin to primary (simplest, hot primary), monotonic tokens (richest), or write-through (safest, slowest).
- **Call out CDC as the "reliable invalidation" upgrade.** Mention Debezium / DynamoDB Streams.
- **Be explicit about the bounded staleness number.** "This gives us at most 5 s of staleness, which is fine for product browsing, not OK for checkout."

---

## 11. Related reading

- [../01-Fundamentals/04-Caching.md](../01-Fundamentals/04-Caching.md) — cache patterns in depth.
- [../03-AdvancedConcepts/DistributedSystems/02-ConsistencyModels.md](../03-AdvancedConcepts/DistributedSystems/02-ConsistencyModels.md) — linearizability, causal, eventual.
- [01-ThunderingHerdAndCacheStampede.md](01-ThunderingHerdAndCacheStampede.md) — cache holes and stampedes often travel with staleness bugs.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — CDC-driven invalidation.
- [03-HotKeysAndHotPartitions.md](03-HotKeysAndHotPartitions.md) — local near-caches as a consistency trade-off.
