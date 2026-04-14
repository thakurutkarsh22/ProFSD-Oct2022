# Design Rate Limiter

> **Difficulty:** Easy | **Frequency:** ★★★★★ | **Companies:** All major companies
> **Source:** Alex Xu Vol 1 Chapter 4

---

## 1. Requirements

### Functional
- Limit the number of requests a client can make in a time window
- Return 429 Too Many Requests when limit exceeded
- Support different limits per API endpoint / user / IP

### Non-Functional
- Low latency (must not slow down valid requests)
- Distributed (works across multiple servers)
- Fault tolerant (if rate limiter fails, allow traffic through)

---

## 2. Where to Place Rate Limiter

```
OPTION 1: Client-side           OPTION 2: Server-side         OPTION 3: Middleware
(Not recommended)               (In app code)                 (API Gateway)

Client ──►  Server              Client ──► Server             Client ──► API Gateway ──► Server
  │                                        │ rate                         │ rate
  │ Easy to                                │ check                        │ check
  │ bypass!                                │                              │
                                App code controls              Clean separation
                                the logic                      Kong, AWS API GW, Nginx

RECOMMENDATION: Middleware (API Gateway) for most cases.
Server-side if you need fine-grained per-endpoint control.
```

---

## 3. Rate Limiting Algorithms

### Algorithm 1: Token Bucket (Most Common)
```
  Bucket has MAX tokens (e.g., 10)
  Tokens added at fixed rate (e.g., 1 per second)
  Each request consumes 1 token
  No tokens → request rejected

  Time 0:  Bucket: [●●●●●●●●●●] (10 tokens, full)
  
  Request 1: Take token → [●●●●●●●●●○] (9 left) → ALLOW
  Request 2: Take token → [●●●●●●●●○○] (8 left) → ALLOW
  ...
  Request 10: Take token → [○○○○○○○○○○] (0 left) → ALLOW
  Request 11: No tokens  → REJECT (429)
  
  After 1 second: refill 1 token → [●○○○○○○○○○]
  Request 12: Take token → [○○○○○○○○○○] → ALLOW

  Pros: Allows bursts (up to bucket size), smooth rate limiting
  Cons: Two parameters to tune (bucket size, refill rate)
  Used by: Amazon, Stripe
```

### Algorithm 2: Sliding Window Log
```
  Keep a log of request timestamps in a sorted set.
  Count requests in the last N seconds.

  Window: 60 seconds, Limit: 5 requests

  Timeline: ──────|──────────────60s────────────────|──►
  
  Requests: [T=01] [T=15] [T=30] [T=45] [T=50] | [T=61]
            req1    req2   req3   req4   req5     req6
  
  At T=61: Remove timestamps older than T=1 (61-60)
  Count remaining: 4 requests → req6 ALLOWED (< 5)
  
  At T=62: [T=15][T=30][T=45][T=50][T=61]
  Count: 5 → next request REJECTED

  Pros: Very accurate, no boundary issues
  Cons: Memory-heavy (stores every timestamp)
  Implementation: Redis sorted set (ZRANGEBYSCORE)
```

### Algorithm 3: Sliding Window Counter
```
  Combines Fixed Window + weight from previous window.

  Limit: 100 requests per minute

  ┌──────────────┐┌──────────────┐
  │ Prev Window  ││ Curr Window  │
  │ (70 reqs)    ││ (20 reqs)    │
  └──────────────┘└──────────────┘
                   ▲
                   │ We're 30% into current window

  Weighted count = 70 × 0.7 (70% of prev) + 20 × 1.0
                 = 49 + 20 = 69
  
  69 < 100 → ALLOW

  Pros: Memory efficient, reasonably accurate
  Cons: Approximate (not exact)
  Used by: Cloudflare
```

### Algorithm 4: Fixed Window Counter
```
  Divide time into fixed windows. Count requests per window.

  Window: 60 seconds, Limit: 100

  |───Window 1───|───Window 2───|
  |  98 requests |  35 requests |
  |              |              |

  Problem: Boundary burst!
  |      ~~~~90 reqs|100 reqs~~~~    |
  In 1 second across boundary: 190 requests pass!
  (90 at end of window 1 + 100 at start of window 2)

  Pros: Simple, memory efficient
  Cons: Burst at window boundaries
```

### Comparison
```
┌─────────────────────┬──────────┬────────┬────────────────┐
│ Algorithm           │ Accuracy │ Memory │ Best For       │
├─────────────────────┼──────────┼────────┼────────────────┤
│ Token Bucket        │ Good     │ Low    │ API rate limit │
│ Sliding Window Log  │ Exact    │ High   │ Small scale    │
│ Sliding Window Ctr  │ Good     │ Low    │ Large scale    │
│ Fixed Window        │ Fair     │ Low    │ Simple needs   │
└─────────────────────┴──────────┴────────┴────────────────┘
```

---

## 4. Distributed Rate Limiting

```
PROBLEM: Rate limiter state must be shared across all servers.

  Server 1: User made 3 requests here
  Server 2: User made 3 requests here
  Total: 6 requests, but limit is 5!
  Each server thinks it's fine (only sees 3).

SOLUTION: Centralized store (Redis)

  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │ Server 1 │     │ Server 2 │     │ Server 3 │
  └────┬─────┘     └────┬─────┘     └────┬─────┘
       │                │                │
       └────────────────┼────────────────┘
                        ▼
                  ┌──────────┐
                  │  Redis   │
                  │          │
                  │ user:123 │
                  │ count: 5 │
                  │ ttl: 60s │
                  └──────────┘

  Redis commands (atomic):
  INCR user:123:rate_limit      # Increment counter
  EXPIRE user:123:rate_limit 60 # Set TTL
  
  Or better: Lua script for atomic check-and-increment
```

---

## 5. Architecture

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐
│  Client  │───►│ Rate Limiter │───►│  API Server  │───►│ Backend  │
└──────────┘    │ (Middleware)  │    └──────────────┘    └──────────┘
                └──────┬───────┘
                       │
                  ┌────▼────┐
                  │  Redis  │
                  │ (shared │
                  │  state) │
                  └─────────┘

  Rules stored in config/DB:
  ┌───────────────────────────────────────────────┐
  │ Rule: /api/login  → 5 requests per minute     │
  │ Rule: /api/search → 100 requests per minute   │
  │ Rule: Premium user → 1000 requests per minute │
  └───────────────────────────────────────────────┘
```

---

## 6. Response Headers
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1623456789  (Unix timestamp)

HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1623456789
Retry-After: 30
```

---

## 7. Key Points for Interview

1. **Token Bucket** is the go-to algorithm (used by AWS, Stripe)
2. **Redis** for distributed rate limiting (atomic operations)
3. **Fail open** if Redis is down (allow traffic, don't block)
4. **Multiple rules**: per user, per IP, per endpoint
5. **Return 429** with rate limit headers and Retry-After
6. **Race condition** → use Redis Lua scripts for atomic operations
