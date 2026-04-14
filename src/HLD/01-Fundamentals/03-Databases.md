# Database Fundamentals for System Design

> **Difficulty:** Easy | **Time:** 3 hours | **Priority:** Must Know

---

## Why Databases Are Central to System Design

Every system stores data. The choice between SQL vs NoSQL, how you index, and how you model data directly impacts scalability, latency, and correctness.

---

## 1. SQL (Relational) vs NoSQL

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DATABASE LANDSCAPE                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  RELATIONAL (SQL)              NOSQL                                │
│  ┌──────────────┐              ┌──────────────────────────────────┐ │
│  │ PostgreSQL   │              │                                  │ │
│  │ MySQL        │              │  Key-Value    │ Document         │ │
│  │ Oracle       │              │  ┌─────────┐  │ ┌────────────┐  │ │
│  │ SQL Server   │              │  │ Redis   │  │ │ MongoDB    │  │ │
│  │              │              │  │ DynamoDB│  │ │ CouchDB    │  │ │
│  │ Structured   │              │  │ Memcache│  │ │ Firestore  │  │ │
│  │ schema       │              │  └─────────┘  │ └────────────┘  │ │
│  │ ACID         │              │               │                  │ │
│  │ Joins        │              │  Wide-Column  │ Graph            │ │
│  │ Normalized   │              │  ┌─────────┐  │ ┌────────────┐  │ │
│  └──────────────┘              │  │Cassandra│  │ │ Neo4j      │  │ │
│                                 │  │ HBase   │  │ │ Amazon     │  │ │
│                                 │  │ BigTable│  │ │ Neptune    │  │ │
│                                 │  └─────────┘  │ └────────────┘  │ │
│                                 └──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### Decision Matrix

| Criteria | SQL | NoSQL |
|----------|-----|-------|
| Data Structure | Structured, fixed schema | Flexible, dynamic schema |
| Relationships | Complex joins supported | Limited joins (denormalized) |
| Transactions | Strong ACID | Eventual consistency (mostly) |
| Scaling | Vertical (scale up) | Horizontal (scale out) |
| Query Language | Standard SQL | Database-specific |
| Best For | Financial data, e-commerce | Social media, IoT, caching |

### When to Choose What
```
Choose SQL when:                     Choose NoSQL when:
───────────────                      ────────────────
✓ Data has relationships             ✓ Schema changes frequently
✓ Need ACID transactions             ✓ Massive scale (millions of writes/sec)
✓ Complex queries with joins         ✓ Simple access patterns (key lookup)
✓ Data integrity is critical         ✓ Denormalized data is acceptable
✓ Financial/banking systems          ✓ Geo-distributed data
✓ E-commerce orders                  ✓ Time-series, logs, metrics

Examples:                            Examples:
- Payment system → PostgreSQL        - Session store → Redis
- Order management → MySQL           - Product catalog → MongoDB
- User accounts → PostgreSQL         - Chat messages → Cassandra
- Inventory → SQL                    - Social graph → Neo4j
                                     - Leaderboard → Redis
```

---

## 2. ACID Properties

```
┌─────────────────────────────────────────────────────────────┐
│                    ACID PROPERTIES                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  A - Atomicity                                              │
│  ────────────                                               │
│  All operations in a transaction succeed or ALL fail.       │
│  No partial updates.                                        │
│                                                             │
│  Transfer $100: A → B                                       │
│  ┌─────────────────────┐                                    │
│  │ Deduct $100 from A  │  ← Both happen                    │
│  │ Add $100 to B       │  ← or neither does                │
│  └─────────────────────┘                                    │
│                                                             │
│  C - Consistency                                            │
│  ──────────────                                             │
│  DB moves from one valid state to another.                  │
│  All constraints/rules are maintained.                      │
│  e.g., Account balance can never be negative                │
│                                                             │
│  I - Isolation                                              │
│  ────────────                                               │
│  Concurrent transactions don't interfere.                   │
│  Each transaction sees a consistent snapshot.               │
│                                                             │
│  Isolation Levels (weakest → strongest):                    │
│  Read Uncommitted → Read Committed → Repeatable Read        │
│  → Serializable                                             │
│                                                             │
│  D - Durability                                             │
│  ────────────                                               │
│  Once committed, data survives crashes.                     │
│  Written to disk / WAL (Write-Ahead Log).                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Indexing

Indexes make reads faster but writes slower. Critical for query performance.

```
Without Index (Full Table Scan):        With Index (B-Tree Lookup):
─────────────────────────────          ─────────────────────────────
Table: users (1M rows)                 B-Tree Index on 'email':

SELECT * FROM users                           ┌─────┐
WHERE email = 'foo@bar.com'                   │ M   │
                                              ├──┬──┤
Scans ALL 1M rows → O(N)                    /    \
Time: ~500ms                           ┌────┐    ┌────┐
                                       │ D  │    │ S  │
                                       ├──┬─┤    ├──┬─┤
                                      /    \    /    \
                                   ┌──┐  ┌──┐ ┌──┐  ┌──┐
                                   │A │  │F │ │M │  │Z │
                                   └──┘  └──┘ └──┘  └──┘

                                   Follows tree → O(log N)
                                   Time: ~1ms
```

### Types of Indexes
```
1. B-Tree Index (Default in most DBs)
   - Sorted tree structure
   - Good for: range queries, equality, sorting
   - Used by: PostgreSQL, MySQL

2. Hash Index
   - Hash table lookup
   - Good for: exact equality (WHERE id = 123)
   - NOT good for: range queries
   - Used by: Redis, Memcached

3. Composite Index
   - Index on multiple columns
   - CREATE INDEX idx ON orders(user_id, created_at)
   - Leftmost prefix rule: can use (user_id) or (user_id, created_at)
   - CANNOT use just (created_at) alone with this index

4. Full-Text Index
   - For text search (LIKE '%keyword%')
   - Used by: Elasticsearch, PostgreSQL tsvector

5. Geospatial Index
   - For location queries (find nearby)
   - R-Tree, Quadtree, Geohash
   - Used by: PostGIS, MongoDB
```

### Index Trade-offs
```
                 Reads                    Writes
                 ─────                    ──────
No Index:        SLOW (full scan)         FAST (just append)
With Index:      FAST (tree lookup)       SLOWER (update index too)

Rule of Thumb:
- Index columns used in WHERE, JOIN, ORDER BY
- Don't over-index (each index costs write performance)
- Composite indexes > multiple single-column indexes
```

---

## 4. NoSQL Deep Dive

### Key-Value Stores (Redis, DynamoDB)
```
┌──────────────────────────────┐
│  Key        │  Value          │
├──────────────────────────────┤
│  user:123   │  {json blob}   │
│  session:ab │  {token, exp}  │
│  cache:page │  <html>...</>  │
└──────────────────────────────┘

Operations: GET, SET, DELETE → O(1)
Use cases: Caching, sessions, counters, leaderboards
```

### Document Stores (MongoDB, CouchDB)
```json
{
  "_id": "user_123",
  "name": "John",
  "email": "john@test.com",
  "orders": [
    { "id": "ord_1", "total": 99.99, "items": [...] },
    { "id": "ord_2", "total": 49.99, "items": [...] }
  ],
  "address": {
    "street": "123 Main St",
    "city": "SF"
  }
}

Embedded documents = no joins needed!
Flexible schema = different docs can have different fields
```

### Wide-Column Stores (Cassandra, HBase)
```
Row Key: user_123
┌──────────┬──────────────────────────────────────────┐
│          │ Column Family: profile    │ CF: activity │
│ Row Key  ├──────┬───────┬───────────┼──────┬───────┤
│          │ name │ email │ city      │ last │ count │
├──────────┼──────┼───────┼───────────┼──────┼───────┤
│ user_123 │ John │ j@t.c │ SF        │ 10am │ 42    │
│ user_456 │ Jane │ ja@.. │ NYC       │ 2pm  │ 17    │
└──────────┴──────┴───────┴───────────┴──────┴───────┘

Best for: Time-series data, event logs, write-heavy workloads
Used by: Netflix, Discord, Instagram
```

### Graph Databases (Neo4j, Neptune)
```
        ┌──────┐  FOLLOWS   ┌──────┐
        │ Alice│ ──────────►│ Bob  │
        └──┬───┘            └──┬───┘
           │                   │
    LIKES  │                   │ POSTED
           ▼                   ▼
        ┌──────┐           ┌──────┐
        │Post_1│           │Post_2│
        └──────┘           └──────┘

Best for: Social networks, recommendation engines, fraud detection
Query: "Find all friends of friends who liked Post_1"
SQL would need expensive recursive joins. Graph DB: 1 traversal.
```

---

## 5. Database Selection Guide for Interviews

```
┌──────────────────────────────────────────────────────────────┐
│               WHICH DATABASE FOR WHICH USE CASE?             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  User Profiles, Auth ──────────► PostgreSQL / MySQL          │
│  Payment Transactions ─────────► PostgreSQL (ACID critical)  │
│  Session Storage ──────────────► Redis                       │
│  Product Catalog ──────────────► MongoDB / DynamoDB          │
│  Chat Messages ────────────────► Cassandra (write-heavy)     │
│  Social Graph ─────────────────► Neo4j / Neptune             │
│  Search ───────────────────────► Elasticsearch               │
│  Time-Series / Metrics ────────► InfluxDB / TimescaleDB      │
│  File Metadata ────────────────► MongoDB                     │
│  Leaderboard / Counters ───────► Redis (sorted sets)         │
│  News Feed ────────────────────► Redis + Cassandra           │
│  Analytics ────────────────────► ClickHouse / BigQuery       │
│  Configuration ────────────────► etcd / ZooKeeper            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. Key Takeaways for Interviews

1. **Always justify your DB choice** — don't just say "use Postgres", explain WHY
2. **Think about access patterns first** — how will data be read/written?
3. **Index strategy matters** — mention what columns you'd index and why
4. **SQL for consistency**, NoSQL for scale and flexibility
5. **Denormalization is OK** in system design when joins become a bottleneck
6. **Know at least one DB** from each category deeply (PostgreSQL, Redis, MongoDB, Cassandra)
7. **WAL (Write-Ahead Log)** — mention for durability questions
