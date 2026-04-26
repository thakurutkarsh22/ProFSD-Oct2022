# Gray Failures and Health Check Design

> **TL;DR.** Your system rarely fails cleanly. The hard outages are **gray failures**: a node responds to pings but returns wrong answers; a disk is 10× slower but "works"; a TCP connection stays open but packets are dropped; a process GCs for 30 seconds but reports "healthy". Classic up/down health checks miss every one of these — and the surrounding system (load balancer, orchestrator, peer nodes) keeps routing traffic to the sick node, amplifying the pain. The fix is **differential observability**: the node's own view of itself, its peers' view of it, and the clients' view of it must be compared. When the views disagree, it's gray failure.

---

## 1. What is a gray failure?

```
┌─────────────────────────────────────────────────────────────┐
│   BLACK FAILURE        │        GRAY FAILURE                 │
├────────────────────────┼─────────────────────────────────────┤
│  Process crash         │  Process up, producing bad output   │
│  Network cable pulled  │  50% packet loss                    │
│  Disk dead             │  Disk 10× slower (bad sector retry) │
│  Host rebooted         │  Clock skew 5 minutes               │
│  Port closed           │  Port open, app deadlocked          │
│  Obvious alarms fire   │  Nothing alarms; performance tanks  │
└────────────────────────┴─────────────────────────────────────┘
```

Gray failures have two defining properties:

1. **Differential observability** — the sick node thinks it's healthy; an observer (peer, client, LB) sees it as sick.
2. **Partial functionality** — not binary; some requests work, some don't; latency spikes; errors intermittent.

Why they're the hard case:
- Auto-remediation doesn't trigger (the node says "I'm fine").
- Load balancer keeps sending traffic.
- Peer replicas keep expecting responses.
- You have a "working" node making everything worse.

Research (Microsoft, "Gray Failure: The Achilles' Heel of Cloud-Scale Systems") found that **the majority of real outages in large systems start as gray failures**, not clean crashes.

---

## 2. Canonical gray failure scenarios

| Scenario | Why it's gray |
|----------|---------------|
| **JVM long GC pause (10-30 s)** | Process alive; health checks pass; zero throughput during pause |
| **Disk with bad sectors + retries** | Occasional 5 s reads; reads *eventually* succeed |
| **Overloaded neighbor tenant** | Kernel scheduling delays; latency doubles; nothing broken |
| **Asymmetric network partition** | A→B works, B→A doesn't → gossip confusion |
| **Kernel memory pressure / swap** | Process alive; syscalls slow |
| **Dependency degraded not down** | Your service is "up", but every request fails downstream |
| **Partial packet loss (5%)** | TCP retransmits; avg latency rises but doesn't crash |
| **Queue filling silently** | Incoming processed; new ones wait; caller times out |
| **Cache hit rate collapse** | Everything "up", DB on fire, latency 100× |
| **Expired TLS cert on one backend** | Most traffic works (session resumption); some errors |
| **Corrupt index on one replica** | Writes replicate but reads return wrong data |
| **Time drift** | Tokens appear expired; tracing timestamps wrong; quorum fails |

None of these show up as "process dead". All of them hurt users.

---

## 3. Why classic health checks fail

```python
# naive health check endpoint
@app.route("/healthz")
def healthz():
    return "OK", 200
```

This reports only "the process is alive". It's blind to:

- GC pauses (returns after the pause; looks fine in a single probe).
- Downstream dependencies.
- Queue depth.
- Disk / memory pressure.
- Data correctness.

### 3.1 The three-level hierarchy

```
┌────────────────────────────────────────────────────────┐
│                LIVENESS vs READINESS vs DEEP            │
├────────────────────────────────────────────────────────┤
│                                                        │
│ LIVENESS  — "am I alive at all?"                       │
│   If false: restart me.                                │
│   Cheap: just answer 200 OK.                           │
│                                                        │
│ READINESS — "can I serve traffic RIGHT NOW?"           │
│   If false: take me out of the LB pool.                │
│   Checks: dependencies reachable, caches warm,         │
│            not in startup/shutdown, not under          │
│            GC pressure.                                │
│                                                        │
│ DEEP / DIAGNOSTIC — "is my subsystem correct?"         │
│   Not automated actions; for SREs.                     │
│   Checks: data integrity, model loaded, indexes OK     │
│                                                        │
└────────────────────────────────────────────────────────┘
```

Kubernetes distinguishes `livenessProbe` and `readinessProbe` for exactly this reason. *Both* should exist; they should *not* be the same endpoint.

### 3.2 Readiness done right

```python
@app.route("/readyz")
def readyz():
    checks = {
        "db":        check_db_ping(timeout=200ms),
        "cache":     check_cache_ping(timeout=50ms),
        "dependency":check_dep_ping(timeout=500ms),
        "gc_pause":  max_gc_pause_last_60s() < 1s,
        "queue":     queue_depth() < queue_depth_limit * 0.9,
        "disk":      disk_io_latency_p99() < 100ms,
    }
    if all(checks.values()):
        return "OK", 200
    return {"failing": [k for k,v in checks.items() if not v]}, 503
```

Rules:
- Short timeouts. A check that hangs defeats its purpose.
- Fail closed: any unknown → 503.
- Expose *why* it failed.
- Tune thresholds to what the LB will react to; overly strict readiness → flapping.

---

## 4. Differential observability — the real trick

The sick node can't always know it's sick. So ask other perspectives:

```
┌────────────────────────────────────────────────────────────┐
│             THREE VIEWS OF NODE HEALTH                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
│   SELF view        — node's own /healthz, /readyz          │
│                     (can lie: node is the last to know)    │
│                                                            │
│   PEER view        — other nodes' success rates calling it │
│                     (accurate for intra-service calls)     │
│                                                            │
│   CLIENT view      — users' end-to-end success / latency   │
│                     (ground truth; but noisy)              │
│                                                            │
│   EXTERNAL PROBE   — synthetic canary from outside         │
│                     (represents users; easy to alert on)   │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

Gray failure = these views disagree. The LB sees the node's self-report as healthy; clients / peers see it as failing. That disagreement is the signal.

### 4.1 Peer-observed health

Every client (intra-service or LB) tracks per-target success rate + latency:

```
per_target_success_rate[target] = ewma(success over last 30s)
if rate < 0.9:   mark target "suspect" → reduce traffic
if rate < 0.5:   mark target "failed"  → stop sending
```

This is **passive health checking** (outlier detection / failure accrual), used by Envoy, Finagle, gRPC health infra. Passive checks always beat active ones for gray failures because they use real request outcomes.

### 4.2 External synthetic probes

A canary service outside your deployment hits the system every N seconds as a user would. Alerts on latency/error rate. This is the **ground truth** — if it fires but nothing else does, you have gray failure.

### 4.3 RUM (Real User Monitoring)

Browser or mobile SDK beacons back user-perceived latency and error rate. Correlates with backend health. If RUM shows pain but backend looks fine: gray failure, possibly in the edge network.

---

## 5. Detection techniques

### 5.1 Outlier detection across a fleet

Given N replicas, compute per-node P99 latency. If one node's P99 is > 3× the median, it's an outlier:

```
median_p99 = median(p99_latency[:])
for node in fleet:
    if p99_latency[node] > 3 * median_p99:
        mark_suspect(node)
```

This catches slow nodes without needing absolute thresholds (they drift).

### 5.2 Quorum-based

Ask multiple peers: "Is X healthy?" Majority says no → X is sick.

Used in Cassandra's gossip, ZK ensembles, etcd health.

### 5.3 Error budget burn

If your 30-day error budget is 0.1% and you're burning 1%/minute, something is wrong *now* — even if each component says it's healthy.

### 5.4 Correlation on cardinality

Group latency by dimension (node, AZ, version, tenant). Which dimension explains the tail? Often a single node or AZ.

---

## 6. Response: what to do with a suspect node

```
    0.  normal
    │
    ▼
    1.  slightly reduce traffic (10-20%)
    │    Continue monitoring; if recovers → back to normal
    │
    ▼
    2.  quarantine (remove from LB pool)
    │    Leave process running; let it reset / GC / drain
    │
    ▼
    3.  kill & replace (let scheduler reschedule)
```

Key: **graduated response**. Don't instantly kill a suspect node — false positives during high-load events cause cascading failures ("I'll mark everyone unhealthy"). Use **panic thresholds** in LBs: "Don't outlier-eject more than 30% of the fleet at once."

### 6.1 Panic mode (Envoy's idea)

If too many nodes are marked unhealthy, the LB has a bug (or a systemic problem). Go back to load-balancing over *all* nodes, because serving requests through degraded nodes is better than serving none.

### 6.2 Auto-remediation loops

Careful — if your auto-healing kills nodes too aggressively, a minor gray failure becomes a rolling restart. Classic cascade. Rate-limit auto-remediation; alert when it fires "too often".

---

## 7. Designing for gray failure — system-level

### 7.1 Hedging

Send the request to two replicas; take whichever answers first. Cost: 2× traffic, but latency is that of the *faster* replica. Clips the tail; unaffected by one slow gray-failing replica.

Usually applied only to reads and only to P99-sensitive paths.

### 7.2 Request-level timeouts + retries on a different node

Client-side:
```
request with 1 s timeout to node X
  ↓ timeout
retry once to a DIFFERENT node (not same one!)
```

This treats gray failure as probabilistic: another random node is probably fine.

### 7.3 Tail tolerance

Accept that a fraction of nodes will be gray; design for the system to succeed even when (e.g.) 5% of backends return slowly. Hedging, subsetting, randomization.

### 7.4 Subsetting

Each client uses a random subset of N backends instead of all of them. (This is [shuffle sharding](21-CellBasedAndShuffleSharding.md) applied to client-to-server connections.) Combined with hedging, one gray backend rarely affects a given client.

### 7.5 Connection draining

When a node says "I'm shutting down / unhealthy", clients should drain existing connections gracefully — don't send new requests, finish in-flight ones.

---

## 8. Clock skew — a special gray failure

Time is a particularly nasty gray failure:

- Tracing / ordering breaks.
- JWTs appear expired.
- Certificates flap (expired by local clock, valid globally).
- Distributed consensus degrades (lease-based protocols fail silently).

Defense:

- NTP / PTP everywhere. Monitor skew as a first-class metric.
- Alarm at ≥ 500 ms skew.
- Prefer monotonic clocks for time intervals.
- Use hybrid logical clocks for ordering if skew can exceed your tolerance.
- For leases / locks: add fencing tokens ([`08-DistributedLocksAndLeases.md`](08-DistributedLocksAndLeases.md)).

---

## 9. Observability checklist

- **Latency P50 / P99 / P99.9 per node.** Outlier detection.
- **Error rate per dependency.** Not just globally.
- **Success rate from client perspective (RUM / synthetic).**
- **GC pause duration (JVM / runtime).**
- **Disk IO latency.**
- **Clock skew.**
- **Queue depth and oldest-message age.**
- **Connection pool saturation.**
- **Health check flap rate.** Constant pass/fail toggling is a signal.

---

## 10. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| `/healthz` returns 200 if process is up | Misses every gray failure |
| Same endpoint for liveness and readiness | Dependency failure → infinite restart loop |
| Dependency health in liveness | Downstream flake → *your* pods keep dying |
| Aggressive auto-ejection with no panic threshold | Whole fleet marked unhealthy; total outage |
| No synthetic / external monitoring | Inside-out view only; misses edge gray failures |
| Fixed thresholds instead of relative outliers | Thresholds drift as traffic patterns change |
| No GC / memory pressure monitoring on JVM services | Long pauses pass silently as normal latency blips |
| Retry to the same node on timeout | Doubles load on the sick node |
| Trusting "node reports healthy" | Defeats the entire point of differential observability |
| Gray failure treated like a crash | Kill-loop instead of quarantine |

---

## 11. Interview talking points

- **Name it.** "This looks like a gray failure — partial degradation, not a crash."
- **Three views.** Self / peer / client. Gray failure = disagreement between them.
- **Liveness vs readiness.** Distinguish them; liveness never includes dependencies.
- **Passive health checking** (outlier detection) beats active for gray failures.
- **Hedging + timeouts** clip the long tail caused by gray nodes.
- **Panic threshold** in LBs prevents mass ejection during systemic events.
- **External synthetic monitors** give ground truth.
- **Clock skew is a gray failure.** Alarm on it.
- **Graduated response.** Quarantine before kill; rate-limit auto-remediation.
- **Cite Microsoft's "Gray Failure" paper.** Signals reading depth.

---

## 12. Related reading

- [04-SinglePointOfFailure.md](04-SinglePointOfFailure.md) — redundancy assumes failures are clean; gray breaks that assumption.
- [05-RetryStormsAndCircuitBreakers.md](05-RetryStormsAndCircuitBreakers.md) — circuit breakers for partially-failing downstreams.
- [08-DistributedLocksAndLeases.md](08-DistributedLocksAndLeases.md) — fencing tokens defend against clock-skew gray failures.
- [18-BackpressureAndLoadShedding.md](18-BackpressureAndLoadShedding.md) — shed load before gray failure becomes black.
- [21-CellBasedAndShuffleSharding.md](21-CellBasedAndShuffleSharding.md) — subsetting / shuffle sharding contains gray-failure blast radius.
- Microsoft Research, "Gray Failure: The Achilles' Heel of Cloud-Scale Systems" (HotOS 2017) — seminal paper.
