# Splitting a Monolith Safely

> **TL;DR.** You don't split a monolith by "rewriting it as microservices". You split it by **carving out one bounded context at a time**, fronting it with an API in the old codebase, switching traffic gradually behind a feature flag, and only *then* moving the database tables. The patterns are the **Strangler Fig**, the **Anti-Corruption Layer**, the **Branch by Abstraction**, and **Database Decomposition** (shared DB → view → dual-write → owned DB).

---

## 1. Why big-bang rewrites fail

1. **Business doesn't freeze.** New features keep being added to the monolith. Every month you spend rewriting, the rewrite diverges further.
2. **Behaviour is undocumented.** Monolith code encodes years of edge cases; a clean rewrite loses 10% of corner cases that each correspond to an upset customer.
3. **Data is entangled.** Shared tables, foreign keys, and cross-module joins make a clean split impossible in one cutover.
4. **Risk is front-loaded.** Year 1: no value, only cost. Year 2: finally launch, often worse than the old.

The approach that works: **Strangler Fig** — the metaphor of a fig tree growing around a host and eventually replacing it. You incrementally route traffic from old to new, and the old code gradually dies off.

---

## 2. The full playbook

```
┌─────────────────────────────────────────────────────────────────┐
│               STRANGLING THE MONOLITH                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. CARVE a bounded context (e.g. "Notifications")              │
│     - Identify inputs, outputs, data owned, side effects        │
│     - Document the API surface                                  │
│                                                                 │
│  2. EXTRACT the interface first (no new service yet)            │
│     - Hide the module behind a facade inside the monolith       │
│     - Now callers depend on the facade, not internals           │
│                                                                 │
│  3. STAND UP the new service                                    │
│     - New service implements the facade via REST/gRPC           │
│     - Points at monolith's DB tables for now (shared DB phase)  │
│                                                                 │
│  4. ROUTE traffic gradually (feature flag + shadow mode)        │
│     - 1% → 10% → 50% → 100% with rollback in between            │
│     - Shadow: new service processes in parallel, compares       │
│                                                                 │
│  5. MIGRATE the data                                            │
│     - New service stops sharing DB                              │
│     - Owns its own tables; monolith accesses via API            │
│     - Apply expand-contract (see 11-ZeroDowntimeSchemaMigration)│
│                                                                 │
│  6. REMOVE the monolith's implementation                        │
│     - Delete dead code                                          │
│     - Monolith now only calls the new service                   │
│                                                                 │
│  7. REPEAT for the next bounded context                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

Each pass takes weeks to months. Three or four passes a year is a healthy rate.

---

## 3. Identifying bounded contexts — what to extract first

Pick the **first slice** based on:

| Criterion | Why |
|-----------|-----|
| **Loosely coupled to the rest** | Fewest cross-module dependencies → cleanest interface |
| **Clear ownership domain** | "Notifications", "Billing", "Search" — nouns that map to teams |
| **Distinct scaling / SLO** | Something that needs 10× the throughput of the rest justifies its own service |
| **Rapid iteration target** | A module being changed weekly gets the biggest payoff from independent deploys |
| **Low data gravity** | The module owns a self-contained set of tables — no multi-table joins with the rest |

Anti-patterns for "first extraction":
- The auth module (touched by *everything*).
- The database itself (unclear boundary).
- Something you don't understand (don't learn distributed systems on a core path).

Start with something peripheral: **notifications, search, reporting, PDF generation, audit logging**. Prove the playbook, then go deeper.

---

## 4. The Anti-Corruption Layer (ACL)

The new service and the old monolith use different vocabularies. Don't let the monolith's concepts leak into the new service.

```
┌──────────────┐     ACL    ┌──────────────────┐
│  Monolith    │◄──────────►│   New service    │
│ (legacy schema)│           │ (clean domain)   │
└──────────────┘    translates│                │
                              └──────────────────┘
```

- ACL is a translation layer at the boundary of the new service.
- Old → New translates incoming events into the new domain model.
- New → Old translates outgoing calls into legacy shapes.
- Keeps the new code clean; isolates the legacy ugly-ness.

---

## 5. Database decomposition — the hard part

### 5.1 Stage 0: shared DB (start here)

```
┌────────────────┐       ┌─────────────────┐
│    Monolith    │──────►│                 │
└────────────────┘       │     shared      │
                         │   database      │
┌────────────────┐       │                 │
│ New service    │──────►│                 │
└────────────────┘       └─────────────────┘
```

**Fine for a few weeks.** Both services use the same tables. Lets you move app logic without a data migration. Accept it as a transitional state; anti-pattern as a long-term model.

### 5.2 Stage 1: views + separation of concerns

Create database views that expose only the new service's needs. Add constraints (permissions, RLS) so the monolith can't sneak writes into "new service" tables.

### 5.3 Stage 2: new service owns its tables; monolith reads via API

```
monolith ─── reads/writes ─── monolith tables
monolith ─── API calls ─────► new service ── owns ──► its tables
```

Monolith is no longer coupled to the new service's schema. Schema changes are private.

### 5.4 Stage 3: data migration out

For tables that are moving ownership:

1. Copy schema to the new service's database (expand).
2. Dual-write from *both* the monolith and new service during a transition.
3. Backfill historical data.
4. Cutover: readers now read from the new DB.
5. Monolith stops writing to its old tables.
6. Drop the old tables.

Sound familiar? It's the expand-contract pattern from [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md), applied across two databases.

Use **CDC** (see [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md)) to keep the old and new DBs in sync during the transition — far safer than app-level dual-writes.

---

## 6. Routing & rollback

### 6.1 The strangler proxy

```
Request ───► Strangler proxy / API GW
                  │
          ┌───────┴───────┐
          │               │
    route to old    route to new
    (90%)           (10%)
```

- Nginx / Envoy / a simple service does the routing.
- Flags read per request: `flag("notifications.use_new_service", user_id=...)`.
- Per-route granularity (maybe `POST /notifications` is on the new service while `GET /notifications/history` is still old).

### 6.2 Shadow / mirrored traffic

Before you route *real* traffic, send **copies**:

```
Request ───► monolith (primary path)
              │
              └── tee ──► new service (no response consumed)
                               │
                               └── compare output with monolith's
                                    log differences
```

Run for days/weeks. Any difference is a behavioural drift bug to fix before cutover.

### 6.3 Graduated rollout

1% → 10% → 50% → 100%, with bake time between steps. Rollback is just setting the flag back to 0% — no redeploy needed.

---

## 7. Cross-cutting concerns

### 7.1 Transactions

The monolith could do `BEGIN; update order; update inventory; COMMIT;`. Split across services and you need:

- **Saga** — sequence of local transactions + compensating actions. See [../03-AdvancedConcepts/03-DistributedTransactions.md](../03-AdvancedConcepts/03-DistributedTransactions.md).
- **Outbox pattern** — publish events transactionally with local state. See [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md).

Don't try to replicate 2PC — it's fragile and often unavailable in your infra.

### 7.2 Shared auth / identity

Extract auth *once*, centrally, and let every service hit it. Don't re-implement auth in each new service.

### 7.3 Tracing & observability

Before you split, instrument with OpenTelemetry so cross-service calls are traceable. A distributed system without tracing is a haunted house.

### 7.4 Contract testing

Old monolith and new service must agree on the API. Contract tests (Pact, Spring Cloud Contract) pin the contract and fail the build on drift.

---

## 8. Timeline — what "safe" looks like

```
┌──────────────────────────────────────────────────────────────┐
│                 REALISTIC MIGRATION TIMELINE                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Week 0   — pick bounded context, define API                 │
│  Week 1-2 — build facade in monolith, everything still inline│
│  Week 3-4 — stand up new service (shared DB), shadow traffic │
│  Week 5-6 — route 10% traffic, bake, compare                 │
│  Week 7   — route 50%                                        │
│  Week 8   — route 100%                                       │
│  Week 9-12— decompose data: dual-write, backfill, verify     │
│  Week 13-14— flip reads to new DB                            │
│  Week 15+ — remove monolith's dead code, contract phase      │
│                                                              │
│  Pick the NEXT context after the first one is stable.        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

If this looks long, good — that's the honest number. Shortcutting kills projects.

---

## 9. Failure modes to plan for

| Risk | Mitigation |
|------|------------|
| New service bug corrupts data | Dual-write + periodic reconciliation job compares old and new |
| Perf regression | Shadow-traffic latency metrics; benchmark before ramp |
| Cascading failure (see [05](05-RetryStormsAndCircuitBreakers.md)) | Circuit breaker in monolith's calls to new service; fallback to in-process path during incident |
| Diverging schemas | Contract tests in CI |
| Flag misconfiguration routes 100% too early | Flag service with audit log + approval gates |
| Rollback leaves inconsistent state | Design every step to be reversible; never drop data in the forward direction |
| Team boundary misalignment | One team owns the extracted service end-to-end (Conway's law) |

---

## 10. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| "Rewrite everything in Go" | Distracts from value; months of rewrite before anyone sees benefit |
| Extract auth first | Too entangled; any bug blocks everyone |
| Make one giant PR per extraction | Un-reviewable, un-rollbackable; split into multi-week trickle of PRs |
| Skip shadow mode | Production is the first place you see a bug |
| New service calls back into monolith synchronously | You've just made the monolith a dependency of its successor |
| Micro-microservices | 50 services for a 5-engineer team = operational collapse. Be proportional |
| Ignore data gravity | Extract the code but leave the tables → every call crosses DB boundary → latency blows up |

---

## 11. Interview talking points

- **Invoke the Strangler Fig by name.** Bonus points for "strangler fig + anti-corruption layer".
- **Walk the seven-step playbook.** Carve, extract interface, stand up service, route gradually, migrate data, remove old code, repeat.
- **Call out "start with something peripheral".** Demonstrates pragmatism.
- **Decompose data last.** Explain why shared DB is OK as a transitional state.
- **Mention Sagas + outbox** for cross-service transactions.
- **Quantify the timeline.** Weeks-to-months per context; years for a whole monolith. Don't pretend it's fast.
- **Contract tests + shadow traffic + feature flags.** The three ops ingredients that separate this from a rewrite-and-pray.

---

## 12. Related reading

- [../03-AdvancedConcepts/06-Microservices.md](../03-AdvancedConcepts/06-Microservices.md) — microservices patterns (deeper coverage).
- [../03-AdvancedConcepts/03-DistributedTransactions.md](../03-AdvancedConcepts/03-DistributedTransactions.md) — Saga / outbox / 2PC.
- [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md) — data migration mechanics.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — syncing the two databases during transition.
- [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md) — dual-writes need idempotency.
