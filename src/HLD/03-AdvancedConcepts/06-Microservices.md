# Microservices Architecture

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

Microservices are an architectural style where a single application is built as a **suite of small, independently deployable services**, each running in its own process, owning its own data, and communicating over lightweight protocols. The style is less about "many small services" and more about **independent deployability, independent scaling, and independent team ownership**.

This note goes deep on what actually matters in an HLD interview and in production: when to pick monolith vs microservices, how to find good service boundaries, sync vs async communication, the saga/outbox/CQRS family, circuit breakers and bulkheads, service meshes, observability, and the real architectures Netflix, Uber, Amazon, and Spotify operate today.

---

## 1. Monolith vs SOA vs Microservices

There are three points on the same spectrum. Interviewers often test whether you can tell them apart.

```
 MONOLITH                 SERVICE-ORIENTED           MICROSERVICES
 ========                 =========================  =============
                                                   
 ┌──────────────────┐     ┌──────────────────┐     ┌────┐ ┌────┐ ┌────┐
 │   ALL CODE       │     │  Large services  │     │User│ │Ord │ │Pay │
 │  one deployment  │     │  heavy ESB bus   │     └─┬──┘ └─┬──┘ └─┬──┘
 │                  │     │  shared DB often │       │      │       │
 │ ┌────┐ ┌──────┐  │     │                  │     ┌─┴──┐ ┌─┴──┐ ┌─┴──┐
 │ │User│ │Order │  │     │  ┌────────────┐  │     │DB A│ │DB B│ │DB C│
 │ │    │ │      │  │     │  │    ESB     │  │     └────┘ └────┘ └────┘
 │ │Pay │ │Notif │  │     │  └──┬──────┬──┘  │
 │ └────┘ └──────┘  │     │     │      │     │     Each service:
 │      ┌────┐      │     │  ┌──▼──┐┌──▼──┐  │     - own code repo
 │      │ DB │      │     │  │ Svc ││ Svc │  │     - own database
 │      └────┘      │     │  └─────┘└─────┘  │     - own CI/CD pipeline
 └──────────────────┘     │   shared DB      │     - own team
                          └──────────────────┘     - small scope
```

| Dimension | Monolith | SOA (classic) | Microservices |
|---|---|---|---|
| Code organization | one codebase | a few large services | many small services |
| Database | usually one shared DB | often shared DB + ESB | one DB per service |
| Communication | in-process function calls | heavy middleware (ESB, SOAP) | lightweight REST/gRPC/messaging |
| Deployment | one artifact | coordinated releases | independent per service |
| Team scaling | one or a few teams | few teams per service | many "two-pizza" teams |
| Failure blast radius | whole app | whole suite via ESB | isolated to one service (if done right) |
| Typical scale | startup → mid-size | 2000s enterprise | modern cloud-native companies |

### 1.1 When to stay with a monolith

Do **not** start with microservices. Martin Fowler's **"Monolith First"** advice still holds. Start monolith when:

- Team < 10 engineers — Conway's law means you don't have the human bandwidth to own 20 services.
- Domain is still being discovered — service boundaries you draw today will almost certainly be wrong.
- You need fast iteration — one deploy, one test suite, one debugger.
- Strong consistency across the whole model is a hard requirement.

A well-modularized monolith (sometimes called a **modular monolith** or **moduliths**) can scale to hundreds of engineers. Shopify, GitHub, and Stack Overflow ran (and largely still run) as successful modular monoliths.

### 1.2 When microservices actually pay off

| Signal | Why it pushes you to microservices |
|---|---|
| Team > 20 engineers, multiple squads | Independent deploys remove coordination tax |
| Parts of the system have very different scale profiles | Scale only the hot path (e.g. checkout vs admin) |
| Different parts need different tech stacks | ML service in Python, trading engine in Rust, web in Node |
| Need to isolate failure domains (compliance, safety-critical) | One service failing must not take down others |
| Clear, stable bounded contexts in the business | You can actually draw the seams |
| You're hitting deployment pain — 4-hour release trains | Microservices explicitly target this |

> **Interview rule of thumb:** if the interviewer asks "monolith or microservices?", the correct first answer is **"it depends on team size and how stable the domain is"** — then walk through the trade-offs above before committing to one.

---

## 2. Finding Service Boundaries (Domain-Driven Design)

The #1 reason microservices projects fail is **wrong boundaries**. Draw them wrong and you get a distributed monolith — all the operational complexity of microservices with none of the benefits.

### 2.1 Bounded Contexts — the DDD answer

A **bounded context** is a part of the system where a specific domain model applies. The exact same word means different things in different contexts:

```
  The word "Order" across contexts in an e-commerce system:

  ┌───────────────────┐  ┌─────────────────────┐  ┌──────────────────┐
  │  SALES context    │  │  WAREHOUSE context  │  │ BILLING context  │
  ├───────────────────┤  ├─────────────────────┤  ├──────────────────┤
  │ Order =           │  │ Order =             │  │ Order =          │
  │  - cart items     │  │  - picking list     │  │  - invoice lines │
  │  - promotions     │  │  - pack instructions│  │  - tax rows      │
  │  - customer seg   │  │  - SKU + bin        │  │  - payment state │
  │  - upsell history │  │  - carrier choice   │  │  - refund state  │
  └───────────────────┘  └─────────────────────┘  └──────────────────┘

      Same word. Different data. Different lifecycle. Different team.
      → Three services, not one.
```

**Heuristics for finding bounded contexts:**

1. **Ubiquitous language changes** — if the same noun means different things to different stakeholders, that's a boundary.
2. **Change cadence** — code that changes together belongs together; code that changes independently can be separated.
3. **Data ownership** — who is the single authority for this data? That team owns the service.
4. **Organizational structure** — Conway's Law: your architecture will mirror your org chart. Plan for it, don't fight it.
5. **Transactional boundaries** — things that must be updated atomically usually belong in the same service.

### 2.2 Service sizing: micro vs "right-sized"

The industry has moved away from "as small as possible" (nano-services) towards **right-sized services**.

```
  Too big          "Right-sized"           Too small ("nano")
  ─────────        ──────────────          ────────────────────
  The old          Owns one bounded        Wraps a single function
  modular          context end-to-end;     or database table; every
  module with      a small team can        feature needs 10 network
  5 teams on it    understand it fully     hops; distributed monolith

  Symptoms:        Symptoms:                Symptoms:
  - merge hell     - 2-8 engineers own it   - 50+ services for 10 engineers
  - multi-team     - deploys in minutes     - changes require coordinating
    deploys        - clear API contract      N teams anyway
  - slow CI        - owns its data          - most calls are service-to-service
```

Sam Newman's guidance: **"A microservice should be rewritable in two weeks by a small team."** If it isn't, it's too big. If it's rewritable in an afternoon, it's probably too small.

---

## 3. Communication Styles

There is no "right" communication style for microservices. Real systems blend several.

### 3.1 Synchronous vs Asynchronous

```
 SYNCHRONOUS (blocking)                ASYNCHRONOUS (non-blocking)
 ──────────────────────                ────────────────────────────
                                       
 Svc A ──REST/gRPC──► Svc B            Svc A ──► [Kafka/RMQ] ──► Svc B
       ◄─response────                                         └─► Svc C
                                                              └─► Svc D
 A waits for B's answer                A publishes and moves on
 Simple, immediate result              Many consumers can react
 A's latency = A + B latency           A's latency = publish only
                                       
 If B is down → A fails                If B is down → queue buffers
 If B is slow → A is slow              If B is slow → queue grows
                                       
 Use for:                              Use for:
 - user-facing reads (login, search)   - email/SMS, data replication,
 - strong-consistency lookups            search indexing, analytics,
 - request-response semantics            webhooks, cross-service workflows
```

### 3.2 REST vs gRPC vs GraphQL vs messaging

| Protocol | Shape | Sweet spot | Avoid when |
|---|---|---|---|
| **REST/JSON** | request-response, human-readable | external APIs, CRUD, browser clients | need strict schema, many calls/sec internal |
| **gRPC (HTTP/2 + protobuf)** | request-response or streaming, binary | internal service-to-service, polyglot teams, low-latency | browser clients (needs gRPC-Web), ad-hoc debugging |
| **GraphQL** | single endpoint, client picks fields | BFFs for mobile/web, aggregation layer | internal CRUD, real-time pipelines, N+1 is hard |
| **Messaging (Kafka, RabbitMQ, SQS)** | async, publish/subscribe | event-driven workflows, fan-out, backpressure | caller needs an immediate answer |

> **Interview shortcut:** REST externally, gRPC internally, messaging for anything that can be async. Don't invent this on the spot — state it confidently.

### 3.3 The chatty-services trap

If one user request turns into 15 internal calls, you've built a **distributed ball of mud**.

```
  Monolith version:                    Chatty microservices version:
  
  request (1 function call tree)       request ─► Gateway
    │                                     │
    ├─ getUser()        0.5ms              ├─► UserSvc    25ms
    ├─ getOrders()      1ms                ├─► OrderSvc   30ms
    ├─ getPayments()    0.8ms              ├─► PaymentSvc 25ms
    ├─ getRecs()        2ms                ├─► RecSvc     40ms
    └─ renderPage()     1ms                ├─► PriceSvc   20ms
  total: ~5ms                              ├─► InvSvc     25ms
                                           └─► ... (15 calls total)
                                         total: 200-250ms (and one bad
                                                service fails the whole page)

  Fix: response aggregation, BFFs, caching, denormalize, or merge services.
```

Uptime also compounds badly: if each of 10 services has 99.9% uptime, a request hitting all 10 sequentially sees 99.0% — roughly **9× more downtime** than a monolith with the same per-service uptime.

---

## 4. Service Discovery

Once you have N services with dynamic IPs in Kubernetes or an auto-scaling group, **how does Service A find a healthy instance of Service B?**

```
 CLIENT-SIDE DISCOVERY                    SERVER-SIDE DISCOVERY
 ──────────────────────                   ─────────────────────
  
   Service A                                Service A
      │                                        │
      │ 1. "Where is B?"                       │ 1. request to b.cluster.local
      ▼                                        ▼
  ┌──────────┐                             ┌──────────┐      ┌──────────┐
  │ Service  │                             │   Load   │ ───► │ Service  │
  │ Registry │                             │ Balancer │      │ Registry │
  │ (Consul, │                             │  / k8s   │      └──────────┘
  │  Eureka) │                             │   svc    │
  └────┬─────┘                             └────┬─────┘
       │ list of healthy B's                    │ picks a healthy B
       ▼                                        ▼
  A picks one, calls directly              ┌───┼───┐
                                           ▼   ▼   ▼
                                         Service B instances
 
 Pros: no extra hop                      Pros: clients are simple; lang-agnostic
 Cons: client needs smart LB             Cons: extra hop; LB is another component
       and has to handle registry
 
 Examples: Netflix Eureka + Ribbon       Examples: AWS ALB/NLB, Kubernetes
           ZooKeeper                               Service + kube-proxy, Envoy
```

### 4.1 How Kubernetes actually does it

Kubernetes bakes server-side discovery into the platform and is now the dominant approach:

```
  1. Each Deployment creates Pods with labels:  app=orders
  2. A Service object selects pods by label and gets a stable DNS name:
       orders.prod.svc.cluster.local  →  ClusterIP 10.96.17.42
  3. kube-proxy programs iptables/IPVS on every node.
  4. Caller just does:  http://orders.prod.svc.cluster.local/api/...
     → traffic is load-balanced to a healthy pod transparently.
```

No service registry client code. No Eureka. This is why "Kubernetes-native microservices" is the default answer in modern interviews.

### 4.2 Health checks power discovery

A registry or k8s Service is only as good as its health checks.

- **Liveness probe** — "is the process alive?" If it fails, kill and restart the pod.
- **Readiness probe** — "is the process ready to serve traffic?" If it fails, remove from LB but don't kill.
- **Startup probe** — "has it finished initializing?" Used for slow-start apps (JVM warmup, caches).

Separating liveness and readiness is essential: a pod might be alive but still warming caches, and we must not send traffic to it yet.

---

## 5. API Gateway and Backend-for-Frontend (BFF)

### 5.1 The problem API gateways solve

Without a gateway, every client talks to every backend service. Clients have to know the full topology, and cross-cutting concerns (auth, rate limiting, TLS) are re-implemented everywhere.

```
  WITHOUT GATEWAY                      WITH GATEWAY
  ────────────────                     ─────────────
  
  Mobile ───► UserSvc                  Mobile ─┐
   │      └─► OrderSvc                         │
   │      └─► PaySvc                  Web ─────┼──► ┌─────────────┐
   │      └─► RecSvc                           │    │ API GATEWAY │
   │                                 IoT ──────┘    │ - auth       │
  Web ────► UserSvc                                  │ - rate limit │──► N backend
   │    └─► OrderSvc                                 │ - routing    │    services
   │    └─► ... (same services                       │ - TLS        │
   │          re-wired N times)                      │ - aggregation│
                                                     └─────────────┘
   Every client knows the topology.                  Clients know ONE endpoint.
   Cross-cutting concerns duplicated.                Cross-cutting concerns central.
```

**Gateway responsibilities:**

- Routing (path-/header-/method-based)
- AuthN / AuthZ (JWT validation, OAuth2 flow)
- Rate limiting and quota enforcement
- Request/response transformation (REST ↔ gRPC, field shaping)
- Response aggregation (fan-out, then merge)
- TLS termination
- Observability (access logs, metrics, tracing)
- WAF / bot protection

**Popular choices:** AWS API Gateway, Kong, Envoy + custom control plane, Apigee, Tyk, Traefik, NGINX, Zuplo. Kong alone handled over **1.5 trillion API calls per year** as of 2023.

### 5.2 Backend-for-Frontend (BFF)

Netflix pioneered BFF around 2012 when it had to support 800+ device types — TVs, phones, game consoles — each with wildly different screen sizes, network profiles, and data needs.

```
                       ┌────────────┐
         ┌────────────►│  Web BFF   │────┐
         │             │  (aggregate │    │
   Web  ─┘             │   for web)  │    │      ┌─────────────┐
                       └────────────┘    │      │ UserSvc      │
                       ┌────────────┐    ├─────►│ OrderSvc     │
         ┌────────────►│ Mobile BFF │────┤      │ CatalogSvc   │
   iOS ──┘             │ (small    │     │      │ PriceSvc     │
   Andr─┘              │  payloads) │    │      │ InventorySvc │
                       └────────────┘    │      │ ReviewSvc    │
                       ┌────────────┐    │      └─────────────┘
         ┌────────────►│ Partner BFF│────┘
   3rd ──┘             │ (public API│
   party               │   shape)   │
                       └────────────┘

  - Each BFF is owned by the team that owns that client.
  - BFFs do aggregation, shape responses for that client, and absorb
    latency (parallel fan-out → single response).
  - Backend services stay generic.
```

### 5.3 Gateway pitfalls

- **God-gateway** — business logic leaks into the gateway and it becomes the new monolith.
- **Tight coupling** — the gateway knows every service's schema; every change coordinates here.
- **SPOF** — always run gateways behind an LB with N+2 instances across AZs.
- **Observability blind spot** — must emit metrics/traces at the gateway, not just backends.

---

## 6. Resilience Patterns — the "Stability Trinity"

Distributed systems fail. **It is not a matter of if, it is when.** These patterns minimize blast radius.

### 6.1 Circuit Breaker

Inspired by electrical circuit breakers. Stop hammering a failing dependency so both sides can recover.

```
                 ┌─────────────────┐
     Service A──►│ Circuit Breaker │──► Service B
                 └─────────────────┘

  STATE MACHINE:

      [CLOSED]                [OPEN]                   [HALF-OPEN]
       normal              calls short-circuit     allow one "probe" call
     all requests          immediately (return     
      pass through         error/fallback)
        │                       │                        │
        │  5 failures in        │  timeout elapses       │  probe succeeds
        │  10 seconds           │  (e.g., 30s)           │  →  CLOSED
        ▼                       ▼                        │
     [OPEN]                [HALF-OPEN]                   │  probe fails
                                                         │  →  OPEN
                                                         ▼
                                                  decision made
```

**Implementation:** Hystrix (Netflix, now maintenance-only), Resilience4j (JVM), Polly (.NET), or let a service mesh handle it transparently.

### 6.2 Retry with Exponential Backoff and Jitter

```
  Naive retry:           Exponential backoff:     Exponential + jitter:
  ─────────────          ─────────────────────    ──────────────────────
  attempt 1 (fail)       attempt 1 (fail)         attempt 1 (fail)
  attempt 2 (fail)       wait 100ms               wait 100ms ± 50ms
  attempt 3 (fail)       attempt 2 (fail)         attempt 2 (fail)
   → thundering herd!    wait 200ms               wait 200ms ± 100ms
                         attempt 3 (fail)         attempt 3 (fail)
                         wait 400ms               wait 400ms ± 200ms
                          ↑ still synchronized     ↑ spread out over time
                            across many clients
                            after a big outage
```

**Rules:**
- Always add **jitter** — without it, retries synchronize after a restart and DDoS your own service (this is AWS's "thundering herd" lesson).
- Retry budget per request (e.g., max 3 attempts, max 2s total).
- **Only retry idempotent operations** or those with idempotency keys.
- Retries must stop at some layer — don't have 4 layers each retrying 3 times (that's 81 real attempts for one user click).

### 6.3 Timeout — the unloved cousin

**Every remote call must have a timeout.** The single most common production outage is a blocking call with no timeout leading to thread-pool exhaustion.

| Layer | Typical timeout |
|---|---|
| TCP connect | 500ms – 2s |
| HTTP request (internal RPC) | 1 – 5s |
| DB query | 1 – 10s (set via statement timeout!) |
| External third-party API | 5 – 30s |
| Long batch job | explicit, visible (and cancelable) |

Timeouts should be **tighter than the caller's timeout**. If user-facing request has a 3s budget, internal call should be ≤ 1s, DB call ≤ 500ms. Otherwise a slow leaf blows up the whole tree.

### 6.4 Bulkhead Pattern

Named after ship compartments: a hole in one section doesn't sink the ship.

```
  WITHOUT BULKHEAD                      WITH BULKHEAD
  ────────────────                      ──────────────
  
  One shared thread pool of 200         Separate pools per dependency
                                         
  ┌──────────────────────┐              ┌───────────────────┐
  │ 200 threads          │              │ Pool A → Svc A    │ 40 threads
  │                      │              ├───────────────────┤
  │ Svc A slow? All 200  │              │ Pool B → Svc B    │ 40 threads
  │ threads block there. │              ├───────────────────┤
  │ Now your app has     │              │ Pool C → Svc C    │ 40 threads
  │ zero threads for any │              ├───────────────────┤
  │ other request.       │              │ Pool D → Svc D    │ 40 threads
  └──────────────────────┘              └───────────────────┘
  
  → total outage                        Svc A slow → only Pool A exhausted.
                                        B, C, D keep serving.
```

Same idea applies to DB connection pools, HTTP client pools, async Semaphores, Kubernetes ResourceQuotas.

### 6.5 Rate Limiting and Load Shedding

Protect the service when upstream goes haywire. If QPS exceeds a budget, **reject fast** rather than queue forever (queueing just delays the failure and makes it worse).

Token bucket is the standard algorithm (see Stream Processing note). Important: rate-limit per **user / tenant / API key**, not only globally — one noisy tenant must not starve the rest.

### 6.6 Fallbacks and Graceful Degradation

When a non-critical dependency is down, serve a **degraded but useful** response.

```
  Product detail page:
  ┌──────────────────────────────────────────────┐
  │   product name & price       (critical)      │ ─► hard-fail if down
  │   stock status               (critical)      │ ─► hard-fail if down
  │   "customers also bought"    (nice-to-have)  │ ─► empty list, log+metric
  │   live chat widget           (nice-to-have)  │ ─► hide it, keep page up
  │   personalized banner        (nice-to-have)  │ ─► show generic banner
  └──────────────────────────────────────────────┘
```

Netflix's famous rule: *"A movie page must render even if the recommendations service is down."*

---

## 7. Data Management

The hardest part of microservices is data.

### 7.1 Database per Service (non-negotiable)

```
  EACH SERVICE OWNS ITS DATA:
  
   ┌───────┐     ┌────────┐     ┌─────────┐
   │UserSvc│     │OrderSvc│     │PaySvc   │
   └───┬───┘     └────┬───┘     └────┬────┘
       │              │              │
   ┌───┴────┐     ┌───┴────┐     ┌───┴────┐
   │Postgres│     │DynamoDB│     │ MySQL  │
   └────────┘     └────────┘     └────────┘
    
   - No other service queries another's DB directly.
   - Cross-service data is exchanged over APIs or events, never SQL.
   - Each service can pick the best storage for its workload.
```

**Why this matters:**
- Schema changes are a local concern — no cross-team migration plan.
- Each team picks the right DB (relational, document, time-series, graph).
- One DB going down doesn't take out the whole suite.
- Zero hidden coupling through "just one join."

### 7.2 The cross-service-query problem

> "But I need user info on the orders page."

This is the moment every microservices design gets tested. Four common answers:

```
 1. API composition                     Simple, but adds latency and is N+1
    ─────────────────                   friendly (query 100 orders → 100 user
    OrderSvc fetches orders,            lookups).
    calls UserSvc per order,            Good for: small result sets, non-hot path.
    stitches in response.
    
 2. Client-side aggregation / BFF       Moves the join into the BFF; backend
    ─────────────────────────────       services remain clean.
    BFF asks OrderSvc + UserSvc         Good for: UI assembly.
    in parallel, merges.
    
 3. Event-driven replication            Pay latency only on writes. Reads are
    ────────────────────────────        local & fast. This is the big one.
    UserSvc publishes UserUpdated;
    OrderSvc subscribes and keeps
    a read-only cache of name/email.
    
 4. CQRS with materialized views        Eventually-consistent denormalized
    ─────────────────────────────       projections purpose-built per query.
    Stream events into a "order        Good for: complex read models,
    history" read model with joined    dashboards, search.
    user+order+payment fields.
```

> Rule of thumb: **reads should usually be local, not a distributed join.**

### 7.3 Event-Driven Architecture

Events are the glue. A good event is **a fact about the past** with a stable schema.

```
  ┌──────────┐  OrderPlaced v1             ┌──────────────┐
  │OrderSvc  │───────────────────►┌─────┐──►│PaymentSvc    │  charge card
  │          │                    │Kafka│──►│InventorySvc  │  reserve stock
  │          │                    └─────┘──►│EmailSvc      │  send receipt
  │          │                             └──►│AnalyticsSvc│  update BI
  └──────────┘                                 └────────────┘

  OrderPlaced:
  {
    "eventId": "uuid",            ← for dedup (idempotent consumers)
    "eventType": "OrderPlaced",
    "version": 1,
    "occurredAt": "2026-04-22T12:00:00Z",
    "aggregateId": "order-789",
    "data": { "userId": ..., "items": [...], "total": 59.99 }
  }
```

### 7.4 The Dual-Write Problem → Transactional Outbox

**The bug:** a service updates its DB and publishes an event. Two writes, no distributed transaction. Any failure between them leaves the system inconsistent.

```
   NAIVE (broken):                           OUTBOX (correct):
   ───────────────                           ─────────────────
   
   BEGIN txn                                 BEGIN txn
     UPDATE orders SET status='PAID'           UPDATE orders SET status='PAID'
   COMMIT                                      INSERT INTO outbox(event)...
                                             COMMIT
   publish("OrderPaid") ← can fail!            ▲
                                               │ atomic — same DB txn
                                               │
                                    ┌──────────┴──────────┐
                                    │  Outbox Publisher   │
                                    │  (CDC / polling)    │
                                    └──────────┬──────────┘
                                               │ reads outbox, publishes
                                               ▼
                                            ┌─────┐
                                            │Kafka│
                                            └─────┘
   Failure modes:                      Failure modes:
   - DB committed, publish failed      - publisher crashes → on restart,
     → silent inconsistency              unpublished rows are still there
   - publish succeeded, DB rolled       - duplicates possible → consumers
     back → "ghost" events               must be idempotent (eventId dedup)
```

Change Data Capture tools like **Debezium** read the DB log and turn `outbox` inserts into Kafka messages, giving you at-least-once delivery with correct ordering per aggregate.

### 7.5 Event Sourcing (optional heavy hammer)

Instead of storing the current state, **store the sequence of events that produced it**. Current state = fold(events).

```
   Traditional:                            Event-sourced:
   ─────────────                           ───────────────
   accounts                                 account_events
   ┌─────────┬──────────┐                   ┌─────────┬──────────────────┐
   │account  │balance   │                   │event_id │payload           │
   ├─────────┼──────────┤                   ├─────────┼──────────────────┤
   │a-1      │250.00    │                   │1        │Opened(a-1, 0)    │
   └─────────┴──────────┘                   │2        │Deposited(100)    │
                                            │3        │Deposited(200)    │
   Lost history.                            │4        │Withdrew(50)      │
                                            └─────────┴──────────────────┘
                                            current_balance = replay → 250
                                            Full audit log. Time travel.
                                            Replay to fix bugs in projections.
```

**When to use:** finance, audit-critical workflows, systems where "why is the state what it is?" is asked often. **Cost:** steeper learning curve, schema evolution is harder (events are immutable).

### 7.6 CQRS — Command-Query Responsibility Segregation

Separate the model for writes (commands) from the model for reads (queries).

```
                  ┌──────────────────────┐
   writes ──────► │   Command side       │
                  │   (write model,      │
                  │    normalized,       │
                  │    transactional)    │
                  └──────────┬───────────┘
                             │ emits events
                             ▼
                  ┌─────────────────────┐
                  │   Event stream      │
                  └──────────┬──────────┘
                             │
             ┌───────────────┼────────────────┐
             ▼               ▼                ▼
      ┌──────────┐   ┌──────────────┐   ┌──────────┐
      │Read view │   │Read view     │   │Elastic   │   ← materialized,
      │"order    │   │"customer     │   │search    │     query-shaped
      │ history" │   │ dashboard"   │   │index     │     projections
      └──────────┘   └──────────────┘   └──────────┘
             ▲
   reads ────┘
```

Benefits: reads and writes scale independently, read models can be denormalized for query shape, easy to add new views. Cost: eventual consistency between read and write sides.

---

## 8. Distributed Transactions — The Saga Pattern

Classic 2-phase commit (2PC) does not work across microservices (blocking, coordinator SPOF, doesn't scale, most modern DBs don't support it). Instead we use **sagas**: a distributed transaction = sequence of **local transactions**, each publishing an event; on failure, **compensating transactions** undo previous steps.

### 8.1 Choreography-based Saga (event-driven)

No central brain. Each service listens for events and reacts.

```
  Happy path:
  OrderSvc ──OrderCreated──► PaymentSvc ──PaymentSucceeded──► InventorySvc
                                                                   │
                                                          ItemsReserved
                                                                   ▼
                                                            ShippingSvc ──► Shipped
  
  Failure path (payment fails):
  OrderSvc ──OrderCreated──► PaymentSvc ──PaymentFailed──► OrderSvc
                                                           │
                                                           ▼
                                                   CANCEL order (compensation)
  
  No orchestrator. Services only know about events they emit/consume.
```

**Pros:** fully decoupled, simple services, scales horizontally.
**Cons:** workflow is implicit (spread across consumers), hard to see the full picture without tracing, hard to add a global "timeout the whole saga" rule.

### 8.2 Orchestration-based Saga

A dedicated **orchestrator** (state machine) issues commands to participants.

```
           ┌────────────────────────┐
           │   Saga Orchestrator    │
           │   (state machine)      │
           └───┬────────┬────────┬──┘
     step 1: charge│  step 2: reserve│  step 3: ship│
               ▼              ▼                ▼
            Payment       Inventory         Shipping
               │              │                │
               ▼              ▼                ▼
            [OK]           [FAIL] ─► orchestrator fires:
                                     - refundPayment    (compensation)
                                     - cancelOrder      (compensation)

  Tools: Netflix Conductor, Uber Cadence/Temporal, AWS Step Functions,
         Camunda, Zeebe.
```

**Pros:** explicit, visible workflow; easier to monitor, retry, timeout; natural home for branching logic.
**Cons:** orchestrator can become a god service; potential bottleneck; harder for teams to evolve their service without touching the orchestrator.

### 8.3 Which one when?

| Prefer choreography | Prefer orchestration |
|---|---|
| Few steps (≤ 3), simple linear flow | Many steps (≥ 4), conditional branches |
| High-scale side effects (send email, ping analytics) | Business-critical workflow (order fulfillment, money movement) |
| Teams already aligned on event schemas | Need explicit timeouts and SLAs on the overall workflow |
| "Fire and forget" semantics OK | Must answer "where is order #123 right now?" immediately |

### 8.4 Five things any saga must get right

1. **Idempotent consumers.** Every event may be redelivered; use `eventId` / `idempotencyKey` tables.
2. **Atomicity at step boundary.** Use the outbox pattern; don't dual-write.
3. **Compensations are not exact undos.** Refund ≠ un-charge. Cancel ≠ uncreate. Compensations act on the **current** state.
4. **Saga timeout.** Some participant will hang; the saga needs a global deadline.
5. **Correlation ID everywhere.** Propagate `sagaId` / `traceId` in every message and log — otherwise debugging is a nightmare.

---

## 9. Service Mesh

A service mesh moves cross-cutting networking concerns — mTLS, retries, timeouts, circuit breaking, observability, traffic shifting — **out of every service's code and into a sidecar proxy**.

```
  WITHOUT MESH                          WITH SERVICE MESH
  ─────────────                         ─────────────────
  
  each service reimplements:            sidecar (Envoy/linkerd2-proxy) does it:
  - retries                              
  - timeouts                            ┌────────────────────┐
  - mTLS                                │     Service A       │
  - metrics                             │ ┌────────┐ ┌──────┐│
  - tracing                             │ │ app    │ │sidecar││
  - circuit breaker                     │ │ code   ├►│ proxy ││──► cluster
                                        │ └────────┘ └──────┘│
  Different quality per service.         └────────────────────┘
  Different languages do it differently. 
                                          All traffic is intercepted by the
                                          sidecar — app code talks plain
                                          HTTP to localhost.
```

### 9.1 Data plane vs Control plane

```
  CONTROL PLANE (istiod / linkerd control)
  ┌───────────────────────────────────────────┐
  │  - service discovery                      │
  │  - cert issuance & rotation               │
  │  - policy config (who can call whom)      │
  │  - pushes config to sidecars (xDS API)    │
  └───────────────────────────────────────────┘
                 │  (config, certs)
                 ▼
  DATA PLANE (one sidecar per pod)
   ┌─────────┐    ┌─────────┐    ┌─────────┐
   │app │envy│    │app │envy│    │app │envy│
   └────┴────┘    └────┴────┘    └────┴────┘
   All application traffic flows through sidecars (via iptables).
```

### 9.2 Istio vs Linkerd (the two big open-source meshes)

| | **Istio** | **Linkerd** |
|---|---|---|
| Proxy | Envoy (C++) | linkerd2-proxy (Rust) |
| Proxy memory per sidecar | ~50 MB+ | ~20–30 MB |
| Control plane memory | 1–2 GB | 200–300 MB |
| P99 latency added at 2k RPS | +5.8 ms (sidecar) / +2.4 ms (ambient) | +2.0 ms |
| L7 feature surface | very rich (50+ CRDs) | focused, smaller (~10 CRDs) |
| Sidecarless mode | **Ambient Mesh** (ztunnel + waypoint) | not supported |
| Learning curve | steep | gentle |
| Best for | large orgs, complex traffic rules, regulatory | small–mid, fast adoption |

### 9.3 When do you actually need a mesh?

- Running 15+ services with complex east-west traffic
- Zero-trust: mTLS everywhere is a compliance requirement
- Progressive delivery (canary, mirroring) across many services
- You already run everything on Kubernetes

If you have < 10 services, **do the resilience patterns in-code** (Resilience4j, Polly). A mesh adds real operational complexity.

> **Ambient Mesh** (Istio's sidecar-less mode introduced in 2023 and stabilizing since) is a hot topic in 2025–2026: ztunnel per node handles L4+mTLS, optional waypoint proxies handle L7. Lower memory, fewer restart-storms, but multi-tenant isolation trade-offs.

---

## 10. Observability: The Three Pillars (plus one)

Distributed systems are fundamentally harder to debug: a user request touches 10 services, each one on a different pod, each one possibly retried. **Logs, metrics, and traces together** give you the story.

```
┌────────────────────────────────────────────────────────────────────┐
│                   THE THREE PILLARS (+ EVENTS)                     │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  LOGS                METRICS                TRACES      EVENTS     │
│  ────                ───────                ──────      ──────     │
│  what happened?      how is it performing?  where did   what        │
│  high cardinality    numbers over time,     time go?    changed in  │
│  free-form           low cardinality        end-to-end  the system? │
│  per-event detail    aggregations           per-request deploys,    │
│                                                         configs,    │
│                                                         feature    │
│                                                         flips      │
│                                                                    │
│  ELK, Splunk,        Prometheus +           Jaeger,     incident    │
│  Loki, Datadog       Grafana, Datadog,      Tempo,      timelines,  │
│  CloudWatch          CloudWatch             X-Ray,      Datadog     │
│                                             Honeycomb   Events      │
└────────────────────────────────────────────────────────────────────┘
```

### 10.1 Structured logging

Plain-text logs don't scale. Log JSON with stable fields:

```json
{
  "ts": "2026-04-22T12:00:01.234Z",
  "level": "INFO",
  "service": "order-service",
  "env": "prod",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": "u-42",
  "orderId": "o-789",
  "msg": "order placed",
  "amount": 59.99
}
```

Now you can search "all ERROR logs where userId=u-42 between 12:00 and 12:05 across every service."

### 10.2 The metrics that matter (RED + USE)

| Framework | Metrics | For |
|---|---|---|
| **RED** | **R**ate, **E**rrors, **D**uration | request-driven services |
| **USE** | **U**tilization, **S**aturation, **E**rrors | resources (CPU, disk, queue) |
| **Four Golden Signals (Google SRE)** | latency, traffic, errors, saturation | everything |

Always track **percentiles, not averages**: p50, p95, p99, p99.9. Averages hide the tail, and tail latency is what users feel.

### 10.3 Distributed Tracing

```
  User request: GET /checkout           traceId = abc-xyz
  ────────────────────────────────────────────────────────────────
   ms:  0   50    100   150   200   250   300   350   400   450
        │                                                      │
  api-gw ██░░                                                  │
   └─► order-svc  ░░████████░░░░░░                            │
         ├─► user-svc      ░░████                              │
         ├─► price-svc         ░░██████                        │
         ├─► inventory-svc        ░░░░█████                    │
         └─► payment-svc                ░░░░████████████████   │
              └─► bank-api                     ░░░░░░█████████
  
  Total: 450ms     p95 page load budget was 300ms.
                   → bank-api at 200ms is the bottleneck.
                   → tracing shows exactly where time went.
```

**Key concepts:**
- **Trace** — one end-to-end request.
- **Span** — one operation inside a trace (HTTP call, DB query, function).
- **Parent/child** — spans form a tree; context is propagated via W3C `traceparent` header.
- **OpenTelemetry** — the industry-standard, vendor-neutral SDK + wire format. Use this; don't lock in to a proprietary one.

### 10.4 Sampling

Tracing 100% of requests in production is expensive. Common strategies:

- **Head-based sampling** — decide at entry (keep 1%). Simple, cheap, but you miss rare bugs.
- **Tail-based sampling** — buffer a trace, then decide (keep 100% of errors, 1% of success). More useful, needs a collector (e.g., OTel Collector with tail-sampler).

---

## 11. Deployment Patterns

### 11.1 Blue / Green

```
  Router ──► BLUE (v1) 100%          Router ──► BLUE (v1) 0%
             GREEN (v2) 0%                       GREEN (v2) 100%
  
  Deploy v2 to green, test it, flip router. Instant rollback = flip back.
  Cost: 2x infra during switch. Good for stateless services.
```

### 11.2 Canary

```
  Router ──► v1  99%                 Router ──► v1  90%  → 50% → 0%
             v2   1%                           v2  10%  → 50% →100%
  
  Shift traffic gradually while watching metrics. Roll back automatically
  if error rate or p99 latency breaches a SLO.
  Tooling: Argo Rollouts, Flagger, Spinnaker.
```

### 11.3 Feature Flags

Decouple **deploy** from **release**. Ship code dark, then turn it on for 1% of users. Your deploy and your feature launch are independent events.

Tools: LaunchDarkly, Unleash, OpenFeature, Split.io.

### 11.4 Shadow (Mirroring)

Copy real production traffic to the new version without serving its responses. Compare outputs offline. Useful for refactors with tight correctness requirements (search ranking, pricing).

---

## 12. Common Anti-Patterns

The surest way to ace a microservices question is to demonstrate you know how it *goes wrong*.

### 12.1 Distributed Monolith

Services that are technically separate but **must be deployed together**. The worst of both worlds.

**Symptoms:**
- One feature = PR across 5 repos
- "Please deploy in this order: A, then B, then C"
- Changing a response shape in A requires B, C, D to redeploy
- Shared libraries with breaking changes that propagate on upgrade

**Fixes:** consumer-driven contracts, backward-compatible APIs (add fields, never remove), event-schema evolution discipline, API versioning, code ownership by bounded context.

### 12.2 Shared Database

Two services writing to the same tables. Now a "simple" column rename is a multi-team migration.

**Fix:** give each service its own database. If you need cross-service data, use events or API composition.

### 12.3 Chatty Services / Chattyness

One page load = 30 synchronous internal calls. Latency multiplies; uptime decays; the blast radius of any failing service explodes.

**Fix:** merge services that always call each other, denormalize via events, use BFFs to aggregate.

### 12.4 Nano-services

A service per REST endpoint. All the network, deployment, and coordination cost of microservices for none of the decoupling benefit.

**Fix:** Right-size. A service should own a bounded context, not a row in a database.

### 12.5 Sync-everywhere

Every interaction is a blocking REST call, so a single slow dependency cascades into a whole-site outage.

**Fix:** Async via Kafka/RabbitMQ for anything that can tolerate eventual consistency (email, indexing, analytics, data replication).

### 12.6 Magic Shared Library

"Just use our internal `common-utils` library." Now every service is coupled to its version; upgrading common breaks 40 services at once.

**Fix:** keep shared libraries **tiny** and stable (interfaces, DTOs, tracing helpers). Don't put business logic in them.

### 12.7 No Observability

You can't debug what you can't see. In production, distributed systems are opaque without logs + metrics + traces.

### 12.8 Conway's-Law Inversion

You split services along technical lines (UI / API / DB teams) instead of business domains. Every feature crosses every team.

**Fix:** align teams with bounded contexts. Each service has exactly one owning team.

---

## 13. Real-World Architectures

### 13.1 Netflix — resilience at scale

- **1000+ microservices**, multi-region, active-active across 3 AWS regions
- Open-sourced **Hystrix** (circuit breaker) and **Eureka** (discovery). Now largely on sidecars + Spring Cloud Gateway.
- **Chaos Monkey / Chaos Kong** — kill instances (and whole regions!) in production on purpose. Forces every team to design for failure.
- **Zuul** gateway → **BFF per device class** (TV, mobile, web) — pattern they invented to support 800+ device types.
- Stream-first data architecture: **Kafka → Flink → Iceberg/Hive**; real-time recommendations and stream analytics.
- **Conductor** workflow engine for orchestrated sagas (content onboarding, billing workflows).

### 13.2 Uber — massive scale and the anti-microservice reckoning

- Peaked at **4000+ microservices** with 2200+ engineers.
- Moved on to a hybrid model: consolidated core domains into **macroservices** (aka "service-oriented")  after suffering from service sprawl.
- Built **Cadence** (now **Temporal**) for durable workflow orchestration.
- **Jaeger** distributed tracing — open-sourced, now CNCF.
- Geographic scale: city-level sharding ("dispatch service per city"), with a global pricing/matching coordinator.
- **IngestionNext**: one streaming write path via Flink → **Hudi** tables on S3, turning data-lake freshness from hours into minutes.

### 13.3 Amazon — the two-pizza team

- The origin of modern microservices (Bezos's "all data must be exposed via service interfaces" mandate, 2002).
- **Two-pizza teams**: if you can't feed the team with two pizzas, it's too big.
- "**You build it, you run it**" — no separate ops team; the service team owns deployment, monitoring, on-call.
- Heavy use of **SQS, SNS, Kinesis, Step Functions** for async and orchestration.

### 13.4 Spotify — squad model and golden paths

- **Squad / tribe / chapter / guild** organizational model.
- Each squad owns services end-to-end.
- "**Golden paths**" — recommended (but not mandated) tech stack with templates and self-service tooling.
- Heavy Kafka pipeline for analytics; GCP + Kubernetes for most services.

### 13.5 What the case studies agree on

1. Pick boundaries around **bounded contexts**, not tech layers.
2. **Event-driven** for anything that can tolerate eventual consistency.
3. **Service mesh or equivalent sidecar** once you pass ~20 services.
4. **Unified observability** — one tracing system, one log aggregator, one metric store.
5. Invest in **platform/"golden path" tooling** early so new services are cheap to spin up *correctly*.
6. **Design for failure explicitly** — Chaos Engineering, game days, SLO-driven alerting.

---

## 14. Migration: Monolith → Microservices (Strangler Fig)

Big-bang rewrites fail. The canonical migration strategy is the **strangler fig**, named after the tree that grows around a host tree and slowly replaces it.

```
  PHASE 0 — STARTING POINT
  ┌───────────────────┐
  │                   │
  │    MONOLITH       │
  │   (all features)  │
  │                   │
  └───────────────────┘

  PHASE 1 — INSERT A ROUTING LAYER (no behavior change)
                   ┌──────────────────┐
  client ─────────►│   Proxy / Gateway│─────► MONOLITH  (100%)
                   └──────────────────┘

  PHASE 2 — EXTRACT ONE BOUNDED CONTEXT
                   ┌──────────────────┐      ┌─────────────┐
  client ─────────►│   Gateway        │─┬───►│ NEW Svc     │  (billing only)
                   └──────────────────┘ │    └─────────────┘
                                        └───► MONOLITH       (everything else)
  Billing traffic → new service. Monolith's billing module is deprecated
  but still there as a fallback.

  PHASE 3 — KEEP PEELING LAYERS
  Repeat for user-profile, catalog, search, checkout… one at a time.

  PHASE N — MONOLITH IS FULLY STRANGLED
    Either remove it, or leave a skeleton hosting truly cross-cutting code.
```

### 14.1 How to pick the first service to extract

Look for an extraction that is **high-value and low-risk**:

- Clear business boundary (easy to draw)
- Independent database tables (few joins to the rest of the monolith)
- High change cadence (migration accelerates future work)
- Low coupling to other features (minimal cross-service chat)
- Independent scaling profile (so microservices benefits show up fast)

Common first candidates: **authentication**, **notifications/email**, **search**, **media/asset service**, **billing**.

### 14.2 Migration gotchas

- **Premature extraction** — you split a service too early, then find the real boundary is somewhere else. Worse than staying monolith.
- **Dual writes** — during transition, data lives in both old and new DBs. Use CDC / outbox or you'll corrupt state.
- **Feature freeze envy** — business won't freeze features during a multi-quarter migration. Plan for extraction while the monolith continues evolving.
- **Integration tax** — expect to build shims, contract tests, and extra infrastructure just for the transition period.

---

## 15. Interview Cheat Sheet

Memorize these; they cover 80% of the microservices questions asked in HLD rounds.

### Patterns tree

```
 Service boundaries     ─ Bounded contexts (DDD)
                        ─ Conway's Law alignment
                        ─ Right-sizing (rewritable in a sprint)

 Communication          ─ Sync:  REST (external), gRPC (internal)
                        ─ Async: Kafka, RabbitMQ, SQS/SNS
                        ─ Aggregation: API Gateway, BFF

 Discovery & routing    ─ Server-side: Kubernetes Service, ALB
                        ─ Client-side: Eureka, Consul
                        ─ Service mesh: Istio, Linkerd

 Resilience             ─ Timeout
                        ─ Retry with backoff + jitter
                        ─ Circuit breaker
                        ─ Bulkhead
                        ─ Rate limiter / load shedder
                        ─ Graceful degradation / fallbacks

 Data                   ─ DB per service
                        ─ Outbox + CDC for reliable events
                        ─ Saga (orchestration vs choreography)
                        ─ CQRS + materialized views
                        ─ Event sourcing (when audit-critical)

 Observability          ─ Structured logs
                        ─ RED/USE metrics, percentiles
                        ─ Distributed tracing (OpenTelemetry)
                        ─ Correlation IDs end-to-end

 Deployment             ─ Blue/green, canary, shadow
                        ─ Feature flags (decouple deploy from release)
                        ─ GitOps (Argo CD, Flux)

 Anti-patterns          ─ Distributed monolith
                        ─ Shared DB
                        ─ Chatty services
                        ─ Nano-services
                        ─ Sync-everywhere
                        ─ Magic shared library
```

### Key takeaways

1. **Start with a modular monolith.** Evolve to microservices when team size, deploy pain, or scale profiles demand it — not before.
2. **Bounded contexts over nano-services.** Service boundaries are a one-shot decision; spend time on them.
3. **Database per service is non-negotiable.** Share data with events, not joins.
4. **Resilience trinity: timeout + retry-with-jitter + circuit breaker.** Add bulkheads and fallbacks.
5. **Saga over 2PC.** Outbox pattern is how you make sagas reliable.
6. **Observability is table stakes.** Logs, metrics, traces — via OpenTelemetry, end-to-end correlation.
7. **Use Kubernetes + service mesh** (Istio/Linkerd) once you cross 15–20 services.
8. **API Gateway for ingress; BFF per client class.**
9. **Strangler fig** to migrate out of a monolith. Never big-bang.
10. **Design for failure.** Chaos engineer your production before your users do.

> **When an interviewer asks "would you use microservices here?"** — the winning answer is: *"Probably not on day one. I'd start with a modular monolith aligned to bounded contexts, extract services as team size, deploy pain, or scaling needs force the issue, and I'd prioritize getting observability, async event infrastructure, and a gateway in place first."*
