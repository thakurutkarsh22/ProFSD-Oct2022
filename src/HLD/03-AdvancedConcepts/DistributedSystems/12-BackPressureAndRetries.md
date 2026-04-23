# Back-Pressure, Retries, Circuit Breakers & Hedged Requests

> **Difficulty:** Medium | **Time:** 2 hours | **Priority:** Must Know

The hardest production incidents are rarely caused by the failure
itself — they're caused by **the system's response to the failure**.
Retries amplify load. Timeouts pile up connections. A slow dependency
drags down every caller. This document covers the four weapons every
distributed system needs: **back-pressure**, **exponential backoff +
jitter**, **circuit breakers**, and **hedged requests**.

---

## Table of Contents

1. [The Problem: Failures that Amplify](#1-the-problem-failures-that-amplify)
2. [Retry Storms](#2-retry-storms)
3. [Exponential Backoff + Jitter](#3-exponential-backoff--jitter)
4. [Retry Budgets & Global Caps](#4-retry-budgets--global-caps)
5. [Circuit Breakers](#5-circuit-breakers)
6. [Timeouts — the Silent Killers](#6-timeouts--the-silent-killers)
7. [Back-Pressure](#7-back-pressure)
8. [Load Shedding & Admission Control](#8-load-shedding--admission-control)
9. [Hedged Requests ("The Tail at Scale")](#9-hedged-requests-the-tail-at-scale)
10. [Bulkheads](#10-bulkheads)
11. [Design Checklist](#11-design-checklist)
12. [Interview Q&A](#12-interview-qa)

---

## 1. The Problem: Failures that Amplify

```
 HEALTHY SYSTEM                      FAILURE MODE
 ──────────────                      ────────────
   100 rps  ──► svc (10ms p99)         100 rps  ──► svc (slow!)
                                                        │
                                                        ▼
                                       timeouts → retries (3x)
                                                        │
                                                        ▼
                                            300 rps against a
                                            SICK service → worse
                                                        │
                                                        ▼
                                                 total collapse
```

Common failure amplifiers:

```
  UNBOUNDED RETRIES          1 failure → 1 million attempts
  NO BACKOFF / NO JITTER     synchronized retry waves
  LONG CLIENT TIMEOUTS       queues fill before failing
  NO CIRCUIT BREAKER         keep hammering a dead dep
  SHARED THREAD POOLS        one slow dep starves all others
  RETRIES ACROSS LAYERS      3×3×3 = 27 effective attempts
```

---

## 2. Retry Storms

```
 100 clients ──► service (fails briefly)
                 │
                 ▼
 Every client retries 3x with no backoff
                 │
                 ▼
 400 requests/sec slam the now-recovering service → crashes it AGAIN.
 Clients retry more → 1600 rps → crash → ...
```

### 2.1 The synchronized wave

Without jitter, retries land at the exact same offset after failure:

```
  time ─────────►
     │       │       │       │       │
     ▼       ▼       ▼       ▼       ▼
    failure  retry   retry   retry   retry
     ALL      ALL     ALL     ALL     ALL
     clients  clients clients clients clients

  → The retry spikes are HIGHER than the original load.
```

---

## 3. Exponential Backoff + Jitter

### 3.1 Plain exponential backoff

```
  attempt_n_delay = base × 2^n     (n = 0, 1, 2, ...)

  base = 100ms:
    attempt 1 → 100ms
    attempt 2 → 200ms
    attempt 3 → 400ms
    attempt 4 → 800ms
    attempt 5 → 1600ms
    ...
```

### 3.2 Problem: no jitter → synchronized thundering herd

```
  All clients retry at EXACTLY the same offset, so spikes remain.
```

### 3.3 Add JITTER

AWS published "Exponential Backoff And Jitter" (2015) with three schemes:

```
  FULL JITTER       delay = random(0, cap)
                    Flattens spikes most aggressively. Good default.

  EQUAL JITTER      delay = base/2 + random(0, base/2)
                    Keeps monotonic "wait more each time"; still spreads.

  DECORRELATED      sleep = min(cap, random(base, sleep × 3))
                    Uses previous sleep → naturally decorrelates.
```

### 3.4 Visual: with/without jitter

```
 WITHOUT JITTER            WITH JITTER (FULL)
 ──────────────            ──────────────────
  spike    spike             ████ ██ █ █ ██ █
   ▓        ▓                 (evenly spread over time)
   ▓        ▓                 tail latency recovers
   ▓        ▓
  smooth  smooth             smooth                smooth
  gap     gap                tiny gaps, no spikes
```

### 3.5 Common defaults

```
  base      100-200 ms
  cap       30 s (don't retry forever)
  max_retries   3-5
  timeout_per_attempt   ≤ 1-2 s for interactive, longer for background
```

### 3.6 Pseudo-code (FULL JITTER)

```
  attempt = 0
  loop:
      result = call()
      if success: return result
      if attempt >= max_retries: raise
      sleep(random(0, min(cap, base * 2^attempt)))
      attempt += 1
```

---

## 4. Retry Budgets & Global Caps

Even well-behaved retriers can collectively kill a dependency.
A **retry budget** limits TOTAL retry volume globally.

### 4.1 Client-side budget (Envoy / gRPC)

```
  retry_ratio = retries / total_requests
  if retry_ratio > threshold (e.g., 20%):
     STOP allowing retries until ratio drops

  Prevents a broken dep from causing 3× load amplification
  while still allowing normal-rate transient-error retries.
```

### 4.2 Token bucket for retries

```
  Each client has a bucket refilled at some rate.
  Each retry attempts to TAKE A TOKEN.
  If bucket is empty → fail fast, no retry.
```

### 4.3 Why "retry 3 times at every layer" is bad

```
  User → Gateway (retry 3x)
           → Service A (retry 3x)
             → Service B (retry 3x)
               → DB

  If DB fails: B makes 3 attempts, A makes 3 × 3 = 9, Gateway 27.
  Real-world retry amplification.

  Fix: retry at the HIGHEST level only, OR drastically lower retry
  counts at lower layers.
```

---

## 5. Circuit Breakers

Adapted from electrical engineering: stop sending requests to a
known-bad dependency; "trip" to avoid cascading failure.

### 5.1 State machine

```
                ┌───────────────┐
                │    CLOSED     │   normal operation: request passes through
                │  (all good)   │
                └──────┬────────┘
                       │  error rate > threshold
                       │  (e.g., 50% over 10 reqs)
                       ▼
                ┌───────────────┐
                │     OPEN      │   fail fast: immediate error, no call
                │ (cooling off) │
                └──────┬────────┘
                       │   after cooldown (e.g., 30s)
                       ▼
                ┌───────────────┐
                │   HALF-OPEN   │   allow N trial requests
                │               │
                └──────┬────────┘
               success │    │  failure
                       ▼    ▼
                  CLOSED    OPEN
```

### 5.2 Benefits

```
  ✓ Fail fast — don't consume threads on calls that will time out
  ✓ Give the dep time to recover
  ✓ Protect your own resources (conn pool, CPU, memory)
  ✓ Signal upstream quickly: they can fail fast or degrade
```

### 5.3 Tuning parameters

```
  error_threshold           trip above X% in N requests
  volume_threshold          don't trip on low-volume noise (need N reqs)
  sleep_window              time to stay OPEN
  half_open_requests        how many trial calls allowed
```

### 5.4 Configurations to AVOID

```
  ✗ Global breaker per POD — one bad host trips, all callers lose dep
     Better: per-upstream-instance breakers.

  ✗ Too-sensitive thresholds — 1 error in 2 requests trips.
     Leads to oscillation and false trips.

  ✗ No half-open trial — goes OPEN → CLOSED immediately → flaps.
```

### 5.5 Examples

```
  Netflix Hystrix        (Java, deprecated but foundational)
  Resilience4j           (modern Java replacement)
  Envoy outlier detection (passive, per-upstream)
  Istio / Linkerd        mesh-level breakers
  gRPC interceptors      per-client circuit breakers
```

---

## 6. Timeouts — the Silent Killers

Without a timeout, a hung dependency will consume every thread
forever. With a bad timeout, you amplify load.

### 6.1 Three kinds you need

```
  CONNECT TIMEOUT       how long to wait to establish a connection
                         (small: 1-2s typical)
  READ / REQUEST TIMEOUT how long a single request waits for response
                         (size based on SLA; sub-second for user-facing)
  OVERALL TIMEOUT        max time across retries and hops
                         (slightly less than caller's timeout)
```

### 6.2 Timeout budget cascades

```
  User's overall deadline:   2 seconds
  Gateway calls A:             max 1500 ms (leaves 500 ms for retries)
  A calls B:                   max 1000 ms
  B calls DB:                  max 500 ms

  Rule: EACH layer's timeout < CALLER's timeout, so your call
  returns BEFORE the caller gives up.
  Without this: caller times out, retries → amplification.
```

### 6.3 gRPC deadlines propagation

gRPC propagates a **deadline** (absolute time) through the call chain.
Each hop sees how much time remains and enforces it, avoiding wasted work.

```
  Client sets deadline = now + 2s.
  Gateway receives, sees 1900 ms left, sends to A with 1800 ms.
  A receives, sees 1600 ms left, sends to B with 1500 ms.
  B receives, sees 1300 ms — decides "too short for this work" →
     aborts immediately with DeadlineExceeded.
```

---

## 7. Back-Pressure

Instead of silently dropping messages or timing out, the consumer
tells the producer to **slow down**.

### 7.1 Two flavors

```
 PUSH with FLOW CONTROL
   consumer signals producer: "I can handle N more." Producer blocks/
   pauses when N=0. Reactive Streams Semaphore, RSocket credits.

 PULL
   consumer drives the pace by pulling only what it can process.
   (Kafka consumer, any pull-based queue.)
```

### 7.2 Visual

```
   fast producer                    slow consumer
   ┌──────────────┐    queue      ┌──────────────┐
   │              │  ──────────►  │              │
   │              │                │              │
   └──────┬───────┘                └──────┬───────┘
          ▲                               │
          │          "slow down"          │
          │       (or block sends)        │
          └───────────────────────────────┘

  Mechanisms:
    TCP windowing                 transport layer
    Reactive Streams request(n)   app-level demand
    Bounded channels              Go buffered chan, Rust mpsc
    RSocket / gRPC flow control   streaming RPCs
    HTTP/2 window_update frames
```

### 7.3 What happens without back-pressure

```
  fast producer ──────► unbounded queue ──────► slow consumer

  Queue grows until OOM.
  Or, messages drop silently when queue is fixed-size without signaling.
  Either way, users get slower responses AND data may be lost.
```

---

## 8. Load Shedding & Admission Control

Under overload, it's better to **fail some requests fast** than to
accept them all and degrade everyone.

### 8.1 Admission control

```
  AT INGRESS (LB / API gateway):
     Estimate current in-flight work and reject ABOVE threshold.

  Request ──► Gateway
               │
               ▼
          in_flight > cap?
           yes   │   no
            │    │
            ▼    ▼
          503   proceed
     Retry-After: 5
```

### 8.2 Priority-based shedding

```
  If overload:
    DROP: low-priority background jobs
    KEEP: user-facing critical requests

  Useful signals:
    Customer tier (free vs paid)
    Request type (read vs write, interactive vs batch)
    Traffic source (internal vs external)
    Per-user quotas
```

### 8.3 Cooperative clients with Retry-After

```
  Server returns 429 or 503 with:  Retry-After: 5
  Good clients respect it; bad clients don't.
  Pair with rate limits enforced at the server level regardless.
```

### 8.4 Google / Facebook style adaptive shedding

Modern systems use signals like **queue depth**, **CPU utilization**,
and **latency p99** to dynamically shed load. Google's paper
"Overload Control for ~1M rps" describes this well — each server
tracks its own health and sheds requests below dynamic thresholds.

---

## 9. Hedged Requests ("The Tail at Scale")

Published by Jeff Dean (2013). The insight: **tail latency dominates
user perception**, and you can kill it by issuing a duplicate request
to a backup once the first request crosses the p95 line.

### 9.1 Mechanics

```
  Client fires request to Replica A.
  If no response after p95_of_A_latency (e.g., 50 ms):
     Fire SAME request to Replica B.
     Use whichever replies first.
     Cancel the laggard.

  Net effect:
    - Happy path: no extra cost.
    - Slow-node path: B rescues us from A's pause.
    - p99/p99.9 latency improves ~3-10×.
```

### 9.2 Cost

```
  Extra load = ~5% (since only 5% of requests actually hedge).
  Worth it for latency-critical reads.
```

### 9.3 Variants

```
  TIED HEDGING
     Fire second request AND tell first: "hedge fired, race to finish."
     Losing side cancels work. Reduces wasted work.

  CROSS-REPLICA HEDGING
     Pick a DIFFERENT replica for the hedge, so both shouldn't
     have same slow path issue (disk, GC).
```

### 9.4 When NOT to use

```
  ✗ Writes (hedging = duplicate writes unless idempotent)
  ✗ Already-saturated backends (hedging adds load exactly when you
     can least afford it; some libs disable hedging at > X% CPU)
  ✗ Stateful sessions tied to one replica
```

---

## 10. Bulkheads

Named after ship compartmentalization. **Isolate resources per
dependency** so one slow dep can't starve all callers.

### 10.1 Without bulkheads

```
  100 app threads share a single pool.
  Dep B becomes slow → 100 threads blocked on B calls.
  Dep A is healthy but has NO THREADS to serve its requests.
  Whole app looks down.
```

### 10.2 With bulkheads (per-dependency thread pools)

```
             ┌────────────── App ──────────────┐
             │                                  │
  Requests   │  pool_A (20) ──► Dep A (healthy) │
             │  pool_B (20) ──► Dep B (SLOW)    │
             │  pool_C (20) ──► Dep C (healthy) │
             │  pool_D (20) ──► Dep D (healthy) │
             │                                  │
             └──────────────────────────────────┘

  Dep B's 20 threads all saturated → B calls fail fast.
  Dep A, C, D pools untouched → those calls keep working.
```

### 10.3 Implementations

```
  Hystrix              Java, thread isolation per command
  Resilience4j         Semaphore-based isolation
  Envoy                Per-upstream connection pools + retry budgets
  Go                   Per-dep bounded channels + goroutines
```

---

## 11. Design Checklist

```
☐ Every outbound call has a TIMEOUT (connect + overall)
☐ Timeouts CASCADE with budget (outer > sum of inners)
☐ Retries use EXPONENTIAL BACKOFF + JITTER
☐ Retries have a BUDGET (cap total retry rate)
☐ Retries only at one layer (don't stack 3x * 3x * 3x)
☐ CIRCUIT BREAKERS trip fast on failing dependencies
☐ Dependencies have BULKHEADS (isolated thread pools / connections)
☐ Server has ADMISSION CONTROL / LOAD SHEDDING
☐ Server supports BACK-PRESSURE (bounded queues + flow control)
☐ Hedged reads for latency-sensitive endpoints (idempotent only)
☐ Graceful degradation paths (serve cached/partial data)
☐ Chaos testing regularly exercises all the above
☐ Runbooks document what breaks what
```

---

## 12. Interview Q&A

### Q1. "Your service gets a retry storm during an incident. What do you do?"

> 1. **Circuit-break** the failing dependency to fail fast.
> 2. **Shed load** at the ingress with 503 + Retry-After.
> 3. **Kill synchronized retries** with exponential backoff + jitter.
> 4. **Cap retries globally** via a retry budget (e.g., 20% of traffic).
> 5. For long-term fix: move retries to the HIGHEST layer only
>    and add admission control to prevent retry amplification.

### Q2. "Why add jitter to exponential backoff?"

> Without jitter, clients retry at synchronized time offsets after a
> failure, producing periodic load spikes that re-crash the recovering
> service. Jitter spreads retries randomly across the backoff window,
> turning spikes into a smooth flow.

### Q3. "Explain the circuit-breaker pattern."

> A stateful wrapper around a dependency call with three states:
> **CLOSED** (normal), **OPEN** (trip on error rate threshold; fail
> fast without calling), **HALF-OPEN** (allow a few probe requests
> after a cooldown; CLOSE on success, reopen on failure). Prevents
> your service from wasting threads on a known-bad dep and gives the
> dep breathing room to recover.

### Q4. "What's wrong with timeouts that aren't cascaded?"

> If inner timeouts are equal to or longer than the outer caller's
> timeout, the caller gives up first — but the inner work keeps
> running, wasting resources. Worse, the caller retries, spawning
> *another* inner call while the first is still in flight.
> Rule: each layer's timeout < caller's remaining deadline.

### Q5. "Explain hedged requests and why they're safe."

> Fire a duplicate request to a second replica after the primary has
> taken longer than p95. Use whichever responds first. Safe for
> **idempotent** reads — they can be issued twice without side effect.
> Kills tail latency (p99/p99.9) at roughly 5% extra load.
> Unsafe for non-idempotent writes unless paired with idempotency keys.

### Q6. "What's a bulkhead and when do you use one?"

> A bulkhead is resource isolation per dependency: dedicated thread
> pool / connection pool / semaphore for each downstream. One slow
> dep can saturate its own pool but cannot block threads serving
> other deps. Use it whenever multiple downstreams share upstream
> thread/connection resources — i.e., almost always.

### Q7. "How would you prevent cascading failures in a microservice system?"

> Combine: tight per-hop **timeouts** with cascaded budgets,
> **circuit breakers** per upstream, **bulkheads** isolating resources,
> **exponential backoff + jitter + retry budgets**, **load shedding**
> at the ingress, and **graceful degradation** (serve cached/partial
> data). Service mesh (Envoy / Istio) can implement most of this
> without app code changes.

### Q8. "Your downstream is sometimes slow but not truly failing. How do you design for it?"

> Don't rely only on circuit breakers (they may not trip on "slow but
> succeeding"). Use **hedged requests** for reads to tolerate tail
> latency. Set **tight p99 timeouts** so "too slow" counts as failure.
> Add **outlier detection** to eject persistently-slow instances
> from the upstream pool (Envoy does this automatically).

### Q9. "Back-pressure vs load shedding — what's the difference?"

> **Back-pressure** signals the producer to slow down (cooperative
> flow control). **Load shedding** rejects requests at ingress when
> overloaded (uncooperative). Back-pressure preserves every request
> at a slower rate; load shedding drops requests so the ones you
> accept can be served within SLA. Production systems use both —
> back-pressure internally, load shedding at boundaries.

### Q10. "A customer is saturating your shared cluster. What do you do?"

> Per-tenant **rate limits + quotas** + **admission control** per
> tenant. If isolation matters more, move the customer to a **dedicated
> cell** (cell-based architecture). In the interim: prioritize other
> tenants' requests when shedding load, so the noisy neighbor hits
> 429s first.

---

## 13. Further Reading

- Jeff Dean, "The Tail at Scale" (2013) — hedged requests, latency
- AWS Architecture Blog, "Exponential Backoff And Jitter" (2015)
- Nygard, "Release It!" — circuit breakers, bulkheads, stability patterns
- Netflix Tech Blog — Hystrix, resilience patterns at scale
- Google SRE Book ch. "Addressing Cascading Failures"
- Envoy / Istio docs — retry budgets, outlier detection

---

> **Previous:** [11-DeliverySemantics.md](./11-DeliverySemantics.md) ·
> **Next:** [13-ServiceDiscovery.md](./13-ServiceDiscovery.md)
