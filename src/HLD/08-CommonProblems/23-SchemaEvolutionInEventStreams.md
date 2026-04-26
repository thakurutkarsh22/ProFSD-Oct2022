# Schema Evolution in Event Streams

> **TL;DR.** Your Kafka topic has 90 days of retention, three producer versions shipping over the quarter, and six downstream consumers on different release cadences. Add one field carelessly and you break a reader that processed events published last month. The discipline is **schema registry + explicit compatibility modes + versioned serialization (Avro / Protobuf) + rules for what changes are allowed**. The moment you skip any of these, your stream becomes a silently-lossy integration contract nobody can change.

---

## 1. Why this is harder than DB schemas

A database has exactly one "now" schema — all readers see today's shape. An event stream has **history**:

```
Retention window: 90 days
  ┌────────────────────────────────────────────────────────┐
  │ v1 events (Jan)   v1  v2 (Feb - field added)           │
  │                                                        │
  │                        v2  v3 (Mar - field renamed)    │
  │                                                        │
  │                            v3 events (current)         │
  └────────────────────────────────────────────────────────┘

Consumers live on different versions:
  ┌────────┐  ┌────────┐  ┌────────┐
  │consumer│  │consumer│  │consumer│
  │  A v1  │  │  B v2  │  │  C v3  │
  └────────┘  └────────┘  └────────┘
   reading     reading     reading
   old topic   all of it   new only
```

Every published event must be readable by every consumer that might receive it — **for as long as the retention window**. Change anything incompatibly → historical events become un-parseable → consumer crashes → backlog → incident.

---

## 2. Schema-on-read vs schema-on-write

| Approach | What it means | Reality |
|----------|---------------|---------|
| **Schema-on-write** | Every event conforms to a registered schema at publish time | Producers and consumers both see the contract; evolution is controlled |
| **Schema-on-read** | Events are "just bytes" (JSON, free form); each consumer interprets as it likes | "Flexibility" — until every consumer has a slightly different interpretation and data drift is silent |

For anything at scale, **pick schema-on-write**. JSON streams without a registry are technical debt shipped as velocity.

---

## 3. Three serialization formats

```
┌───────────────────────────────────────────────────────────────┐
│                  FORMATS AND THEIR TRADE-OFFS                  │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  JSON (+ JSON Schema)                                         │
│    + Human readable, debuggable                               │
│    − 2-5× larger payload                                      │
│    − Schema enforcement only if you wire it up                │
│    − No efficient binary evolution                            │
│                                                               │
│  Avro                                                         │
│    + Compact, schema evolves gracefully                       │
│    + Schema travels with data (or via registry ID)            │
│    + First-class compatibility rules                          │
│    − Schema is needed to read the bytes                       │
│                                                               │
│  Protobuf                                                     │
│    + Compact, fast, polyglot                                  │
│    + Field numbers are the contract (stable)                  │
│    + Nice tooling, gRPC integration                           │
│    − Schema evolution is manual discipline                    │
│                                                               │
│  Thrift / Flatbuffers — niche, specific use cases             │
└───────────────────────────────────────────────────────────────┘
```

Production defaults:
- **Avro + Schema Registry** — the Kafka-ecosystem standard.
- **Protobuf + Confluent / Buf Schema Registry** — growing, especially outside Kafka.
- **JSON + JSON Schema** — prototypes; internal-only streams with few consumers; or where humans debug via CLI.

---

## 4. Schema Registry — the source of truth

```
┌────────────────────────────────────────────────────────────┐
│                   SCHEMA REGISTRY FLOW                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Producer                 Broker (Kafka)        Consumer   │
│  │                         │                     │         │
│  │ 1. lookup schema ID     │                     │         │
│  │    (subject = topic-val)│                     │         │
│  │    in registry          │                     │         │
│  │                         │                     │         │
│  │ 2. serialize event      │                     │         │
│  │    with that schema     │                     │         │
│  │                         │                     │         │
│  │ 3. publish:             │                     │         │
│  │    [magic][schema_id][payload]                │         │
│  │─────────────────────────►                     │         │
│  │                         │ (just bytes; doesn't  │       │
│  │                         │  care about schema)   │       │
│  │                         │─────────────────────► │       │
│  │                         │                     │         │
│  │                         │                     │ 4. read │
│  │                         │                     │    schema_id│
│  │                         │                     │ 5. fetch│
│  │                         │                     │    schema│
│  │                         │                     │    from │
│  │                         │                     │    registry│
│  │                         │                     │ 6. deserialize│
│                                                            │
└────────────────────────────────────────────────────────────┘
```

The schema registry:

- Stores all schema versions per "subject" (usually `<topic>-value`).
- Enforces compatibility rules on every register.
- Serves schemas by ID to consumers (cached locally).
- Rejects incompatible changes at registration time, not in production.

Classic implementations: **Confluent Schema Registry**, **Apicurio Registry**, **AWS Glue Schema Registry**, **Buf Schema Registry** (Protobuf).

---

## 5. Compatibility modes — pick one and commit

```
┌──────────────────────────────────────────────────────────┐
│                  COMPATIBILITY MATRIX                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  BACKWARD — new consumer can read old events             │
│    (consumer upgraded first; producers upgrade later)    │
│                                                          │
│  FORWARD  — old consumer can read new events             │
│    (producer upgraded first; consumers upgrade later)    │
│                                                          │
│  FULL     — both directions                              │
│    (safest; most constraints on changes)                 │
│                                                          │
│  TRANSITIVE variants — compatibility with ALL prior       │
│    versions, not just the immediately previous one       │
│                                                          │
│  NONE     — no checks. Do not use in production.         │
└──────────────────────────────────────────────────────────┘
```

### 5.1 Which to choose

- **BACKWARD (or BACKWARD_TRANSITIVE)** — default, good for most producer-driven flows. Consumers need to be upgraded when they want to use new fields, but old consumers keep working.
- **FORWARD (or FORWARD_TRANSITIVE)** — when you must upgrade the producer first without touching consumers.
- **FULL (or FULL_TRANSITIVE)** — strictest. Use when consumers can be on arbitrary versions (ideal for platform teams / shared topics).

Once chosen, **don't flip** midstream — you'll let through a change that breaks past guarantees.

### 5.2 What each mode allows

| Change | BACKWARD | FORWARD | FULL |
|--------|----------|---------|------|
| Add optional field (with default) | ✅ | ✅ | ✅ |
| Add required field | ❌ | ✅ (new producer only) | ❌ |
| Remove optional field | ✅ | ❌ | ❌ |
| Remove required field | ❌ | ❌ | ❌ |
| Rename field | ❌ | ❌ | ❌ (use aliases) |
| Change field type | rarely | rarely | rarely |
| Change field default | ✅ mostly | ✅ mostly | ✅ mostly |

**Rule of thumb**: *add fields with defaults*, *deprecate instead of remove*. Anything else is a migration project.

---

## 6. Safe changes vs breaking changes

### 6.1 Safe

- **Add optional field (with default).** Consumers that don't know about it ignore it.
- **Deprecate a field.** Leave it present; stop writing meaningful values; document.
- **Add an enum value** — BACKWARD only, since old consumers may crash on unknown enum. Avro's "default value" on enums handles this; Protobuf has a similar convention.
- **Add a new message type** — entirely new fields don't break existing.

### 6.2 Unsafe

- **Rename a field** without alias.
- **Remove a field** that old consumers require.
- **Change field type** (string → int). Even "widening" can break serialization layout.
- **Tightening constraints** (making optional into required).
- **Re-using a field number / position** — catastrophic in Protobuf (silent wrong-parse).

### 6.3 Protobuf specifics

Field numbers are sacred. Never reuse them. Use `reserved` for deleted fields:

```proto
message Order {
  reserved 4, 7;        // never assign these again
  reserved "old_price"; // name also reserved

  int64 id     = 1;
  string name  = 2;
  int32 qty    = 3;
  // (field 4 deleted, reserved)
  double total = 5;
}
```

### 6.4 Avro specifics

- Fields with defaults are added safely.
- Aliases let you rename: `{"name": "full_name", "aliases": ["name"]}`.
- Unions (`["null", "string"]`) model optionality.

---

## 7. Producer / consumer deploy order

```
BACKWARD compat:
  consumer upgrade FIRST (can read old + new)
  producer upgrade SECOND

FORWARD compat:
  producer upgrade FIRST (consumers can still read new)
  consumer upgrade SECOND

FULL compat:
  either order works
```

The registry enforces compatibility at schema registration. Your deploy pipeline should also enforce the order — a producer deploy that registers a BACKWARD-only change must block until consumers are upgraded (or confirmed safe).

---

## 8. Versioning patterns

### 8.1 Single subject per topic (default)

One schema subject per topic, versioned. Simple. Works for 90% of cases.

### 8.2 Multiple types on one topic

Some systems put heterogeneous events on the same topic (e.g., an "all order events" topic with `OrderCreated`, `OrderUpdated`, `OrderCancelled`). Options:

- **Union type** (Avro) — topic-level schema is a union of all event types.
- **Schema reference** — each event carries schema ID; consumer demuxes.
- **Envelope pattern** — outer schema is `{type: string, payload: bytes}`; payload has its own schema lookup.

Union / envelope is cleaner once you have many event types.

### 8.3 Topic-per-version vs evolve-in-place

- **Evolve in place** (single topic, evolving schema) — default; what schema registry is for.
- **Topic-per-major-version** (`orders.v1`, `orders.v2`) — for major, incompatible redesigns. Run both; consumers migrate over weeks/months; retire `v1` once no consumer is reading.

Don't version-bump for minor changes; that's what the compat rules are for.

---

## 9. Breaking changes when you *must*

Sometimes you have to make a breaking change (remove a confusing field, fundamentally restructure). The safe recipe:

1. **Announce.** Calendar; channel; owner; deadline.
2. **Add the new shape alongside the old.** Dual-write both old and new fields.
3. **Migrate consumers one by one** to the new shape.
4. **Stop writing the old shape** after all consumers migrated.
5. **Deprecate the old shape**; schedule removal.
6. **Remove** (a new topic version or a field `reserved` call).

This is the **expand-contract** pattern ([`11-ZeroDowntimeSchemaMigration.md`](11-ZeroDowntimeSchemaMigration.md)) applied to events.

### 9.1 Dual-shape event

```json
{
  "event_version": 2,

  "user_id": "u-42",     // v1 field
  "user":    { "id": "u-42", "name": "Alice" },  // v2 nested version

  "status":  "ACTIVE",   // v1 field
  "lifecycle": { "state": "ACTIVE", "since": "2024-01-01" }  // v2 richer
}
```

Both are present; either-or consumers can read.

---

## 10. Poison events and DLQ interactions

See also [`10-DeadLetterQueuesAndPoisonMessages.md`](10-DeadLetterQueuesAndPoisonMessages.md).

An incompatible change *plus* a consumer that can't fall back cleanly = poison event. Rules:

- Consumers should **log-and-skip** unknown fields (Avro/Protobuf default), never crash.
- On deserialization error, publish the raw bytes to a DLQ with context, not crash the consumer.
- Circuit-break: if > X% of events fail deserialization in a window, alarm loudly and halt; something systemic broke.

---

## 11. Observability

- **Schema version distribution per consumer group.** Lag by schema version; some consumers stuck on v1?
- **Compatibility check failures at registry**. Indicates a producer trying to publish breaking changes.
- **Deserialization error rate.** Should be ≈ 0.
- **DLQ depth.** Growing? Pipeline broken.
- **Retention vs oldest consumer position.** If a consumer lags so far it reads events older than its schema version supports, you have a problem.

---

## 12. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| JSON "just works" — no schema | Consumers drift; data quality erodes; impossible to evolve safely |
| No schema registry ("we'll agree on the shape") | Agreement breaks; silent breakage in prod |
| Compatibility = NONE or disabled | Exists to prevent the problem you're about to cause |
| Removing fields without a deprecation window | Old consumers crash on ancient events in retention |
| Renaming fields without aliases | Same crash |
| Reusing Protobuf field numbers | *Silent* wrong parse. Worst kind of bug. |
| Changing types | String → int parses as 0; wrong-value writes |
| Different compatibility mode per topic without rationale | Inconsistent guarantees; documentation nightmare |
| Making a required field optional (should be safe?) | Usually yes for consumers, but breaks writers expecting it |
| Not communicating breaking changes | Announce in channels, not in the diff |
| Consumers that crash on unknown fields | Should be forward-compatible by default |

---

## 13. Interview talking points

- **Always name "schema registry + Avro / Protobuf + compatibility mode"** as the foundation.
- **State which compatibility mode you'd pick and why.** "BACKWARD-TRANSITIVE: producer upgrades freely; consumers catch up."
- **Producer / consumer deploy order** is a direct consequence of the mode.
- **Safe changes: additive with defaults.** Unsafe: remove/rename/retype.
- **Protobuf `reserved`** to prevent field-number reuse.
- **Evolve in place vs new topic version.** Default to the former; only break a topic for major redesigns.
- **Dual-shape events + expand/contract** for the rare hard migration.
- **Consumers must be forward-compatible.** Ignore unknown fields.
- **Poison message handling.** DLQ + circuit breaker on deserialization errors.
- **90-day retention = 90 days of schema compatibility** obligation.

---

## 14. Related reading

- [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md) — same discipline for DB schemas; the expand-contract pattern.
- [10-DeadLetterQueuesAndPoisonMessages.md](10-DeadLetterQueuesAndPoisonMessages.md) — where un-parseable events go.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — CDC and outbox as producers into versioned topics.
- [17-WatermarksAndLateEvents.md](17-WatermarksAndLateEvents.md) — streaming topologies that depend on stable schemas.
- [../03-AdvancedConcepts/05-StreamProcessing.md](../03-AdvancedConcepts/05-StreamProcessing.md) — stream fundamentals.
- [../02-BuildingBlocks/Components/01-Kafka.md](../02-BuildingBlocks/Components/01-Kafka.md) — Kafka mechanics (path may differ per repo).
