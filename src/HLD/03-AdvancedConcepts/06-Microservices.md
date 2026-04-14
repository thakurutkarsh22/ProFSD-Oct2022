# Microservices Architecture

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Must Know

---

## 1. Monolith vs Microservices

```
MONOLITH:                              MICROSERVICES:
┌──────────────────────┐              ┌─────┐ ┌─────┐ ┌─────┐
│                      │              │User │ │Order│ │Pay- │
│   ALL CODE           │              │Svc  │ │Svc  │ │ment │
│   in ONE             │              │     │ │     │ │Svc  │
│   deployment         │              └──┬──┘ └──┬──┘ └──┬──┘
│                      │                 │       │       │
│  ┌─────┐ ┌────────┐ │              ┌──┴──┐ ┌──┴──┐ ┌──┴──┐
│  │User │ │Orders  │ │              │DB   │ │DB   │ │DB   │
│  │     │ │        │ │              └─────┘ └─────┘ └─────┘
│  │Pay  │ │Notif   │ │              
│  └─────┘ └────────┘ │              Each service:
│         ┌────┐       │              - Own codebase
│         │ DB │       │              - Own database
│         └────┘       │              - Own deployment
└──────────────────────┘              - Own team

WHEN TO USE WHAT:

Start with Monolith when:              Move to Microservices when:
─ Small team (< 10 engineers)          ─ Team is large (> 20 engineers)
─ New product, unclear requirements    ─ Clear domain boundaries
─ Speed of iteration matters           ─ Independent scaling needed
─ Simple deployment needed             ─ Different tech stacks per service
```

---

## 2. Service Communication

```
SYNCHRONOUS:                           ASYNCHRONOUS:
─────────────                          ──────────────
Service A ──REST/gRPC──► Service B     Service A ──► [Message Queue] ──► Service B

A waits for B's response               A doesn't wait (fire & forget)
Simple, immediate response              Decoupled, resilient
If B is slow/down → A is affected      If B is down → messages wait

Use: User-facing requests,              Use: Email sending, data processing,
     real-time lookups                       event notifications


┌──────────────────────────────────────────────────────────────┐
│           COMMUNICATION PATTERNS                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Request-Response (REST/gRPC)                                │
│  A──req──►B──resp──►A                                        │
│  Simple, synchronous                                         │
│                                                              │
│  Event-Driven (Kafka/RabbitMQ)                               │
│  A──event──►[Queue]──►B,C,D                                  │
│  Decoupled, can fan out to multiple consumers                │
│                                                              │
│  API Gateway (BFF Pattern)                                   │
│  Client──►Gateway──►multiple services──►aggregate──►Client   │
│  Single entry point, response aggregation                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Service Discovery

How does Service A find Service B?

```
CLIENT-SIDE DISCOVERY:               SERVER-SIDE DISCOVERY:

  Service A                           Service A
     │                                   │
     │ query                             │ request
     ▼                                   ▼
  ┌──────────┐                       ┌──────────┐
  │ Service  │                       │   Load   │  query  ┌──────────┐
  │ Registry │                       │ Balancer │────────►│ Service  │
  │ (Consul, │                       └────┬─────┘         │ Registry │
  │  Eureka) │                            │               └──────────┘
  └────┬─────┘                       ┌────┼────┐
       │                             ▼    ▼    ▼
  Service A picks one          Service B instances
  and calls directly

Examples:                           Examples:
Netflix Eureka,                     AWS ALB, Kubernetes Service
Consul, ZooKeeper
```

---

## 4. Resilience Patterns

```
CIRCUIT BREAKER:
────────────────
Prevent cascading failures. If a service is failing, stop calling it.

  ┌────────┐     ┌──────────────┐     ┌────────┐
  │Service │────►│Circuit Breaker│────►│Service │
  │   A    │     └──────────────┘     │   B    │
  └────────┘                          └────────┘

  States:
  CLOSED ───► 5 failures ───► OPEN ──► timeout ──► HALF-OPEN
  (normal)    in 10 seconds   (reject   (30 sec)   (allow 1 request)
                              all calls)              │
                                                      ├─ success → CLOSED
                                                      └─ failure → OPEN


RETRY WITH BACKOFF:
──────────────────
  Attempt 1: fail → wait 1s
  Attempt 2: fail → wait 2s
  Attempt 3: fail → wait 4s
  Attempt 4: fail → wait 8s + jitter (random 0-1s)
  Give up after N attempts → fallback/DLQ


BULKHEAD PATTERN:
────────────────
  Isolate failures to prevent one bad service from consuming all resources.

  ┌────────────────────────────────┐
  │  Thread Pool A (for Service X) │  ← max 20 threads
  │  Thread Pool B (for Service Y) │  ← max 20 threads
  │  Thread Pool C (for Service Z) │  ← max 20 threads
  └────────────────────────────────┘
  
  If Service X is slow → Pool A fills up
  But Pool B and C are unaffected!


TIMEOUT:
───────
  Always set timeouts on external calls.
  Connect timeout: 1-5 seconds
  Read timeout: 5-30 seconds
  Never wait forever!
```

---

## 5. Data Management in Microservices

```
DATABASE PER SERVICE (Recommended):
  Each service owns its data. No shared databases!

  ┌─────┐   ┌─────┐   ┌─────┐
  │Svc A│   │Svc B│   │Svc C│
  └──┬──┘   └──┬──┘   └──┬──┘
     │         │         │
  ┌──┴──┐   ┌──┴──┐   ┌──┴──┐
  │DB A │   │DB B │   │DB C │
  └─────┘   └─────┘   └─────┘

  How to handle cross-service data needs:
  1. API calls between services (synchronous)
  2. Event-driven data replication (async)
  3. CQRS with materialized views
  4. Saga for distributed transactions

SHARED DATABASE (Anti-pattern):
  Multiple services access same DB.
  ─ Tight coupling
  ─ Schema changes break everything
  ─ Can't scale independently
  AVOID THIS!
```

---

## 6. Observability (The Three Pillars)

```
┌──────────────────────────────────────────────────────────────┐
│                  THREE PILLARS OF OBSERVABILITY               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. LOGGING                                                  │
│     What happened? Detailed event records.                   │
│     Tools: ELK Stack, Splunk, CloudWatch Logs                │
│     Structured logging (JSON) > plain text                   │
│                                                              │
│  2. METRICS                                                  │
│     How is the system performing? Numbers over time.         │
│     - Latency (p50, p95, p99)                                │
│     - Error rate                                             │
│     - QPS / throughput                                       │
│     - CPU, memory, disk                                      │
│     Tools: Prometheus + Grafana, Datadog, CloudWatch         │
│                                                              │
│  3. DISTRIBUTED TRACING                                      │
│     How does a request flow across services?                 │
│     Each request gets a trace ID propagated across services. │
│     Tools: Jaeger, Zipkin, AWS X-Ray, OpenTelemetry          │
│                                                              │
│  Request: GET /order/123                                     │
│  Trace ID: abc-xyz                                           │
│  ┌──────────┐ 10ms  ┌──────────┐ 50ms  ┌──────────┐        │
│  │ API GW   │──────►│Order Svc │──────►│ DB Query │        │
│  └──────────┘       └──────────┘       └──────────┘        │
│                         │ 30ms                                │
│                         ▼                                    │
│                    ┌──────────┐                               │
│                    │User Svc  │                               │
│                    └──────────┘                               │
│  Total: 90ms (can see exactly where time is spent)           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. Key Takeaways for Interviews

1. **Start monolith, evolve to microservices** — don't over-engineer day 1
2. **Database per service** is non-negotiable in true microservices
3. **Circuit breaker + retry + timeout** = resilience trinity
4. **API Gateway** for external clients, **service mesh** for internal communication
5. **Event-driven** for async communication between services (Kafka)
6. **Always mention observability**: logging, metrics, tracing
7. **Mention Kubernetes** for container orchestration and service discovery
