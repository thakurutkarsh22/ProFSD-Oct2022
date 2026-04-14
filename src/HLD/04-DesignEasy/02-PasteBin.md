# Design Paste Bin

> **Difficulty:** Easy | **Frequency:** ★★★☆☆ | **Companies:** Google, Amazon

---

## 1. Requirements

### Functional
- Users can create a paste with text content and get a unique URL
- Users can read a paste via URL
- Pastes can be public or private
- Pastes expire after configurable TTL (default: 30 days)
- Optional: syntax highlighting, user accounts

### Non-Functional
- High availability, low latency reads
- Max paste size: 10 MB

### Scale
- 10M pastes created per day
- Read:Write = 5:1 → 50M reads/day

---

## 2. Estimation

```
WRITES: 10M/day ≈ 120 QPS
READS:  50M/day ≈ 600 QPS

STORAGE (5 years):
  10M × 365 × 5 = 18.25B pastes
  Average paste: 10 KB
  Content: 18.25B × 10 KB = 182.5 TB (content in object storage)
  Metadata: 18.25B × 200 bytes = 3.65 TB (in database)
```

---

## 3. Architecture

```
┌──────────┐     ┌────────┐     ┌──────────────┐
│  Client  │────►│   LB   │────►│  API Server  │
└──────────┘     └────────┘     └──────┬───────┘
                                       │
                          ┌────────────┼────────────┐
                          ▼            ▼            ▼
                    ┌──────────┐ ┌──────────┐ ┌──────────┐
                    │  Cache   │ │ Metadata │ │  Object  │
                    │ (Redis)  │ │ DB (SQL) │ │ Storage  │
                    │          │ │          │ │  (S3)    │
                    │ paste_id │ │ paste_id │ │          │
                    │→ metadata│ │→ s3_path │ │ Actual   │
                    └──────────┘ │→ user_id │ │ paste    │
                                 │→ created │ │ content  │
                                 │→ expires │ │          │
                                 └──────────┘ └──────────┘

CREATE PASTE:
  1. Generate unique paste ID (same as URL shortener — Base62)
  2. Upload content to S3: s3://pastes/{paste_id}
  3. Store metadata in DB: paste_id, s3_path, user, expiry
  4. Return URL: https://pastebin.com/{paste_id}

READ PASTE:
  1. Check Redis cache → HIT: return content
  2. MISS: Query metadata DB for S3 path
  3. Fetch content from S3
  4. Cache in Redis
  5. Return to user

KEY DIFFERENCE FROM URL SHORTENER:
  URL Shortener: stores ~500 bytes per record (URLs)
  Paste Bin: stores up to 10 MB per paste (use object storage!)
```

---

## 4. Key Design Decisions

- **Object storage (S3)** for paste content — not in the database
- **CDN** for frequently accessed public pastes
- **Key generation** same as URL Shortener (counter-based or pre-generated)
- **Cleanup job** to delete expired pastes from S3 and metadata DB
- **Rate limiting** to prevent spam paste creation
