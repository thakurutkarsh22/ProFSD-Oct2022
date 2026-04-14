# Back-of-the-Envelope Estimation

> **Difficulty:** Easy-Medium | **Time:** 2 hours | **Priority:** Must Know
> **Source:** Alex Xu Vol 1 Chapter 2, Jeff Dean's Google Talk

---

## Why Estimation Matters

Interviewers want to see you can reason about scale. You don't need exact numbers — just order-of-magnitude estimates that drive design decisions.

---

## 1. Quick Reference Numbers

### Traffic
```
┌────────────────────────────────────────────────────┐
│  DAILY ACTIVE USERS → QPS CONVERSION               │
├────────────────────────────────────────────────────┤
│                                                    │
│  1 Million users/day, each makes 10 requests:      │
│  = 10M requests / 86,400 seconds                   │
│  ≈ 116 QPS                                         │
│                                                    │
│  Quick formula:                                    │
│  QPS = (DAU × requests_per_user) / 86,400          │
│                                                    │
│  Peak QPS ≈ 2× to 3× average QPS                  │
│                                                    │
│  Handy shortcuts:                                  │
│  ─────────────────────────────                     │
│  1M requests/day  ≈ 12 QPS                         │
│  10M requests/day ≈ 120 QPS                        │
│  100M requests/day ≈ 1,200 QPS                     │
│  1B requests/day  ≈ 12,000 QPS                     │
│                                                    │
│  Seconds in a day: 86,400 ≈ 10^5 (for quick math) │
│                                                    │
└────────────────────────────────────────────────────┘
```

### Storage
```
┌────────────────────────────────────────────────────┐
│  STORAGE ESTIMATION                                 │
├────────────────────────────────────────────────────┤
│                                                    │
│  Text:                                             │
│  ─ Tweet/message: ~300 bytes                       │
│  ─ URL: ~100 bytes                                 │
│  ─ User profile (text fields): ~1 KB               │
│  ─ JSON API response: ~1-10 KB                     │
│                                                    │
│  Media:                                            │
│  ─ Thumbnail image: ~20 KB                         │
│  ─ Regular image: ~200 KB                          │
│  ─ High-res photo: ~2 MB                           │
│  ─ 1-minute video (compressed): ~50 MB             │
│  ─ 1-hour HD video: ~3 GB                          │
│                                                    │
│  Example: Twitter storage                          │
│  ─ 500M tweets/day × 300 bytes = 150 GB/day       │
│  ─ Per year: ~55 TB                                │
│  ─ With media: 10× → ~550 TB/year                  │
│                                                    │
└────────────────────────────────────────────────────┘
```

### Bandwidth
```
┌────────────────────────────────────────────────────┐
│  BANDWIDTH ESTIMATION                               │
├────────────────────────────────────────────────────┤
│                                                    │
│  Bandwidth = QPS × average_response_size            │
│                                                    │
│  Example: Photo sharing service                    │
│  ─ 100 QPS for image uploads                       │
│  ─ Average image: 200 KB                           │
│  ─ Upload bandwidth: 100 × 200 KB = 20 MB/s       │
│                                                    │
│  Reads are typically 10-100× writes                │
│  ─ Read bandwidth: 1000 QPS × 200 KB = 200 MB/s   │
│                                                    │
└────────────────────────────────────────────────────┘
```

---

## 2. Estimation Framework (Step by Step)

```
Step 1: Clarify scale
  "How many DAU?"  → e.g., 100M DAU

Step 2: Estimate QPS
  100M users × 5 reads/day = 500M reads/day
  500M / 100K (seconds in day) ≈ 5,000 QPS
  Peak: 5,000 × 3 = 15,000 QPS

Step 3: Estimate storage
  100M users × 1KB profile = 100 GB (user data)
  10M posts/day × 500 bytes = 5 GB/day
  5 GB × 365 = 1.8 TB/year

Step 4: Estimate bandwidth
  5,000 QPS × 10 KB avg response = 50 MB/s

Step 5: Estimate servers needed
  1 server ≈ 5K-10K QPS (for API servers)
  15K peak QPS / 10K = 2 servers (minimum)
  With redundancy: 4-6 servers
```

---

## 3. Worked Example: Design URL Shortener

```
Requirements: 100M new URLs/day, 10:1 read/write ratio

WRITES (URL Creation):
─────────────────────
  100M/day ÷ 86,400 ≈ 1,200 QPS
  Peak: 1,200 × 3 = 3,600 QPS

READS (URL Redirect):
────────────────────
  1B/day (10:1 ratio) ÷ 86,400 ≈ 12,000 QPS
  Peak: 12,000 × 3 = 36,000 QPS

STORAGE (over 5 years):
──────────────────────
  100M URLs/day × 365 × 5 = 182.5 Billion URLs
  Each URL entry: ~500 bytes (short URL + long URL + metadata)
  182.5B × 500 bytes = 91.25 TB

BANDWIDTH:
─────────
  Write: 1,200 QPS × 500 bytes = 600 KB/s (negligible)
  Read:  12,000 QPS × 500 bytes = 6 MB/s

CACHE:
──────
  80/20 rule: 20% of URLs generate 80% of traffic
  Daily read requests: 1B × 500 bytes = 500 GB
  Cache 20%: 100 GB → fits in memory (Redis)

DESIGN IMPLICATIONS:
───────────────────
  ✓ Read-heavy → Use cache (Redis) + Read replicas
  ✓ 36K peak QPS → 4-8 application servers
  ✓ 91 TB storage → Need sharding (NoSQL or sharded SQL)
  ✓ 100 GB cache → Redis cluster (easily fits)
```

---

## 4. Powers of 2 Cheat Sheet

```
2^10  = 1,024            ≈ 1 Thousand    (1 KB)
2^20  = 1,048,576        ≈ 1 Million     (1 MB)
2^30  = 1,073,741,824    ≈ 1 Billion     (1 GB)
2^40  = 1,099,511,627,776 ≈ 1 Trillion   (1 TB)

Common character encodings:
  ASCII: 1 byte per character
  UTF-8: 1-4 bytes per character
  
UUID: 128 bits = 16 bytes
Timestamp (Unix): 4-8 bytes
Integer: 4-8 bytes
```

---

## 5. Key Takeaways for Interviews

1. **Round aggressively** — use 10^5 for seconds/day, 10^6 for million
2. **State your assumptions** clearly before calculating
3. **The goal is order-of-magnitude** — 5K vs 50K QPS matters; 5K vs 6K doesn't
4. **Use estimation to drive decisions**: need sharding? need cache? how many servers?
5. **Practice the framework**: QPS → Storage → Bandwidth → Cache → Servers
6. **Peak QPS = 2-3× average** — always design for peak
