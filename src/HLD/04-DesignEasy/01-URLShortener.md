# Design URL Shortener (TinyURL / bit.ly)

> **Difficulty:** Easy | **Frequency:** ★★★★★ | **Companies:** Google, Meta, Amazon, Microsoft
> **Source:** Alex Xu Vol 1 Chapter 8

---

## 1. Requirements

### Functional
- Given a long URL, generate a short URL
- Clicking short URL redirects to original URL
- Short URLs expire after configurable TTL
- Custom short URLs (optional)

### Non-Functional
- Low latency redirect (< 100ms)
- High availability (99.99%)
- Short URL should not be guessable

### Scale
- 100M URLs created per day
- Read:Write = 10:1 → 1B reads/day

---

## 2. Estimation

```
WRITES: 100M/day ÷ 86,400 ≈ 1,200 QPS (Peak: ~3,600)
READS:  1B/day ÷ 86,400 ≈ 12,000 QPS (Peak: ~36,000)

STORAGE (5 years):
  100M × 365 × 5 = 182.5B URLs
  Each record: ~500 bytes (short URL + long URL + metadata)
  Total: ~91 TB

SHORT URL LENGTH:
  Base62 encoding (a-z, A-Z, 0-9) = 62 chars
  62^7 = 3.5 Trillion combinations (enough for 182.5B URLs)
  → 7 characters is sufficient

CACHE:
  80/20 rule: 20% of URLs get 80% of traffic
  Daily reads: 1B × 500 bytes = 500 GB
  Cache 20%: 100 GB → fits in Redis cluster
```

---

## 3. API Design

```
POST /api/v1/urls
  Body: { "long_url": "https://example.com/very/long/path", "ttl": 86400 }
  Response: { "short_url": "https://tiny.url/aB3x7Kz" }

GET /{short_url_key}
  Response: 301 (Permanent Redirect) or 302 (Temporary Redirect)
  Location: https://example.com/very/long/path

  301 vs 302:
  - 301: Browser caches redirect → fewer server hits → less analytics
  - 302: Every click hits server → better analytics tracking
  → Use 302 if analytics matter (most URL shorteners use this)
```

---

## 4. High-Level Architecture

```
┌──────────┐      ┌──────────────┐      ┌─────────────────────┐
│  Client  │─────►│ Load Balancer│─────►│   API Servers       │
└──────────┘      └──────────────┘      │   (Stateless)       │
                                        └────────┬────────────┘
                                                 │
                                    ┌────────────┴────────────┐
                                    │                         │
                              ┌─────▼─────┐            ┌──────▼──────┐
                              │   Cache   │            │  Database   │
                              │  (Redis)  │            │  (NoSQL/SQL)│
                              │           │            │             │
                              │ short_key │            │ short_key   │
                              │ → long_url│            │ → long_url  │
                              └───────────┘            │ → created_at│
                                                       │ → expires_at│
                                                       │ → user_id   │
                                                       └─────────────┘

URL CREATION FLOW:
─────────────────
  1. Client sends long URL
  2. Server generates short key (7 chars)
  3. Store mapping: short_key → long_url in DB
  4. Cache the mapping in Redis
  5. Return short URL to client

URL REDIRECT FLOW:
──────────────────
  1. Client clicks short URL: GET /aB3x7Kz
  2. Server checks Redis cache
  3. Cache HIT → return 302 redirect
  4. Cache MISS → query DB → cache result → return 302
```

---

## 5. Short Key Generation Approaches

```
APPROACH 1: Hash + Truncate
────────────────────────────
  MD5("https://long-url.com") = "5d41402abc4b2a76"
  Take first 7 chars → "5d41402"
  Convert to Base62 → "aB3x7Kz"
  
  Problem: Collisions! Two URLs could hash to same 7 chars.
  Solution: Check DB, if exists → append counter and rehash.

APPROACH 2: Counter-Based (Recommended)
───────────────────────────────────────
  Auto-increment counter: 1, 2, 3, ..., N
  Convert to Base62: 1 → "1", 100000 → "q0U"
  
  ┌─────────────────────────────┐
  │  ID Service (Distributed)   │
  │  ┌───────┐  ┌───────┐      │
  │  │Range 1│  │Range 2│      │
  │  │1-1M   │  │1M-2M  │     │
  │  └───────┘  └───────┘      │
  └─────────────────────────────┘
  
  Each server gets a range of IDs to assign.
  No collision possible! 
  
  But: sequential IDs are predictable → add random shuffle if needed.

APPROACH 3: Pre-generated Key Service
──────────────────────────────────────
  Pre-generate millions of unique 7-char keys.
  Store in a "key pool" database.
  When URL created → grab unused key from pool.
  
  ┌──────────────────────────┐
  │  Key Pool                │
  │  ┌──────────┬──────────┐│
  │  │ Unused   │ Used     ││
  │  │ aB3x7Kz  │ xY9mN2p ││
  │  │ pQ4rS1t  │ bC5dE6f ││
  │  │ ...      │ ...      ││
  │  └──────────┴──────────┘│
  └──────────────────────────┘
  
  Pros: No collision, no counter coordination
  Cons: Need to manage key pool
```

---

## 6. Database Choice

```
Option A: SQL (PostgreSQL)
  Table: urls
  ┌──────────┬──────────────────────────┬─────────┬───────────┐
  │ id (PK)  │ long_url                 │short_key│ expires_at│
  ├──────────┼──────────────────────────┼─────────┼───────────┤
  │ 1        │ https://example.com/path │ aB3x7Kz │ 2025-12-31│
  └──────────┴──────────────────────────┴─────────┴───────────┘
  Index on: short_key (for redirect lookups)
  Index on: long_url (to avoid duplicate entries)

Option B: NoSQL (DynamoDB) — Better for scale
  Partition Key: short_key
  Attributes: long_url, created_at, expires_at, user_id
  
  → Simple key-value lookup = perfect for NoSQL
  → 91 TB over 5 years → horizontal scaling with DynamoDB
```

---

## 7. Deep Dive Topics

### Analytics
```
Track: click count, referrer, geolocation, device type, timestamp
Store clicks in Kafka → process with Flink → store in analytics DB
```

### Rate Limiting
```
Prevent abuse: max 100 URL creations per user per hour
Use API key + Redis counter (see Rate Limiter design)
```

### Cleanup Expired URLs
```
Background job: scan DB for expired URLs, delete them
Or: lazy deletion — check expiry on read, return 404 if expired
```

---

## 8. Key Points to Mention in Interview

1. **Base62 encoding** for human-readable short URLs
2. **Counter-based or pre-generated keys** to avoid collisions
3. **302 redirect** for analytics tracking
4. **Cache hot URLs** in Redis (80/20 rule)
5. **Shard by short_key hash** for scaling the database
6. **Bloom filter** to quickly check if a long URL already has a short URL
