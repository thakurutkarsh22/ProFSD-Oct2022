# Grafana & the Observability Stack — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 6-8 hours | **Priority:** Must Know  
> **Sources:** Grafana Labs Official Docs, Grafana Engineering Blog, ByteByteGo (Alex Xu), Uber Engineering, Airbnb Engineering, Prometheus Docs, OpenTelemetry Docs, Post-Mortems  
> **For:** Senior Engineers (7+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is Grafana](#1-what-is-grafana)
2. [Core Architecture](#2-core-architecture)
3. [The LGTM Stack — Full Observability Ecosystem](#3-the-lgtm-stack--full-observability-ecosystem)
4. [Grafana Mimir — Metrics at Scale](#4-grafana-mimir--metrics-at-scale)
5. [Grafana Loki — Log Aggregation](#5-grafana-loki--log-aggregation)
6. [Grafana Tempo — Distributed Tracing](#6-grafana-tempo--distributed-tracing)
7. [Alerting Architecture (Unified Alerting)](#7-alerting-architecture-unified-alerting)
8. [Plugin Architecture & Extensibility](#8-plugin-architecture--extensibility)
9. [High Availability & Production Deployment](#9-high-availability--production-deployment)
10. [Query Execution & Dashboard Rendering Internals](#10-query-execution--dashboard-rendering-internals)
11. [Real-World Usage at Scale](#11-real-world-usage-at-scale)
12. [When Grafana Failed — Production Incidents](#12-when-grafana-failed--production-incidents)
13. [When NOT to Use Grafana](#13-when-not-to-use-grafana)
14. [Scenarios Where Grafana Could Not Cope](#14-scenarios-where-grafana-could-not-cope)
15. [Grafana vs Datadog vs New Relic](#15-grafana-vs-datadog-vs-new-relic)
16. [Designing a Monitoring System (Interview Pattern)](#16-designing-a-monitoring-system-interview-pattern)
17. [Anti-Patterns That Kill Grafana](#17-anti-patterns-that-kill-grafana)
18. [Performance Tuning Cheat Sheet](#18-performance-tuning-cheat-sheet)
19. [Interview Questions — Medium](#19-interview-questions--medium)
20. [Interview Questions — Hard](#20-interview-questions--hard)
21. [Quick Reference Card](#21-quick-reference-card)

---

## 1. What Is Grafana

Grafana is an **open-source observability and data visualization platform** originally created by Torkel Ödegaard in 2014 as a fork of Kibana. It is NOT a database, NOT a collector, and NOT an APM tool by itself. It is a **query, visualization, and alerting engine** that connects to external data sources.

Think of it as the **"brain" of your monitoring stack** — it doesn't store data, but it knows how to ask the right questions and display the answers.

```
What Grafana IS:                              What Grafana is NOT:

  ✓ Visualization/dashboarding engine           ✗ A time-series database
  ✓ Multi-source query aggregator               ✗ A metrics collector/agent
  ✓ Unified alerting platform                   ✗ An APM tool (by itself)
  ✓ Plugin-extensible platform                  ✗ A log storage engine
  ✓ Correlation layer (logs + metrics + traces) ✗ A replacement for Prometheus
  ✓ RBAC-enabled multi-tenant UI                ✗ A replacement for ELK stack
```

### The Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THREE PILLARS OF OBSERVABILITY                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   METRICS                    LOGS                     TRACES                │
│   ┌─────────────────┐   ┌─────────────────┐   ┌──────────────────┐        │
│   │ "WHAT happened"  │   │ "WHY it happened"│   │ "WHERE it happened│       │
│   │                   │   │                   │   │  across services" │       │
│   │ CPU = 95%         │   │ ERROR: OOM killed │   │ UserSvc → AuthSvc│       │
│   │ Latency = 500ms   │   │ at line 42        │   │ → DB → Cache     │       │
│   │ Error rate = 5%   │   │ stack trace...    │   │ total: 1200ms    │       │
│   └─────────────────┘   └─────────────────┘   └──────────────────┘        │
│          │                       │                       │                  │
│          │          Grafana Unifies All Three             │                  │
│          └───────────────────┐ ┌─────────────────────────┘                  │
│                              ▼ ▼                                            │
│                    ┌──────────────────────┐                                  │
│                    │      GRAFANA          │                                  │
│                    │  Query + Visualize +  │                                  │
│                    │  Alert + Correlate    │                                  │
│                    └──────────────────────┘                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why Grafana Matters in System Design Interviews

| Reason | Detail |
|--------|--------|
| **Every system needs monitoring** | Step 4 of any system design interview is "Monitoring & Observability" |
| **Multi-source correlation** | Shows you understand observability beyond "just add Prometheus" |
| **Alerting design** | Demonstrates you think about failure detection, not just happy paths |
| **Cost awareness** | Grafana (OSS) vs Datadog ($$$) is a real production tradeoff |
| **Scale reasoning** | High-cardinality metrics, retention policies, downsampling |

---

## 2. Core Architecture

### The Four Layers

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        GRAFANA CORE ARCHITECTURE                             │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  LAYER 1: FRONTEND (Browser)                                                 │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │  React + TypeScript + Scenes Library                               │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────────┐    │      │
│  │  │Dashboard  │  │ Panel    │  │ Query    │  │ Variable       │    │      │
│  │  │ Engine    │  │ Renderers│  │ Editors  │  │ Interpolation  │    │      │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────────────┘    │      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                              │ HTTP/WebSocket                                │
│                              ▼                                               │
│  LAYER 2: BACKEND SERVER (Go Binary)                                         │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │  ┌───────────┐ ┌───────────┐ ┌────────────┐ ┌───────────────┐    │      │
│  │  │ Auth &    │ │ Dashboard │ │ Alerting   │ │ Provisioning  │    │      │
│  │  │ RBAC      │ │ Service   │ │ Engine     │ │ Engine        │    │      │
│  │  └───────────┘ └───────────┘ └────────────┘ └───────────────┘    │      │
│  │  ┌───────────┐ ┌───────────┐ ┌────────────┐ ┌───────────────┐    │      │
│  │  │ Query     │ │ Transform │ │ Plugin     │ │ API Server    │    │      │
│  │  │ Service   │ │ Engine    │ │ Manager    │ │ (REST/gRPC)   │    │      │
│  │  └───────────┘ └───────────┘ └────────────┘ └───────────────┘    │      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                              │ Plugin RPC (gRPC/HashiCorp)                   │
│                              ▼                                               │
│  LAYER 3: DATA SOURCE PLUGINS (Subprocess / gRPC)                            │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────────┐     │      │
│  │  │Prometheus │ │ InfluxDB  │ │ Postgres  │ │ CloudWatch    │     │      │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────────┘     │      │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────────┐     │      │
│  │  │ Loki      │ │ Tempo     │ │ Mimir     │ │ Elasticsearch │     │      │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────────┘     │      │
│  │  + 150+ community plugins                                        │      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                              │                                               │
│                              ▼                                               │
│  LAYER 4: METADATA DATABASE (Internal Only)                                  │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │  SQLite (dev) / MySQL / PostgreSQL (prod)                          │      │
│  │  Stores: dashboards, users, orgs, alert rules, annotations         │      │
│  │  Does NOT store: metrics, logs, traces (those live in data sources)│      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Request Flow — What Happens When You Open a Dashboard

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                  DASHBOARD LOAD — FULL REQUEST FLOW                          │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. User opens dashboard URL                                                 │
│     │                                                                        │
│     ▼                                                                        │
│  2. Frontend sends GET /api/dashboards/uid/{uid}                             │
│     │                                                                        │
│     ▼                                                                        │
│  3. Backend authenticates (session/token/OAuth) + checks RBAC permissions    │
│     │                                                                        │
│     ▼                                                                        │
│  4. Dashboard JSON loaded from metadata DB (layout, panels, variables)       │
│     │                                                                        │
│     ▼                                                                        │
│  5. Frontend parses JSON, resolves variables, builds panel grid              │
│     │                                                                        │
│     ▼                                                                        │
│  6. For each panel, frontend sends POST /api/ds/query                        │
│     │  ┌─ Panel A: PromQL → Prometheus                                       │
│     │  ├─ Panel B: LogQL  → Loki                                             │
│     │  └─ Panel C: SQL    → PostgreSQL                                       │
│     │                                                                        │
│     ▼                                                                        │
│  7. Backend's Query Service routes each query to appropriate plugin          │
│     │                                                                        │
│     ▼                                                                        │
│  8. Data source plugin translates to native query, fetches from source       │
│     │                                                                        │
│     ▼                                                                        │
│  9. Raw response → Grafana Data Frames (unified format) → Transformations   │
│     │                                                                        │
│     ▼                                                                        │
│  10. Frontend renders: Time series → Graph, Table → Grid, Stat → Big Number │
│                                                                              │
│  Performance Metrics shown per panel:                                        │
│  ┌─────────────────────────────────────────┐                                 │
│  │  Q: 120ms (query)                       │                                 │
│  │  T:  15ms (transformation)              │                                 │
│  │  R:  30ms (render)                      │                                 │
│  └─────────────────────────────────────────┘                                 │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. The LGTM Stack — Full Observability Ecosystem

The **LGTM stack** is Grafana Labs' full observability platform. Knowing this stack is critical because interviewers expect you to understand **not just Grafana, but what feeds data into it**.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                       THE LGTM STACK                                         │
│              (Loki + Grafana + Tempo + Mimir)                                │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────────┐       │
│  │ Application  │  │ Application  │  │ Application│  │ Infrastructure│       │
│  │ Service A    │  │ Service B    │  │ Service C  │  │ (k8s, VMs)   │       │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘  └──────┬───────┘       │
│         │                │                 │                │                │
│         └────────────────┴─────────────────┴────────────────┘                │
│                                    │                                         │
│                     ┌──────────────▼──────────────┐                          │
│                     │   OpenTelemetry Collector    │                          │
│                     │   (or Grafana Alloy Agent)   │                          │
│                     │                              │                          │
│                     │  Receives: OTLP, Prometheus, │                          │
│                     │  FluentBit, Jaeger, Zipkin   │                          │
│                     └──┬──────────┬────────────┬───┘                          │
│                        │          │            │                              │
│               Metrics  │   Logs   │   Traces   │                              │
│                        ▼          ▼            ▼                              │
│              ┌─────────────┐ ┌─────────┐ ┌──────────┐                        │
│              │   MIMIR     │ │  LOKI   │ │  TEMPO   │                        │
│              │             │ │         │ │          │                        │
│              │ Prometheus- │ │ Like    │ │ Trace    │                        │
│              │ compatible  │ │ Prom,   │ │ storage  │                        │
│              │ TSDB        │ │ but for │ │ (OTLP)   │                        │
│              │             │ │ logs    │ │          │                        │
│              │ PromQL      │ │ LogQL   │ │ TraceQL  │                        │
│              └──────┬──────┘ └────┬────┘ └─────┬────┘                        │
│                     │             │             │                             │
│                     └─────────────┴─────────────┘                             │
│                                   │                                           │
│                     ┌─────────────▼─────────────┐                             │
│                     │         GRAFANA            │                             │
│                     │                            │                             │
│                     │  Dashboards + Alerts +     │                             │
│                     │  Explore + Correlations    │                             │
│                     │                            │                             │
│                     │  "Show me the metric spike │                             │
│                     │   → find related logs      │                             │
│                     │   → trace the exact request│                             │
│                     │   → alert the right team"  │                             │
│                     └────────────────────────────┘                             │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ GRAFANA ALLOY: Unified telemetry agent (replaces Promtail, OTel Agent) │  │
│  │ Component-based: Receivers → Processors → Exporters (like OTel Coll.)  │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

### LGTM Quick Comparison

| Component | Stores | Query Language | Analogous To |
|-----------|--------|----------------|--------------|
| **Mimir** | Metrics (time-series) | PromQL | Prometheus long-term storage, Thanos, VictoriaMetrics |
| **Loki** | Logs (indexed labels, raw log lines) | LogQL | Elasticsearch/OpenSearch, Splunk |
| **Tempo** | Traces (spans) | TraceQL | Jaeger, Zipkin, AWS X-Ray |
| **Grafana** | Nothing (metadata only) | Delegates to above | Kibana, Datadog dashboards |

---

## 4. Grafana Mimir — Metrics at Scale

Mimir is the **long-term, horizontally scalable Prometheus-compatible TSDB**. Grafana Cloud runs Mimir internally to handle billions of active series.

### Architecture — Classic vs Ingest Storage

```
┌─────────────────────────────────────────────────────────────────────────────┐
│             MIMIR ARCHITECTURE — TWO MODES                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CLASSIC (Legacy):                                                           │
│                                                                              │
│  Prometheus ──remote_write──► Distributor ──► Ingester (RF=3) ──► Object    │
│                                    │          (stateful, WAL)      Storage   │
│                                    │               │                         │
│                               hash ring        flush every                   │
│                               sharding          2 hours                      │
│                                                                              │
│  Problem: Ingesters are shared between read + write paths.                   │
│           A heavy query can OOM an ingester and drop live writes.            │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  INGEST STORAGE (New — Preferred):                                           │
│                                                                              │
│  Prometheus ──► Distributor ──► KAFKA ──┬──► Block Builder ──► Object Store  │
│                                         │                                    │
│                                         └──► Querier (reads from Kafka       │
│                                               for recent data +             │
│                                               Object Store for historical)  │
│                                                                              │
│  ✓ Read and Write paths are fully decoupled                                 │
│  ✓ No stateful ingesters — Kafka handles durability                          │
│  ✓ Heavy queries cannot disrupt live writes                                  │
│  ✓ Simpler scaling — just add Kafka partitions                               │
│  ✓ Lower cost — no RF=3 replication on ingesters                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Mimir Write Path (Ingest Storage)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MIMIR WRITE PATH                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Prometheus / OTel Collector / Grafana Alloy                                 │
│       │                                                                      │
│       │  remote_write (Snappy-compressed Protobuf)                           │
│       ▼                                                                      │
│  ┌──────────────┐                                                            │
│  │ DISTRIBUTOR   │  Validates samples, enforces rate limits,                 │
│  │               │  shards by metric name hash → Kafka partition             │
│  └───────┬──────┘                                                            │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────┐                                                            │
│  │    KAFKA      │  Durable buffer. Write is ACK'd here.                     │
│  │  (partitions) │  Decouples write from storage flush.                      │
│  └───────┬──────┘                                                            │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────┐                                                            │
│  │ BLOCK BUILDER │  Consumes from Kafka, organizes into                      │
│  │               │  TSDB blocks (2-hour range, ~120 samples/chunk)           │
│  └───────┬──────┘                                                            │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────────┐                                                        │
│  │  OBJECT STORAGE   │  S3, GCS, Azure Blob, MinIO                           │
│  │  (long-term)      │  TSDB block format (Prometheus-compatible)            │
│  └──────────────────┘                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Mimir Read Path

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MIMIR READ PATH                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Grafana Dashboard (PromQL query)                                            │
│       │                                                                      │
│       ▼                                                                      │
│  ┌──────────────────┐                                                        │
│  │  QUERY FRONTEND   │  Splits large time ranges into sub-queries,           │
│  │                    │  caches results, enforces query limits                │
│  └───────┬──────────┘                                                        │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────┐     ┌──────────────────┐                                   │
│  │   QUERIER    │────►│  Recent: Kafka    │  (last 30-60 min)                │
│  │              │     │  Historical: S3   │  (older blocks)                  │
│  │              │     │  Cache: Memcached │  (hot queries)                   │
│  └───────┬──────┘     └──────────────────┘                                   │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────────┐                                                        │
│  │  QUERY FRONTEND   │  Merges sub-query results                             │
│  │  (merge phase)    │  Returns unified PromQL result                        │
│  └──────────────────┘                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Scale Numbers (Mimir)

| Metric | Number |
|--------|--------|
| Active series at Grafana Cloud | **1+ billion** |
| Pipedrive deployment | 8 million active series, 500 users |
| Airbnb migration | 100 million samples/second ingestion |
| Block size | 2-hour time range |
| Chunk size | ~120 samples per chunk |

---

## 5. Grafana Loki — Log Aggregation

Loki's core insight: **index only metadata (labels), not log content.** This makes it 10-100x cheaper than Elasticsearch for log storage.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                LOKI vs ELASTICSEARCH — ARCHITECTURE DIFFERENCE                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ELASTICSEARCH:                      LOKI:                                   │
│                                                                              │
│  "Index EVERYTHING"                  "Index labels ONLY"                     │
│                                                                              │
│  Log line: "User 123 logged in       Log line: same                          │
│   from IP 10.0.0.1 at 2024-01-01"                                           │
│                                                                              │
│  Index entries:                       Index entries:                          │
│  ┌──────────────────────┐            ┌──────────────────────┐                │
│  │ "User"    → doc1      │            │ {app="auth"}  → chunk│                │
│  │ "123"     → doc1      │            │ {env="prod"}  → chunk│                │
│  │ "logged"  → doc1      │            │ {level="info"}→ chunk│                │
│  │ "in"      → doc1      │            └──────────────────────┘                │
│  │ "IP"      → doc1      │                                                   │
│  │ "10.0.0.1"→ doc1      │            Log content = compressed,              │
│  │ ... every word ...    │            stored as raw chunks                    │
│  └──────────────────────┘            in object storage (S3)                  │
│                                                                              │
│  Cost: $$$$ (RAM-heavy indexing)      Cost: $ (minimal indexing)              │
│  Speed: Fast full-text search         Speed: Fast by labels, slower           │
│  Scale: Hard to scale storage         full-text (grep over chunks)            │
│                                       Scale: Horizontally scalable            │
│                                                                              │
│  When to use:                         When to use:                            │
│  - Full-text search is critical       - Cost-sensitive log storage            │
│  - Complex query patterns             - Already using Prometheus labels       │
│  - You need search over content       - Don't need full-text indexing         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Loki Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LOKI ARCHITECTURE                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Grafana Alloy / Promtail / FluentBit                                        │
│       │                                                                      │
│       │  Push logs via HTTP (POST /loki/api/v1/push)                         │
│       ▼                                                                      │
│  ┌──────────────┐                                                            │
│  │ DISTRIBUTOR   │  Validates, rate-limits, hashes by {labels}               │
│  └───────┬──────┘  to find correct ingester                                  │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────┐                                                            │
│  │  INGESTER     │  Builds in-memory "chunks" of compressed log lines        │
│  │               │  Organized by label set (stream)                          │
│  └───────┬──────┘  Flushes chunks periodically                               │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────────┐                                                        │
│  │  OBJECT STORAGE   │  Chunks (compressed log content) +                    │
│  │  (S3/GCS/Azure)   │  Index (label → chunk pointer, TSDB format)           │
│  └──────────────────┘                                                        │
│                                                                              │
│  READ PATH:                                                                  │
│                                                                              │
│  Grafana (LogQL) → Query Frontend → Querier → Index lookup by labels         │
│                                                  → Fetch matching chunks     │
│                                                  → Decompress + grep/filter  │
│                                                  → Return results            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### LogQL Example

```
# Find error logs for auth service in the last hour
{app="auth-service", env="production"} |= "ERROR" | json | latency > 500

# Breakdown:
# {app="auth-service"}  → Label selector (uses index, FAST)
# |= "ERROR"            → Line filter (grep over chunks)
# | json                 → Parse JSON fields from log line
# | latency > 500        → Filter on parsed field
```

---

## 6. Grafana Tempo — Distributed Tracing

Tempo stores **traces** (sequences of spans across services). Its design mirrors Mimir/Loki: Kafka + Object Storage.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TEMPO ARCHITECTURE (v3.0+)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  App (instrumented with OTel SDK)                                            │
│       │                                                                      │
│       │  OTLP (gRPC or HTTP)                                                 │
│       ▼                                                                      │
│  ┌──────────────┐                                                            │
│  │ DISTRIBUTOR   │  Validates, rate-limits, shards by trace ID               │
│  └───────┬──────┘  Writes to Kafka partition (keyed by trace ID)             │
│          │                                                                   │
│          ▼                                                                   │
│  ┌──────────────┐                                                            │
│  │    KAFKA      │  Durable buffer — write ACK'd here                        │
│  └──┬────────┬──┘                                                            │
│     │        │                                                               │
│     ▼        ▼                                                               │
│  ┌────────┐ ┌────────────┐                                                   │
│  │ LIVE   │ │   BLOCK    │                                                   │
│  │ STORE  │ │  BUILDER   │                                                   │
│  │        │ │            │                                                   │
│  │ Recent │ │ Organizes  │                                                   │
│  │ traces │ │ spans into │                                                   │
│  │ on disk│ │ Apache     │                                                   │
│  │(30-60m)│ │ Parquet    │                                                   │
│  └────────┘ │ blocks     │                                                   │
│             └─────┬──────┘                                                   │
│                   │                                                          │
│                   ▼                                                          │
│           ┌──────────────┐                                                   │
│           │OBJECT STORAGE│  Parquet format (columnar, efficient)              │
│           └──────────────┘                                                   │
│                                                                              │
│  READ PATH:                                                                  │
│  Grafana (TraceQL) → Query Frontend → Querier → Live Store (recent)          │
│                                                + Object Store (historical)   │
│                                     → Query Frontend merges results          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Correlation — The Killer Feature

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              CROSS-SIGNAL CORRELATION IN GRAFANA                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. You see a METRIC spike:  p99 latency jumped from 200ms → 2000ms         │
│                                       │                                      │
│     Click "Explore" → Drill to Logs   │                                      │
│                                       ▼                                      │
│  2. You find the LOG:  "Database connection pool exhausted at 14:23:05"      │
│                                       │                                      │
│     Click trace ID in log line        │                                      │
│                                       ▼                                      │
│  3. You see the TRACE:  UserService → OrderService → DB (timeout: 30s)      │
│                                                                              │
│     Root cause: DB connection leak in OrderService                            │
│                                                                              │
│  Time to root cause: ~2 minutes (vs hours without correlation)               │
│                                                                              │
│  HOW IT WORKS:                                                               │
│  ┌──────────┐     trace_id      ┌──────────┐                                │
│  │  Metric   │ ◄──exemplars───► │  Trace   │                                │
│  │  (Mimir)  │                   │  (Tempo)  │                                │
│  └──────────┘                   └──────────┘                                │
│       ▲                              ▲                                       │
│       │         trace_id             │                                       │
│       └──── in log lines ────────────┘                                       │
│              (Loki)                                                           │
│                                                                              │
│  Key: Applications must emit trace_id in logs and use exemplars in metrics   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Alerting Architecture (Unified Alerting)

### Grafana Unified Alerting Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   GRAFANA UNIFIED ALERTING PIPELINE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                     ┌────────────────────────┐                               │
│                     │     ALERT RULES         │                               │
│                     │                          │                               │
│                     │  "Every 1m, check if     │                               │
│                     │   error_rate > 5%        │                               │
│                     │   for the last 5m"       │                               │
│                     └───────────┬──────────────┘                               │
│                                 │                                             │
│                    ┌────────────▼───────────┐                                 │
│                    │    EVALUATION ENGINE    │                                 │
│                    │                         │                                 │
│                    │ Runs on schedule         │                                 │
│                    │ Queries data sources     │                                 │
│                    │ Evaluates conditions     │                                 │
│                    │ Produces alert instances │                                 │
│                    └────────────┬────────────┘                                 │
│                                 │                                             │
│            ┌────────────────────┼────────────────────┐                        │
│            │                    │                    │                        │
│            ▼                    ▼                    ▼                        │
│     ┌──────────┐        ┌──────────┐        ┌──────────┐                     │
│     │ Instance │        │ Instance │        │ Instance │                     │
│     │ app=web  │        │ app=api  │        │ app=auth │                     │
│     │ OK → 🔥  │        │ OK       │        │ OK → 🔥  │                     │
│     └────┬─────┘        └──────────┘        └────┬─────┘                     │
│          │                                       │                           │
│          └───────────────┬───────────────────────┘                           │
│                          │                                                   │
│               ┌──────────▼───────────┐                                       │
│               │    ALERTMANAGER       │                                       │
│               │                       │                                       │
│               │  1. GROUPING          │  Bundle: app=web + app=auth           │
│               │     (reduce noise)    │  into single notification             │
│               │                       │                                       │
│               │  2. INHIBITION        │  If "cluster_down" is firing,         │
│               │     (suppress child)  │  suppress "pod_down" alerts           │
│               │                       │                                       │
│               │  3. SILENCING         │  Mute during planned maintenance      │
│               │     (manual mute)     │                                       │
│               │                       │                                       │
│               │  4. ROUTING           │  Match labels → notification policy   │
│               │     (who gets what)   │  → contact point                      │
│               └──────────┬───────────┘                                       │
│                          │                                                   │
│          ┌───────────────┼───────────────┐                                   │
│          ▼               ▼               ▼                                   │
│    ┌──────────┐   ┌──────────┐   ┌──────────────┐                            │
│    │  Slack   │   │PagerDuty │   │  Webhook     │                            │
│    │  #alerts │   │ on-call  │   │  (custom)    │                            │
│    └──────────┘   └──────────┘   └──────────────┘                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Notification Policy Tree (Advanced Routing)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│               NOTIFICATION POLICY — TREE-BASED ROUTING                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Root Policy (default receiver: email-ops@company.com)                       │
│  │                                                                           │
│  ├── Match: severity=critical                                                │
│  │   └── Contact: PagerDuty (on-call rotation)                               │
│  │       ├── Match: team=platform                                            │
│  │       │   └── Contact: PagerDuty (platform-oncall)                        │
│  │       └── Match: team=product                                             │
│  │           └── Contact: PagerDuty (product-oncall)                         │
│  │                                                                           │
│  ├── Match: severity=warning                                                 │
│  │   └── Contact: Slack #alerts-warning                                      │
│  │       group_wait: 30s, group_interval: 5m, repeat_interval: 4h            │
│  │                                                                           │
│  └── Match: severity=info                                                    │
│      └── Contact: Slack #alerts-info                                         │
│          group_wait: 1m, repeat_interval: 24h                                │
│                                                                              │
│  KEY TIMING PARAMETERS:                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │ group_wait:      How long to buffer before sending first notif.   │      │
│  │                  (wait for more alerts to group together)          │      │
│  │ group_interval:  How long to wait before sending updates           │      │
│  │ repeat_interval: How long before re-sending if alert still firing │      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Alerting in HA Mode

```
Important: In Grafana HA, ALL servers evaluate ALL alert rules independently.
Alertmanager deduplicates so only ONE notification is sent per alert.

  Server A ──evaluate──► Alert firing ──┐
                                        ├──► Alertmanager (gossip protocol)
  Server B ──evaluate──► Alert firing ──┘    deduplicates → 1 notification
  Server C ──evaluate──► Alert firing ──┘

  Limitation: No load distribution — every server does all the work.
  At scale (1000s of rules): this becomes a bottleneck.
```

---

## 8. Plugin Architecture & Extensibility

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GRAFANA PLUGIN ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  THREE PLUGIN TYPES:                                                         │
│                                                                              │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌──────────────────┐     │
│  │  DATA SOURCE PLUGIN  │  │   PANEL PLUGIN       │  │   APP PLUGIN     │     │
│  │                       │  │                       │  │                  │     │
│  │ Connect to any DB,    │  │ Custom visualization  │  │ Full app with    │     │
│  │ API, or service       │  │ (heatmap, topology,   │  │ custom pages,    │     │
│  │                       │  │  flamegraph, etc.)     │  │ backend APIs,    │     │
│  │ Examples:             │  │                       │  │ and nested       │     │
│  │ - Prometheus          │  │ Examples:             │  │ plugins          │     │
│  │ - PostgreSQL          │  │ - Worldmap            │  │                  │     │
│  │ - Elasticsearch       │  │ - Flamegraph          │  │ Examples:        │     │
│  │ - Datadog             │  │ - Flow chart          │  │ - OnCall         │     │
│  │ - Splunk              │  │ - Candlestick         │  │ - Incident Mgmt  │     │
│  └─────────┬─────────────┘  └──────────────────────┘  └──────────────────┘     │
│            │                                                                 │
│            │  Backend plugins use HashiCorp plugin system:                    │
│            │                                                                 │
│  ┌─────────▼─────────────────────────────────────────────────────────┐       │
│  │                    HASHICORP PLUGIN SYSTEM                          │       │
│  │                                                                     │       │
│  │  Grafana (host) ◄──── gRPC ────► Plugin (subprocess)               │       │
│  │                                                                     │       │
│  │  ✓ Plugins run as isolated subprocesses                             │       │
│  │  ✓ A crashing plugin CANNOT crash Grafana                           │       │
│  │  ✓ Written in Go (or any gRPC-compatible language)                  │       │
│  │  ✓ Plugin gets only necessary interfaces/arguments                  │       │
│  │  ✓ Grafana manages lifecycle (start/stop/health check)             │       │
│  └─────────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│  PLUGIN CONFIGURATION (plugin.json):                                         │
│  {                                                                           │
│    "type": "datasource",                                                     │
│    "name": "My Custom Source",                                               │
│    "backend": true,           ← enables Go backend component                 │
│    "executable": "gpx_myds",  ← binary name                                 │
│    "alerting": true           ← enables Grafana Alerting support             │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. High Availability & Production Deployment

```
┌─────────────────────────────────────────────────────────────────────────────┐
│               GRAFANA HIGH AVAILABILITY DEPLOYMENT                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                        ┌──────────────────┐                                  │
│                        │   Load Balancer   │                                  │
│                        │ (Nginx / HAProxy) │                                  │
│                        │  SSL Termination  │                                  │
│                        └────┬────┬────┬────┘                                  │
│                             │    │    │                                       │
│                    ┌────────┘    │    └────────┐                              │
│                    ▼            ▼            ▼                              │
│             ┌──────────┐ ┌──────────┐ ┌──────────┐                           │
│             │ Grafana  │ │ Grafana  │ │ Grafana  │                           │
│             │ Node 1   │ │ Node 2   │ │ Node 3   │                           │
│             │          │ │          │ │          │                           │
│             │ Backend  │ │ Backend  │ │ Backend  │                           │
│             │ Alerting │ │ Alerting │ │ Alerting │  (all nodes evaluate      │
│             │ Plugins  │ │ Plugins  │ │ Plugins  │   all alerts)             │
│             └────┬─────┘ └────┬─────┘ └────┬─────┘                           │
│                  │            │            │                                 │
│                  └────────────┼────────────┘                                 │
│                               │                                              │
│                    ┌──────────▼──────────┐                                    │
│                    │  Shared Database     │                                    │
│                    │  (MySQL or Postgres) │                                    │
│                    │                      │                                    │
│                    │  - Dashboards        │                                    │
│                    │  - Users & Orgs      │                                    │
│                    │  - Alert rules       │                                    │
│                    │  - Sessions          │                                    │
│                    │  - Annotations       │                                    │
│                    └─────────────────────┘                                    │
│                                                                              │
│  KEY REQUIREMENTS:                                                           │
│  ┌───────────────────────────────────────────────────────────────┐           │
│  │ ✓ Replace SQLite with MySQL/Postgres (shared DB)             │           │
│  │ ✓ All nodes point to same database                            │           │
│  │ ✓ Load balancer: no sticky sessions needed (sessions in DB)  │           │
│  │ ✓ SSL termination at load balancer level                      │           │
│  │ ✓ Shared root_url config across all nodes                     │           │
│  │ ✗ Alerting: NOT load-distributed (every node runs all rules) │           │
│  │ ✗ Image rendering: configure separately per node              │           │
│  └───────────────────────────────────────────────────────────────┘           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Full Production Stack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│            PRODUCTION OBSERVABILITY STACK — COMPLETE PICTURE                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │  APPLICATION TIER                                                    │     │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                      │     │
│  │  │Svc A │ │Svc B │ │Svc C │ │Svc D │ │Svc E │                      │     │
│  │  │(OTel)│ │(OTel)│ │(OTel)│ │(OTel)│ │(OTel)│                      │     │
│  │  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘                      │     │
│  └─────┼────────┼────────┼────────┼────────┼───────────────────────────┘     │
│        └────────┴────────┴────────┴────────┘                                │
│                          │                                                   │
│              ┌───────────▼───────────┐                                        │
│              │   COLLECTION TIER      │                                        │
│              │                         │                                        │
│              │  Grafana Alloy (agent)  │   ← DaemonSet on each K8s node       │
│              │  or OTel Collector      │                                        │
│              └──┬─────────┬────────┬──┘                                        │
│                 │         │        │                                           │
│        metrics  │  logs   │ traces │                                           │
│                 ▼         ▼        ▼                                           │
│  ┌──────────┐ ┌─────────┐ ┌──────────┐                                       │
│  │  Mimir   │ │  Loki   │ │  Tempo   │     ← STORAGE TIER                    │
│  │ (PromQL) │ │ (LogQL) │ │(TraceQL) │     All backed by Object Storage      │
│  └────┬─────┘ └────┬────┘ └────┬─────┘                                       │
│       └─────────────┴──────────┘                                              │
│                     │                                                         │
│        ┌────────────▼────────────┐                                            │
│        │  Grafana (HA cluster)   │  ← VISUALIZATION TIER                     │
│        │  3 nodes + shared DB    │                                            │
│        │  behind load balancer   │                                            │
│        └────────────┬────────────┘                                            │
│                     │                                                         │
│        ┌────────────▼────────────┐                                            │
│        │  Alertmanager           │  ← ALERTING TIER                          │
│        │  → PagerDuty            │                                            │
│        │  → Slack                │                                            │
│        │  → OpsGenie             │                                            │
│        └─────────────────────────┘                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Query Execution & Dashboard Rendering Internals

### PromQL Execution Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              PROMQL QUERY EXECUTION PIPELINE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Query: rate(http_requests_total{status="500"}[5m])                          │
│                                                                              │
│  STEP 1: PARSING → Abstract Syntax Tree (AST)                               │
│  ┌──────────────────────────────────────────────┐                            │
│  │            rate()                             │                            │
│  │              │                                │                            │
│  │        MatrixSelector                         │                            │
│  │         {metric: "http_requests_total",       │                            │
│  │          labels: {status="500"},              │                            │
│  │          range: 5m}                           │                            │
│  └──────────────────────────────────────────────┘                            │
│                                                                              │
│  STEP 2: PLANNING                                                            │
│  - Determine time range from dashboard panel settings                        │
│  - Calculate evaluation step (e.g., every 15s for 1h window)                 │
│  - Identify data sources to query                                            │
│                                                                              │
│  STEP 3: EXECUTION                                                           │
│  - Engine walks AST, calls Select() for each selector node                   │
│  - Fetches raw samples from TSDB (Prometheus/Mimir)                          │
│  - Applies function (rate = per-second increase over range)                  │
│                                                                              │
│  STEP 4: RESULT                                                              │
│  - Returns vector/matrix of (timestamp, value) pairs                         │
│  - Grafana converts to Data Frames (internal format)                         │
│  - Frontend renders as time-series graph                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Dashboard Rendering Architecture (Scenes Library)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│               GRAFANA SCENES — DASHBOARD ENGINE (2024+)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Dashboard JSON (stored in metadata DB)                                      │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────┐                         │
│  │  SCENES LIBRARY (Declarative React Framework)    │                         │
│  │                                                   │                         │
│  │  SceneApp                                         │                         │
│  │  └── ScenePage                                    │                         │
│  │      └── SceneFlexLayout                          │                         │
│  │          ├── VizPanel (time-series graph)          │                         │
│  │          │   ├── SceneQueryRunner (fetches data)  │                         │
│  │          │   ├── SceneDataTransformer (transform) │                         │
│  │          │   └── PanelRenderer (visualize)        │                         │
│  │          ├── VizPanel (stat panel)                │                         │
│  │          └── VizPanel (table)                     │                         │
│  │                                                   │                         │
│  │  Features enabled by Scenes:                      │                         │
│  │  ✓ Multiple time ranges per dashboard             │                         │
│  │  ✓ Nested variable scopes                         │                         │
│  │  ✓ Panel grouping & tabs                          │                         │
│  │  ✓ URL state management                           │                         │
│  │  ✓ Lazy loading of panels                         │                         │
│  └─────────────────────────────────────────────────┘                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Real-World Usage at Scale

### Uber — Corporate Network Observability

```
Uber built a cloud-native observability platform for global corporate network
monitoring across USC, EMEA, and APAC regions.

Stack: Prometheus + Thanos + Grafana + Telegraf + Kibana

Grafana role:
- Unified dashboards for network health across 3 regions
- Custom panels for BGP routing, SNMP metrics, DNS resolution
- Alerting for SLA breaches (packet loss > 0.1%, latency > 50ms)
```

### Airbnb — 100M Samples/Second

```
Airbnb migrated to an OpenTelemetry-based metrics stack:

- 100 million samples per second ingestion
- Grafana Mimir for Prometheus-compatible long-term storage
- Grafana for visualization across all engineering teams
- Migration from custom VictoriaMetrics agents to OTel Collector
```

### Pipedrive — 8 Million Active Series

```
- 500 active Grafana users
- 8 million active series in Mimir
- Replaced standalone Prometheus (which hit scaling limits)
- Mimir enabled horizontal scaling + long-term retention
```

### Common Patterns Across Large Deployments

```
┌──────────────────────────────────────────────────────────────────┐
│  PATTERN                          │  COMPANIES                   │
├───────────────────────────────────┼──────────────────────────────┤
│  Prometheus + Grafana             │  90%+ of K8s deployments     │
│  Mimir for long-term storage      │  Airbnb, Pipedrive, CERN    │
│  Loki replacing ELK               │  Cost-sensitive log users    │
│  Tempo replacing Jaeger           │  OTel-first organizations    │
│  GitOps dashboards (Jsonnet)      │  Spotify, GitLab, Wise       │
│  Grafana Cloud (managed)          │  Startups, mid-size orgs     │
│  Self-hosted + Thanos             │  Privacy/compliance-heavy    │
└───────────────────────────────────┴──────────────────────────────┘
```

---

## 12. When Grafana Failed — Production Incidents

### Incident 1: TLS Policy Cascade — 150-Minute Outage (Feb 2025)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Grafana Cloud 150-Minute Partial Outage                          │
│  DATE: February 18, 2025                                                     │
│  IMPACT: ~25% of services affected                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHAT HAPPENED:                                                              │
│                                                                              │
│  1. Engineer pushed a TLS policy config change to Crossplane                 │
│     (Kubernetes infrastructure-as-code tool)                                 │
│                                                                              │
│  2. The change UNINTENTIONALLY overrode default values:                      │
│     - Kubernetes Service type changed: LoadBalancer → ClusterIP              │
│     - This DESTROYED existing load balancers                                 │
│                                                                              │
│  3. Blast radius was NOT contained:                                          │
│     - Dev, Staging, AND Production changed simultaneously                    │
│     - Standard deployment process was BYPASSED                               │
│                                                                              │
│  Timeline:                                                                   │
│  ┌───────────────────────────────────────────────────────┐                   │
│  │ 14:00  Config change pushed                           │                   │
│  │ 14:02  Load balancers start getting destroyed         │                   │
│  │ 14:05  25% of services unreachable                    │                   │
│  │ 14:10  Alert fires, incident declared                 │                   │
│  │ 14:30  Root cause identified                          │                   │
│  │ 15:00  Code rolled back                               │                   │
│  │ 16:30  All K8s services recreated, full recovery      │                   │
│  └───────────────────────────────────────────────────────┘                   │
│                                                                              │
│  ROOT CAUSES:                                                                │
│  ✗ Insufficient testing of infrastructure-as-code changes                    │
│  ✗ No blast radius reduction (should have been dev → staging → prod)         │
│  ✗ Bypassed standard deployment pipeline                                     │
│  ✗ Crossplane defaults were not explicitly pinned                            │
│                                                                              │
│  LESSONS (interview gold):                                                   │
│  ✓ Infrastructure-as-code needs the SAME rigor as application deployments   │
│  ✓ Always pin defaults explicitly — never rely on implicit behavior          │
│  ✓ Canary/progressive rollout for infrastructure changes too                 │
│  ✓ Blast radius reduction is non-negotiable                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Incident 2: etcd Client — Stuck TCP Connection (March 2020)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Hosted Prometheus Partial Outage — London Region                  │
│  DATE: March 2020                                                            │
│  DURATION: ~12 minutes                                                       │
│  ROOT CAUSE: Stuck TCP connection after K8s upgrade                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHAT HAPPENED:                                                              │
│                                                                              │
│  K8s upgrade terminated the etcd leader node                                 │
│       │                                                                      │
│       ▼                                                                      │
│  etcd elected new leader, but Cortex distributors held a stale              │
│  TCP connection to the OLD leader                                            │
│       │                                                                      │
│       ▼                                                                      │
│  gRPC keepalive probes were IMPROPERLY CONFIGURED                            │
│  → Connection appeared alive but was actually dead                           │
│       │                                                                      │
│       ▼                                                                      │
│  Distributors couldn't discover new ring state → writes failed               │
│                                                                              │
│  FIX: Restarted affected distributors. No data loss.                         │
│                                                                              │
│  LESSON: Always configure gRPC keepalive properly in distributed systems.   │
│  Default TCP keepalive (2+ hours) is almost NEVER correct for production.    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Incident 3: Limit Enforcement Bug — Cascading OOM (March 2021)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Hosted Prometheus ~2-Hour Outage — us-central                     │
│  DATE: March 2021                                                            │
│  ROOT CAUSE: Missing rate limits + cascading OOM                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  TIMELINE:                                                                   │
│                                                                              │
│  1. New large customer onboarded                                             │
│          │                                                                   │
│  2. Bug in limit enforcement code let them exceed cluster capacity           │
│          │                                                                   │
│  3. Ingesters OOM'd under load                                               │
│          │                                                                   │
│  4. Remaining ingesters took on redistributed load → more OOMs               │
│          │                                                                   │
│  5. Cascading failure: entire us-central cluster down for ~2 hours           │
│                                                                              │
│  PATTERN: Classic "thundering herd" / noisy neighbor problem                 │
│                                                                              │
│  FIXES:                                                                      │
│  ✓ Stricter per-tenant rate limits enforced at distributor level              │
│  ✓ Cluster scaled up with headroom                                           │
│  ✓ Limit enforcement code audited and hardened                               │
│  ✓ Added per-tenant resource isolation                                       │
│                                                                              │
│  INTERVIEW INSIGHT: Multi-tenant systems MUST enforce limits at ingestion    │
│  — not at query time. By the time you query, the damage is already done.    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. When NOT to Use Grafana

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   WHEN NOT TO USE GRAFANA                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO                         │  USE INSTEAD                             │
│  ─────────────────────────────────┼──────────────────────────────────────── │
│                                    │                                         │
│  Full-text log search is critical  │  Elasticsearch/OpenSearch               │
│  (e.g., searching inside log       │  (inverted index on every word)         │
│   content, not just labels)        │                                         │
│                                    │                                         │
│  Business intelligence / analytics │  Looker, Tableau, Metabase, Superset    │
│  (pivot tables, drill-down,        │  (designed for BI, not observability)   │
│   non-time-series data)            │                                         │
│                                    │                                         │
│  APM with auto-instrumentation     │  Datadog, New Relic, Dynatrace          │
│  (zero-code tracing, code-level    │  (commercial APM with agents)           │
│   profiling, runtime analysis)     │                                         │
│                                    │                                         │
│  Zero operational overhead         │  Datadog, Grafana Cloud (managed)       │
│  (no team to manage infra)         │  (fully managed SaaS)                   │
│                                    │                                         │
│  Real-time streaming dashboards    │  Apache Druid + Superset,               │
│  (sub-second updates, millions     │  ClickHouse + custom UI                 │
│   of events/second displayed)      │  (Grafana has refresh limits)           │
│                                    │                                         │
│  Security/SIEM use case            │  Splunk, Elastic SIEM, Sentinel         │
│  (threat detection, compliance,    │  (built for security workflows)         │
│   forensic log analysis)           │                                         │
│                                    │                                         │
│  Embedded analytics in your product│  Custom charts (D3.js, Chart.js),       │
│  (white-label dashboards for       │  Grafana requires licensing for embed   │
│   end users)                       │                                         │
│                                    │                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 14. Scenarios Where Grafana Could Not Cope

### High-Cardinality Explosion

```
PROBLEM:
  Your team creates a dashboard with a query like:
  rate(http_requests_total{path=~".*"}[5m])

  With 50,000 unique API paths × 200 instances = 10 million series

WHAT HAPPENS:
  ┌─────────────────────────────────────────────────────┐
  │ Browser: Tries to render 10M data points → freezes  │
  │ Grafana: Query proxy timeout after 30s              │
  │ Prometheus: OOM or extremely slow query             │
  │ Result: Dashboard unusable, other dashboards slow   │
  └─────────────────────────────────────────────────────┘

SOLUTION:
  - Use recording rules to pre-aggregate
  - Limit label cardinality at instrumentation
  - Use topk() to show only top N series
  - Downsample historical data
```

### SQLite Under Write Pressure

```
PROBLEM:
  - Default Grafana uses SQLite for metadata
  - With unified alerting generating thousands of alert instances
  - Each evaluation writes state to SQLite
  - SQLite has a single-writer lock

WHAT HAPPENS:
  ┌──────────────────────────────────────────────────────┐
  │ Alert evaluations queue up behind SQLite write lock  │
  │ Dashboard saves timeout                              │
  │ API responses slow to 10+ seconds                    │
  │ Entire Grafana instance becomes unresponsive         │
  └──────────────────────────────────────────────────────┘

SOLUTION:
  - Switch to MySQL or PostgreSQL for production
  - NEVER use SQLite with more than ~100 alert rules
```

### Dashboard-as-Monolith

```
PROBLEM:
  Single dashboard with 50+ panels, each querying different data sources
  with complex PromQL and 5-minute refresh interval.

WHAT HAPPENS:
  ┌──────────────────────────────────────────────────────┐
  │ 50 panels × 3 queries each = 150 concurrent queries │
  │ Each query hits Prometheus with 1-hour range         │
  │ Grafana backend overwhelmed proxying all queries     │
  │ Browser JS heap exceeds 2GB → tab crashes            │
  │ Other users' dashboards also slow down               │
  └──────────────────────────────────────────────────────┘

SOLUTION:
  - Split into multiple focused dashboards
  - Use drill-down links instead of showing everything
  - Increase refresh interval (15s → 1m for overview dashboards)
  - Use dashboard lazy loading (Scenes library feature)
```

### Multi-Tenant Noisy Neighbor

```
PROBLEM:
  Shared Grafana instance. One team creates a dashboard with a
  "SELECT * FROM metrics WHERE timestamp > now() - 30d" query
  against a slow data source.

WHAT HAPPENS:
  ┌──────────────────────────────────────────────────────┐
  │ Query consumes all available backend connections     │
  │ Other teams' queries queue and timeout               │
  │ Alerting evaluations delayed → missed alerts         │
  │ No per-user query quotas in OSS Grafana              │
  └──────────────────────────────────────────────────────┘

SOLUTION:
  - Configure query timeout limits per data source
  - Use Grafana Enterprise for per-org query concurrency limits
  - Set max_data_points and max_series in data source config
  - Implement query caching with Grafana query caching plugin
```

---

## 15. Grafana vs Datadog vs New Relic

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              GRAFANA vs DATADOG vs NEW RELIC — COMPARISON                     │
├──────────────────┬──────────────────┬────────────────┬──────────────────────┤
│                  │ GRAFANA (OSS)    │ DATADOG         │ NEW RELIC           │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Model            │ Open-source      │ SaaS only      │ SaaS + open agents  │
│                  │ (AGPLv3)         │                │                      │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Self-host?       │ ✓ Yes            │ ✗ No           │ Partial              │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Metrics store    │ Mimir/Prometheus │ Custom TSDB    │ NRDB (custom)        │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Log store        │ Loki             │ Custom         │ Built-in             │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Tracing          │ Tempo            │ Built-in APM   │ Built-in APM         │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Integrations     │ 150+ plugins     │ 850+           │ 600+                 │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ APM              │ Requires OTel    │ Auto-instrument│ Auto-instrument      │
│                  │ instrumentation  │ agents         │ agents               │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Cost at scale    │ $ (infra cost)   │ $$$$           │ $$ (per-GB)          │
│ (1000 hosts)     │                  │ (per-host +    │                      │
│                  │                  │  per-metric)   │                      │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Ops overhead     │ HIGH             │ ZERO           │ LOW                  │
│                  │ (manage cluster) │ (fully managed)│                      │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Setup time       │ Days-Weeks       │ Minutes-Hours  │ Hours                │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Query language   │ PromQL, LogQL,   │ Proprietary    │ NRQL                 │
│                  │ TraceQL          │                │                      │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Data ownership   │ ✓ Full control   │ ✗ Vendor-locked│ Partial              │
├──────────────────┼──────────────────┼────────────────┼──────────────────────┤
│ Best for         │ K8s-native orgs, │ Fast setup,    │ Mid-size teams,      │
│                  │ cost-conscious,  │ comprehensive  │ predictable billing, │
│                  │ data sovereignty │ features       │ full-stack APM       │
└──────────────────┴──────────────────┴────────────────┴──────────────────────┘
```

### Decision Framework

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              WHICH TO CHOOSE? — DECISION TREE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  "Do you have a platform engineering team?"                                  │
│       │                                                                      │
│       ├── NO → "Is budget a concern?"                                        │
│       │         ├── YES → Grafana Cloud (managed LGTM)                       │
│       │         └── NO  → Datadog (fastest time to value)                    │
│       │                                                                      │
│       └── YES → "Do you need data sovereignty / self-hosting?"              │
│                  ├── YES → Self-hosted Grafana + LGTM stack                  │
│                  └── NO  → "Scale?"                                          │
│                            ├── < 1000 hosts → New Relic (simple pricing)     │
│                            └── > 1000 hosts → Grafana Cloud or self-hosted   │
│                                               (Datadog cost explodes here)   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Designing a Monitoring System (Interview Pattern)

This is the pattern Alex Xu (ByteByteGo) recommends. Knowing Grafana helps you nail the "Visualization + Alerting" components.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│          DESIGN A METRICS MONITORING & ALERTING SYSTEM                        │
│          (Alex Xu — System Design Interview Vol. 2)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  REQUIREMENTS:                                                               │
│  - 1,000 server pools, 100 machines per pool = 100K hosts                    │
│  - ~10M metrics, 10K QPS writes                                              │
│  - 7-day raw retention → 30-day 1-min rollup → 1-year 1-hour rollup         │
│  - Alerts via email, PagerDuty, webhooks                                     │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │  ARCHITECTURE                                                         │   │
│  │                                                                       │   │
│  │  ┌──────────────┐                                                     │   │
│  │  │ Metrics       │  Servers, DBs, queues, custom app metrics          │   │
│  │  │ Sources       │                                                     │   │
│  │  └──────┬───────┘                                                     │   │
│  │         │  push or pull (Prometheus-style scrape)                      │   │
│  │         ▼                                                              │   │
│  │  ┌──────────────┐                                                     │   │
│  │  │ Metrics       │  Grafana Alloy, OTel Collector, Telegraf           │   │
│  │  │ Collector     │  Batches + compresses before sending                │   │
│  │  └──────┬───────┘                                                     │   │
│  │         │                                                              │   │
│  │         ▼                                                              │   │
│  │  ┌──────────────┐                                                     │   │
│  │  │  Kafka        │  Decouples collection from storage                  │   │
│  │  │  (buffer)     │  Handles burst traffic                              │   │
│  │  └──────┬───────┘                                                     │   │
│  │         │                                                              │   │
│  │    ┌────┴─────┐                                                        │   │
│  │    ▼          ▼                                                        │   │
│  │  ┌──────┐  ┌──────────┐                                                │   │
│  │  │Stream│  │  TSDB     │  Mimir, InfluxDB, TimescaleDB                │   │
│  │  │Proc. │  │ (storage) │  Block format on Object Storage              │   │
│  │  │(Flink│  └─────┬────┘                                                │   │
│  │  │Storm)│        │                                                     │   │
│  │  └──┬───┘        │                                                     │   │
│  │     │            │                                                     │   │
│  │     ▼            ▼                                                     │   │
│  │  ┌──────┐  ┌──────────────┐                                            │   │
│  │  │Alert │  │   GRAFANA     │  Query + Visualize + Explore              │   │
│  │  │Rules │  │  (dashboards) │                                            │   │
│  │  └──┬───┘  └──────────────┘                                            │   │
│  │     │                                                                  │   │
│  │     ▼                                                                  │   │
│  │  ┌──────────────┐                                                     │   │
│  │  │ Alertmanager  │  Grouping, routing, dedup                           │   │
│  │  └──────┬───────┘                                                     │   │
│  │         │                                                              │   │
│  │    ┌────┴────┬──────────┐                                              │   │
│  │    ▼         ▼          ▼                                              │   │
│  │  Email   PagerDuty   Webhooks                                          │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  DATA RETENTION STRATEGY:                                                    │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │ RAW data (all samples)        → 7 days    → hot storage            │      │
│  │ 1-minute rollup (avg/max/min) → 30 days   → warm storage           │      │
│  │ 1-hour rollup                  → 1 year    → cold/object storage   │      │
│  │ Beyond 1 year                  → deleted or archived to S3 Glacier │      │
│  └────────────────────────────────────────────────────────────────────┘      │
│                                                                              │
│  BACK-OF-ENVELOPE:                                                           │
│  - 10M metrics × 1 sample/15s = ~667K writes/sec peak                       │
│  - Each sample ~16 bytes (timestamp 8B + value 8B)                           │
│  - Daily raw: 667K × 86400 × 16B ≈ 923 GB/day                               │
│  - 7-day raw retention: ~6.5 TB                                              │
│  - With compression (10:1 typical): ~650 GB hot storage                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 17. Anti-Patterns That Kill Grafana

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              ANTI-PATTERNS THAT KILL GRAFANA DEPLOYMENTS                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. CARDINALITY BOMB                                                         │
│     Using user_id, request_id, or IP as metric labels                        │
│     → millions of unique time series                                         │
│     → Prometheus/Mimir OOM                                                   │
│     → Grafana query timeout                                                  │
│     FIX: Labels should have bounded, low cardinality (< 1000 values)         │
│                                                                              │
│  2. DASHBOARD SPRAWL                                                         │
│     500+ dashboards, no ownership, no naming convention                      │
│     → Nobody knows which dashboard is correct                                │
│     → Stale dashboards with broken queries                                   │
│     → Alert fatigue from orphaned alert rules                                │
│     FIX: GitOps (dashboards-as-code), ownership labels, regular pruning      │
│                                                                              │
│  3. QUERY WITHOUT LIMITS                                                     │
│     {__name__=~".+"} (matches ALL metrics)                                   │
│     → Fetches entire TSDB into memory                                        │
│     → Kills Prometheus and Grafana backend                                   │
│     FIX: Always scope queries. Set max_series and query_timeout.             │
│                                                                              │
│  4. ALERTING ON RAW METRICS                                                  │
│     Alert: http_requests_total > 1000                                        │
│     → Counter always increases, alert always fires                           │
│     → Alert fatigue → real alerts ignored                                    │
│     FIX: Alert on rate(), increase(), or ratio. Use for-duration.            │
│                                                                              │
│  5. SINGLE GRAFANA INSTANCE IN PROD                                          │
│     No HA, SQLite database, no backups                                       │
│     → Node goes down = no dashboards = no visibility during incident         │
│     → "We can't see the monitoring during the outage"                        │
│     FIX: HA setup (3 nodes + shared DB + LB). Backup dashboard JSON.         │
│                                                                              │
│  6. NO RECORDING RULES                                                       │
│     Every dashboard panel runs a complex aggregation query live              │
│     → 50 users × 20 panels × complex PromQL = 1000 heavy queries            │
│     → Prometheus CPU at 100%                                                 │
│     FIX: Pre-compute common aggregations with recording rules.               │
│                                                                              │
│  7. IGNORING RETENTION POLICY                                                │
│     Storing raw metrics forever                                              │
│     → Storage grows unbounded → disk full → data loss                        │
│     FIX: Configure retention (--storage.tsdb.retention.time)                 │
│          Use downsampling for historical data                                │
│                                                                              │
│  8. MIXING OBSERVABILITY AND BUSINESS METRICS                                │
│     Using Prometheus/Grafana for business KPIs, revenue dashboards           │
│     → Time-series DBs are bad at non-time-series aggregations                │
│     → PromQL can't do joins, pivots, or complex analytics                    │
│     FIX: Use Looker/Metabase/Superset for business metrics.                  │
│          Keep Grafana for infrastructure + application observability.         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 18. Performance Tuning Cheat Sheet

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              GRAFANA + PROMETHEUS PERFORMANCE TUNING                          │
├──────────────────────────┬──────────────────────────────────────────────────┤
│ AREA                     │ TUNING                                           │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Dashboard refresh        │ Overview: 1m | Detail: 15s | Alerts: 30s-1m     │
│ interval                 │ NEVER use 5s for large dashboards                │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Max data points          │ Set to panel width in pixels (~1000)             │
│                          │ Prevents fetching millions of raw samples        │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Query timeout            │ 30s default. Lower to 10s for dashboards.       │
│                          │ Prevents slow queries blocking the backend.     │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Recording rules          │ Pre-compute: job-level aggregations,            │
│                          │ SLO burn rates, top-N queries                   │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Query caching            │ Enable Grafana query caching (Enterprise)       │
│                          │ or use Trickster (OSS caching proxy)            │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Prometheus               │ --query.max-concurrency=20 (default)            │
│ query limits             │ --query.max-samples=50000000                    │
│                          │ --storage.tsdb.retention.time=15d               │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Dashboard panels         │ Max 20-25 panels per dashboard                  │
│                          │ Use drill-down links for detail views           │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Grafana database         │ PROD: MySQL or Postgres (never SQLite)          │
│                          │ Connection pool: 50-100 connections              │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Browser performance      │ Lazy load panels below fold (Scenes)            │
│                          │ Limit time range (avoid 30d on graph panels)    │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Alert evaluation         │ group_wait: 30s (batch related alerts)          │
│                          │ Evaluate every: 1m (not 10s for non-critical)   │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ Long-term storage        │ Mimir or Thanos for retention > 30 days         │
│                          │ Object storage (S3) for cost efficiency         │
└──────────────────────────┴──────────────────────────────────────────────────┘
```

---

## 19. Interview Questions — Medium

### Q1: How does Grafana differ from Prometheus? Why do we need both?

```
ANSWER FRAMEWORK:

Prometheus = COLLECTS + STORES + QUERIES metrics
Grafana    = VISUALIZES + ALERTS + CORRELATES data from ANY source

┌──────────────────┬──────────────────────┬─────────────────────────┐
│                  │ Prometheus            │ Grafana                  │
├──────────────────┼──────────────────────┼─────────────────────────┤
│ Role             │ TSDB + scraper        │ Dashboard + alerting     │
│ Stores data?     │ Yes (local TSDB)      │ No (metadata only)       │
│ Query language   │ PromQL                │ Delegates to data source │
│ Data sources     │ Self only             │ 150+ (Prometheus, SQL...) │
│ Visualization    │ Basic (PromUI)        │ Rich (50+ panel types)   │
│ Multi-source     │ No                    │ Yes (single dashboard)   │
│ HA built-in      │ No (need Thanos/Mimir)│ Yes (shared DB + LB)    │
└──────────────────┴──────────────────────┴─────────────────────────┘

WHY BOTH: Prometheus is excellent at scraping and storing metrics.
Grafana is excellent at querying multiple data sources and presenting
a unified view. Together they form the standard monitoring stack.
```

### Q2: Explain the Grafana alerting pipeline. How does it prevent alert storms?

```
ANSWER:

1. Alert Rule evaluated on schedule (e.g., every 1m)
2. Query executes against data source
3. Condition checked (e.g., error_rate > 5%)
4. If true for `for` duration → alert FIRES
5. Alert sent to Alertmanager

Alertmanager prevents storms via:

GROUPING:    100 pod alerts → 1 "cluster unhealthy" notification
             (group_by: [cluster, namespace])

INHIBITION:  "datacenter_down" firing → suppress all child alerts
             (reduces 1000 alerts to 1)

SILENCING:   During maintenance window → mute matching alerts

ROUTING:     severity=critical → PagerDuty (immediate)
             severity=warning  → Slack (batched every 5m)
```

### Q3: How would you set up Grafana for high availability?

```
ANSWER:

1. Replace SQLite → shared MySQL/PostgreSQL
2. Deploy 3+ Grafana nodes (identical config)
3. Put behind load balancer (no sticky sessions needed)
4. SSL termination at LB
5. All nodes share same root_url

Alerting caveat: ALL nodes evaluate ALL alerts.
Dedup handled by Alertmanager gossip protocol.

For the data sources (Prometheus/Mimir/Loki):
- Those have their OWN HA strategies (federation, replication)
- Grafana HA only covers the visualization/UI layer
```

### Q4: What is label cardinality and why does it matter?

```
ANSWER:

Cardinality = number of unique time series = unique label combinations

Example:
  http_requests_total{method="GET", status="200", endpoint="/api/users"}

  Labels:  method(4) × status(5) × endpoint(100) = 2,000 series ← OK
  
  But add: user_id(1M) → 2,000 × 1M = 2 BILLION series ← DISASTER

Why it matters:
- Prometheus stores each series separately in TSDB
- High cardinality → massive memory usage → OOM
- Query performance degrades linearly with series count
- Grafana dashboard becomes unresponsive

Rule of thumb: Any label with >1000 unique values is suspicious.
Never use: user_id, request_id, email, IP as metric labels.
```

### Q5: Explain Loki's "index only labels, not content" philosophy.

```
ANSWER:

Elasticsearch indexes EVERY WORD in every log line.
  → Full inverted index → fast arbitrary search
  → Cost: massive RAM and storage for index

Loki indexes ONLY labels (app, env, level, namespace).
  → Log content stored as compressed chunks in S3
  → Search by labels (fast, uses index)
  → Search within content (slow, grep over chunks)

Tradeoff:
  ┌──────────────┬──────────────────┬──────────────────┐
  │              │ Elasticsearch     │ Loki              │
  ├──────────────┼──────────────────┼──────────────────┤
  │ Index size   │ 10-20% of data   │ < 1% of data      │
  │ Storage cost │ $$$$             │ $ (object storage) │
  │ Full-text    │ Fast (indexed)   │ Slow (brute force) │
  │ Label search │ Fast             │ Fast               │
  │ Best for     │ Complex search   │ Cost-efficient logs │
  └──────────────┴──────────────────┴──────────────────┘

When to choose Loki: You already use Prometheus labels and most log
queries start with a service/label filter (which is the 90% case).
```

### Q6: How would you handle metric retention and downsampling?

```
ANSWER (tiered retention strategy):

Raw data (15s resolution)  → 7-15 days  (hot, local SSD or TSDB)
1-minute rollup            → 30-90 days (warm, Mimir/Thanos on S3)
1-hour rollup              → 1-3 years  (cold, S3 Glacier)

Implementation:
1. Prometheus recording rules compute rollups:
   record: job:http_requests:rate5m
   expr: sum(rate(http_requests_total[5m])) by (job)

2. Thanos/Mimir compactor performs automatic downsampling:
   5m resolution blocks for data > 40 hours
   1h resolution blocks for data > 10 days

3. Object storage lifecycle policies:
   S3 Standard → S3 IA (30 days) → S3 Glacier (1 year)

Back-of-envelope:
  10M series × 4 samples/min × 16B/sample = ~1 TB/day raw
  With 10:1 compression → ~100 GB/day stored
  7-day retention → ~700 GB hot storage
```

### Q7: What is the difference between push and pull models for metrics collection?

```
PULL (Prometheus default):
  Prometheus scrapes /metrics endpoint on each target every 15s
  
  ✓ Prometheus controls the load (no thundering herd)
  ✓ Easy to detect if a target is down (scrape fails)
  ✓ No client-side buffering needed
  ✗ Targets must be reachable (hard behind NAT/firewall)
  ✗ Short-lived jobs may miss scrape windows

PUSH (Datadog, StatsD, OTel default):
  Applications push metrics to a collector/gateway
  
  ✓ Works behind NAT/firewall
  ✓ Better for short-lived batch jobs
  ✓ Client controls what/when to send
  ✗ Can overwhelm collector during bursts
  ✗ Harder to detect if sender is down vs just quiet

Grafana ecosystem supports BOTH:
  Pull: Prometheus scrape → Grafana reads from Prometheus
  Push: OTel SDK → Grafana Alloy → Mimir (remote_write)
```

### Q8: How does cross-signal correlation work in Grafana?

```
ANSWER:

The key enabler is the TRACE ID propagated across all three signals:

1. Application emits:
   - Metric with EXEMPLAR (trace_id embedded in metric sample)
   - Log line with trace_id field
   - Trace span with the same trace_id

2. In Grafana:
   Metric spike → click exemplar → opens Tempo trace
   Log line → click trace_id → opens Tempo trace
   Trace span → link to Loki logs for that service/time window

3. Requirements:
   - Applications must propagate W3C TraceContext headers
   - Metrics must include exemplars (Prometheus 2.27+)
   - Logs must include trace_id in structured fields
   - Grafana data sources must be configured with cross-links

This is the single biggest advantage of the LGTM stack over
separate tools — unified correlation within one UI.
```

---

## 20. Interview Questions — Hard

### Q1: Design a monitoring system for a 100K-host infrastructure. Walk through every component.

```
ANSWER (use the architecture from Section 16):

SCALE: 100K hosts × 100 metrics each = 10M active series

COLLECTION TIER:
  - Grafana Alloy (DaemonSet) on each K8s node
  - Scrapes Prometheus metrics + receives OTLP from apps
  - Batches + compresses + forwards

INGESTION BUFFER:
  - Kafka cluster (decouple collection from storage)
  - 10K QPS writes, 3x replication, 7-day retention
  - Partitioned by metric name hash

STORAGE TIER:
  - Mimir (horizontally scaled, S3 backend)
  - Tiered retention: 7d raw → 30d 1m → 1y 1h
  - Compactor runs downsampling
  
QUERY TIER:
  - Mimir query-frontend (splits + caches + limits)
  - Memcached for query result caching

VISUALIZATION:
  - Grafana HA (3 nodes + PostgreSQL + load balancer)
  - GitOps dashboards (Jsonnet/Grafonnet → Git → provisioning)

ALERTING:
  - Grafana Unified Alerting → Alertmanager
  - Routing: critical → PagerDuty, warning → Slack
  - Recording rules for common SLO calculations

COST OPTIMIZATION:
  - Loki (not ELK) for logs → 10x cheaper storage
  - Object storage for everything > 7 days
  - Drop high-cardinality labels at collection time
```

### Q2: Your Grafana dashboards are slow. Walk through your debugging approach systematically.

```
ANSWER:

STEP 1: Identify the bottleneck layer

  Check panel performance indicators:
  Q: 5000ms ← Query is slow (data source problem)
  T: 50ms   ← Transform is fine
  R: 100ms  ← Render is fine

  If Q is high → data source / query problem
  If R is high → too many data points / browser problem
  If everything is slow → Grafana backend overloaded

STEP 2: If query is slow

  a. Check Prometheus/Mimir:
     - promql_engine_query_duration_seconds histogram
     - Is it a single query or all queries?
     
  b. Analyze the query:
     - High cardinality? (check series count)
     - Large time range without downsampling?
     - Missing recording rule for common aggregation?
     
  c. Solutions:
     - Add recording rules for repeated complex queries
     - Reduce time range or increase step interval
     - Add topk() to limit series
     - Check if max_data_points is set on panel

STEP 3: If Grafana backend is overloaded

  a. Check concurrent users/queries:
     - grafana_http_request_duration_seconds
     - grafana_datasource_request_total
     
  b. Solutions:
     - Enable query caching
     - Increase query timeout (kill slow queries faster)
     - Scale horizontally (add Grafana nodes)
     - Set per-data-source concurrent query limits

STEP 4: If browser is slow

  a. Check:
     - Number of panels (>30 = problem)
     - Data points per panel (>10K = problem)
     - Number of variables with "All" selected
     
  b. Solutions:
     - Split dashboard, use drill-down links
     - Enable lazy loading
     - Limit max data points per panel
     - Use stat/gauge instead of time-series for overview
```

### Q3: How would you implement multi-tenancy in a Grafana observability stack?

```
ANSWER:

LAYER 1: Grafana Multi-Tenancy
  ┌────────────────────────────────────────────────────────────┐
  │ Organizations: Logical isolation within single Grafana      │
  │ - Each org has own dashboards, data sources, users         │
  │ - Org-level RBAC (Viewer, Editor, Admin)                   │
  │ - Cannot see other org's dashboards                        │
  │                                                             │
  │ Limitations:                                                │
  │ - Shared Grafana process (noisy neighbor risk)              │
  │ - No per-org query limits in OSS                            │
  │ - Enterprise needed for per-org data source permissions     │
  └────────────────────────────────────────────────────────────┘

LAYER 2: Mimir/Loki/Tempo Multi-Tenancy
  ┌────────────────────────────────────────────────────────────┐
  │ Tenant ID: X-Scope-OrgID HTTP header                       │
  │                                                             │
  │ WRITE PATH:                                                 │
  │ Distributor enforces per-tenant limits:                     │
  │ - Max series per tenant                                     │
  │ - Max samples per second                                    │
  │ - Max label count / label length                            │
  │                                                             │
  │ STORAGE:                                                    │
  │ Blocks prefixed by tenant ID in object storage              │
  │ tenant-a/blocks/...                                         │
  │ tenant-b/blocks/...                                         │
  │                                                             │
  │ READ PATH:                                                  │
  │ Querier only reads blocks for the requesting tenant         │
  │ Query limits enforced per-tenant:                           │
  │ - Max query range                                           │
  │ - Max series per query                                      │
  │ - Max query concurrency                                     │
  └────────────────────────────────────────────────────────────┘

LAYER 3: Network Isolation
  ┌────────────────────────────────────────────────────────────┐
  │ Option A: Namespace-level isolation in K8s                  │
  │ Option B: Separate Grafana instances per tenant (expensive) │
  │ Option C: Single Grafana + org-level data sources           │
  │           pointing to tenant-specific Mimir/Loki endpoints  │
  └────────────────────────────────────────────────────────────┘

CRITICAL: Multi-tenancy in a monitoring stack is about
PREVENTING one tenant from affecting another's:
  1. Ingestion (rate limits at distributor)
  2. Query performance (per-tenant query limits)
  3. Storage (separate prefixes in object store)
  4. Visibility (RBAC + org isolation)
```

### Q4: Explain the Mimir ingest-storage architecture. Why did Grafana abandon the classic ingester model?

```
ANSWER:

CLASSIC MODEL PROBLEM:
  ┌──────────────────────────────────────────────────────────┐
  │ Ingester nodes served BOTH reads AND writes              │
  │                                                          │
  │ Write: Receive samples → WAL → in-memory TSDB → flush   │
  │ Read:  Querier asks ingester for recent data             │
  │                                                          │
  │ FAILURE MODE:                                            │
  │ 1. Heavy query hits ingester                             │
  │ 2. Ingester's memory spikes                              │
  │ 3. GC pauses delay write acknowledgments                 │
  │ 4. Upstream (distributor) times out                      │
  │ 5. Redistributes writes to other ingesters               │
  │ 6. OTHER ingesters now overloaded → CASCADING FAILURE    │
  │                                                          │
  │ The read path POISONED the write path because they       │
  │ shared the same process and memory space.                │
  └──────────────────────────────────────────────────────────┘

INGEST STORAGE MODEL:
  ┌──────────────────────────────────────────────────────────┐
  │ Kafka sits between writes and storage                    │
  │                                                          │
  │ Write: Distributor → Kafka (ACK'd immediately)           │
  │ Storage: Block Builder consumes Kafka → S3 blocks        │
  │ Read: Querier reads from Kafka (recent) + S3 (old)       │
  │                                                          │
  │ BENEFITS:                                                │
  │ 1. Writes never blocked by reads (fully decoupled)       │
  │ 2. No stateful ingesters (simpler operations)            │
  │ 3. No RF=3 replication on ingesters (Kafka handles it)   │
  │ 4. Block builders can be scaled independently            │
  │ 5. Lower cost (no 3x persistent volume replication)      │
  │                                                          │
  │ This is the same architecture Tempo v3 adopted.          │
  └──────────────────────────────────────────────────────────┘
```

### Q5: How would you implement SLO monitoring with Grafana?

```
ANSWER:

SLO = Service Level Objective
  Example: "99.9% of requests complete in < 500ms over 30 days"

STEP 1: Define SLIs (Service Level Indicators)
  Availability SLI:
    1 - (sum(rate(http_errors_total[5m])) / sum(rate(http_requests_total[5m])))
  
  Latency SLI:
    sum(rate(http_request_duration_bucket{le="0.5"}[5m]))
    / sum(rate(http_request_duration_count[5m]))

STEP 2: Recording Rules (pre-compute)
  - record: slo:availability:ratio5m
    expr: <availability SLI above>
  
  - record: slo:latency_good:ratio5m
    expr: <latency SLI above>

STEP 3: Error Budget Calculation
  Allowed errors in 30 days at 99.9% SLO:
    30 days × 24h × 60m = 43,200 minutes
    Error budget = 43,200 × 0.001 = 43.2 minutes

  Remaining budget:
    error_budget_remaining = 1 - (errors_in_window / allowed_errors)

STEP 4: Multi-Window Burn Rate Alerting
  ┌──────────────────────────────────────────────────────────────┐
  │ BURN RATE = actual error rate / SLO error rate                │
  │                                                               │
  │ Alert if:                                                     │
  │   burn_rate(1h) > 14.4 AND burn_rate(5m) > 14.4 → PAGE      │
  │   burn_rate(6h) > 6    AND burn_rate(30m) > 6   → TICKET     │
  │   burn_rate(3d) > 1    AND burn_rate(6h) > 1    → LOG        │
  │                                                               │
  │ Two windows prevent:                                          │
  │ - Long window only: slow to detect sudden spikes              │
  │ - Short window only: too many false positives                 │
  │                                                               │
  │ This is Google's recommended approach from the SRE Workbook.  │
  └──────────────────────────────────────────────────────────────┘

STEP 5: Grafana Dashboard
  - SLO burn rate gauge (green/yellow/red)
  - Error budget remaining (percentage + time)
  - 30-day rolling SLI graph
  - Alert history annotation overlay
```

### Q6: Your monitoring system is the first thing that goes down during an incident. How do you make it resilient?

```
ANSWER (meta-monitoring / monitoring the monitoring):

PRINCIPLE: Your monitoring stack should be MORE reliable than
the systems it monitors. Target: 99.99% availability.

STRATEGY 1: Self-Monitoring
  ┌──────────────────────────────────────────────────────────────┐
  │ Grafana monitors itself:                                      │
  │ - grafana_http_request_duration_seconds                       │
  │ - grafana_alerting_rule_evaluations_total                     │
  │ - grafana_datasource_request_duration_seconds                 │
  │                                                               │
  │ Prometheus monitors itself:                                   │
  │ - prometheus_tsdb_head_series (cardinality growth)             │
  │ - prometheus_target_scrape_pool_exceeded_target_limit          │
  │ - process_resident_memory_bytes (OOM risk)                    │
  └──────────────────────────────────────────────────────────────┘

STRATEGY 2: Separate Failure Domains
  ┌──────────────────────────────────────────────────────────────┐
  │ Application cluster ≠ Monitoring cluster                      │
  │                                                               │
  │ Run Grafana + Mimir + Loki in a SEPARATE K8s cluster         │
  │ or SEPARATE cloud account (different blast radius)            │
  │                                                               │
  │ If app cluster goes down, monitoring still works              │
  │ If monitoring cluster goes down, apps still work              │
  └──────────────────────────────────────────────────────────────┘

STRATEGY 3: Dead Man's Switch
  ┌──────────────────────────────────────────────────────────────┐
  │ A "dead man's switch" alert that ALWAYS fires.                │
  │ If you STOP receiving it → monitoring is broken.              │
  │                                                               │
  │ Alertmanager sends "heartbeat" to external service            │
  │ (e.g., Healthchecks.io, PagerDuty heartbeat)                 │
  │ If heartbeat stops → external service pages on-call           │
  └──────────────────────────────────────────────────────────────┘

STRATEGY 4: Multi-Region / Multi-Cloud
  ┌──────────────────────────────────────────────────────────────┐
  │ Primary monitoring: Self-hosted LGTM in us-east-1            │
  │ Secondary (canary): Grafana Cloud (different provider)        │
  │                                                               │
  │ Both monitor the same targets                                 │
  │ If primary fails, secondary still has visibility              │
  └──────────────────────────────────────────────────────────────┘

STRATEGY 5: Graceful Degradation
  - Prometheus with local TSDB works WITHOUT network
  - Grafana falls back to cached dashboard JSON
  - Alert rules continue evaluating even if UI is down
  - PagerDuty has its own redundancy (alerts still delivered)
```

### Q7: Compare Thanos vs Mimir for Prometheus long-term storage. When would you pick each?

```
ANSWER:

┌──────────────────┬──────────────────────────┬──────────────────────────┐
│                  │ THANOS                    │ MIMIR                    │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Architecture     │ Sidecar on each Prom      │ Centralized write path   │
│                  │ instance + Store Gateway   │ (remote_write)           │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Data flow        │ Prometheus → local TSDB   │ Prometheus → remote_write│
│                  │ → Sidecar uploads to S3   │ → Mimir → S3             │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Global query     │ Thanos Querier (fanout    │ Built-in (single query   │
│                  │ to all sidecars + stores) │ frontend, no fan-out)    │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Deduplication    │ At query time (slower)    │ At write time (faster)   │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Multi-tenancy    │ Basic                     │ Built-in (X-Scope-OrgID) │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Operational      │ Sidecar per Prometheus    │ Centralized (simpler     │
│ complexity       │ + Store Gateway + Compact │ to operate at scale)     │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Scale proven     │ Very large deployments    │ 1B+ active series        │
│                  │ (many companies)          │ (Grafana Cloud)          │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Pick Thanos      │ Already have Prometheus   │                          │
│ when...          │ and want to ADD long-term │                          │
│                  │ storage with minimal      │                          │
│                  │ change to existing setup  │                          │
├──────────────────┼──────────────────────────┼──────────────────────────┤
│ Pick Mimir       │                          │ Greenfield deployment    │
│ when...          │                          │ Multi-tenant requirement │
│                  │                          │ Using Grafana ecosystem  │
│                  │                          │ Need 10B+ series scale   │
└──────────────────┴──────────────────────────┴──────────────────────────┘
```

### Q8: How would you migrate from Datadog to a self-hosted Grafana stack without losing observability during the migration?

```
ANSWER:

PHASE 1: Dual-Write (Week 1-4)
  ┌──────────────────────────────────────────────────────────┐
  │ Applications send metrics to BOTH:                        │
  │                                                           │
  │ OTel Collector → Datadog (existing)                       │
  │              └──→ Mimir (new, via remote_write)            │
  │                                                           │
  │ Logs: FluentBit → Datadog (existing)                      │
  │                └──→ Loki (new)                             │
  │                                                           │
  │ Traces: OTel → Datadog (existing)                         │
  │              └──→ Tempo (new)                              │
  └──────────────────────────────────────────────────────────┘

PHASE 2: Mirror Dashboards (Week 2-6)
  - Recreate critical dashboards in Grafana
  - Use Datadog-to-Grafana migration tools
  - Validate: same data, same alerts, same visualizations
  - Run both in parallel, compare

PHASE 3: Migrate Alerting (Week 4-8)
  - Recreate alert rules in Grafana Unified Alerting
  - Run dual alerting (both fire, only Datadog pages)
  - Compare: false positive/negative rates
  - When confident, switch paging to Grafana alerts

PHASE 4: Cut Over (Week 8-10)
  - Switch primary dashboards to Grafana
  - Redirect on-call runbooks to Grafana URLs
  - Keep Datadog as read-only backup for 2 weeks
  - Decommission Datadog agents

RISKS AND MITIGATIONS:
  ✗ Missing Datadog-specific integrations → build custom plugins
  ✗ PromQL learning curve → training + runbook updates
  ✗ Operational burden → hire/train platform engineer
  ✗ Regression in alerting → parallel run before cutover
```

### Q9: Explain how you would implement distributed tracing in a microservices architecture using Tempo.

```
ANSWER:

STEP 1: Instrumentation
  ┌──────────────────────────────────────────────────────────┐
  │ Add OpenTelemetry SDK to each service:                    │
  │                                                           │
  │ - Auto-instrumentation for HTTP/gRPC frameworks           │
  │ - Manual spans for business-critical operations           │
  │ - Context propagation via W3C TraceContext headers         │
  │   (traceparent: 00-{trace_id}-{span_id}-{flags})         │
  └──────────────────────────────────────────────────────────┘

STEP 2: Collection
  ┌──────────────────────────────────────────────────────────┐
  │ OTel Collector (DaemonSet):                               │
  │                                                           │
  │ receivers:                                                │
  │   otlp: (gRPC :4317, HTTP :4318)                          │
  │                                                           │
  │ processors:                                               │
  │   tail_sampling:  (keep 100% errors, 10% success)         │
  │   batch:          (reduce network calls)                  │
  │                                                           │
  │ exporters:                                                │
  │   otlp/tempo:     (send to Tempo)                         │
  └──────────────────────────────────────────────────────────┘

STEP 3: Storage (Tempo)
  Distributor → Kafka → Block Builder → S3 (Parquet format)
  
  Tail sampling decision:
  - 100% of error traces kept
  - 100% of high-latency traces (> 1s) kept
  - 10% random sampling for normal traces
  - This reduces storage 5-10x while keeping all interesting traces

STEP 4: Querying
  TraceQL in Grafana:
    { resource.service.name = "checkout" && span.http.status_code >= 500 }
  
  Shows: All error traces in the checkout service with full
  span waterfall across all downstream services.

STEP 5: Metrics from Traces (Span Metrics)
  Tempo generates RED metrics from traces automatically:
  - Rate: traces_spanmetrics_calls_total
  - Error: traces_spanmetrics_calls_total{status="error"}
  - Duration: traces_spanmetrics_duration_bucket
  
  Stored in Mimir → queried in Grafana → full circle.
```

### Q10: Your Grafana alerting fires 500 alerts during a single incident. How do you redesign it?

```
ANSWER:

DIAGNOSIS: 500 alerts = alert fatigue = nobody reads any of them

ROOT CAUSES:
  1. Alerting on symptoms instead of causes
  2. No grouping configured
  3. No inhibition rules
  4. No tiered severity
  5. Missing "for" duration (alerts on transient spikes)

REDESIGN:

STEP 1: Alert Hierarchy (top-down)
  ┌──────────────────────────────────────────────────────┐
  │ Level 1 (PAGE): Cluster/zone-level health             │
  │   "us-east-1 availability < 99.9%"                    │
  │   → PagerDuty, immediate                              │
  │                                                       │
  │ Level 2 (TICKET): Service-level issues                │
  │   "checkout-service error rate > 5%"                   │
  │   → Slack + Jira ticket                                │
  │                                                       │
  │ Level 3 (LOG): Instance-level details                  │
  │   "pod-xyz-abc OOM killed"                             │
  │   → Logs only (for forensics, NOT notification)       │
  └──────────────────────────────────────────────────────┘

STEP 2: Alertmanager Configuration
  group_by: [cluster, namespace, alertname]
  group_wait: 30s     (batch first notification)
  group_interval: 5m  (wait before sending updates)
  
  inhibit_rules:
  - source: cluster_down    → target: service_down
  - source: service_down    → target: pod_down
  - source: datacenter_down → target: everything

STEP 3: Mandatory "for" Duration
  All alerts MUST have: for: 5m (minimum)
  Prevents firing on transient spikes
  
  Exception: data loss alerts (for: 0m is OK)

STEP 4: SLO-Based Alerting
  Replace 500 threshold alerts with 5-10 SLO burn rate alerts
  "Are we burning through our error budget too fast?"
  → Catches real problems, ignores noise

RESULT: 500 alerts → 3-5 actionable notifications
```

---

## 21. Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GRAFANA — QUICK REFERENCE CARD                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHAT: Open-source observability visualization + alerting platform           │
│  CREATED: 2014 by Torkel Ödegaard (fork of Kibana)                          │
│  LANGUAGE: Go (backend) + React/TypeScript (frontend)                        │
│  LICENSE: AGPLv3 (OSS) / Proprietary (Enterprise/Cloud)                     │
│  MANAGED: Grafana Cloud                                                      │
│                                                                              │
│  LGTM STACK:                                                                 │
│  ┌─────────┬──────────┬──────────────────────────────────┐                   │
│  │ Loki    │ Logs     │ Index labels, store chunks in S3  │                   │
│  │ Grafana │ UI       │ Dashboards, alerts, correlation   │                   │
│  │ Tempo   │ Traces   │ Distributed tracing, Parquet/S3   │                   │
│  │ Mimir   │ Metrics  │ Long-term Prometheus TSDB, S3     │                   │
│  └─────────┴──────────┴──────────────────────────────────┘                   │
│                                                                              │
│  QUERY LANGUAGES:                                                            │
│  ┌────────────┬───────────────────────────────────────────┐                   │
│  │ PromQL     │ rate(http_requests_total[5m])              │                   │
│  │ LogQL      │ {app="web"} |= "error" | json             │                   │
│  │ TraceQL    │ {span.http.status >= 500}                  │                   │
│  └────────────┴───────────────────────────────────────────┘                   │
│                                                                              │
│  HA: 3+ nodes + shared MySQL/Postgres + Load Balancer                        │
│  PLUGINS: HashiCorp plugin system (gRPC, subprocess isolation)               │
│  ALERTING: Unified Alerting → Alertmanager → Contact Points                  │
│                                                                              │
│  KEY METRICS TO KNOW:                                                        │
│  - grafana_http_request_duration_seconds                                     │
│  - grafana_alerting_rule_evaluations_total                                   │
│  - grafana_datasource_request_total                                          │
│  - prometheus_tsdb_head_series (cardinality)                                 │
│  - process_resident_memory_bytes                                             │
│                                                                              │
│  INTERVIEW PATTERNS:                                                         │
│  - "Design a monitoring system" → Section 16 architecture                    │
│  - "How to handle alert fatigue" → Grouping + Inhibition + SLO alerts        │
│  - "Grafana vs Datadog" → Cost vs convenience vs data ownership              │
│  - "High cardinality" → Recording rules + label discipline                   │
│  - "Make monitoring resilient" → Separate failure domains + dead man switch   │
│                                                                              │
│  FURTHER READING:                                                            │
│  - Grafana Blog: grafana.com/blog (post-mortems, architecture deep dives)    │
│  - Alex Xu: blog.bytebytego.com (Metrics Monitoring chapter)                 │
│  - Google SRE Book: Chapter on SLOs and Alerting                             │
│  - Arpit Bhayani: arpitbhayani.me (distributed systems foundations)          │
│  - Prometheus Docs: prometheus.io/docs (PromQL, recording rules, alerting)   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

*Document version: 1.0 | Last updated: April 2026 | Next review: After Grafana 12.x release*
