# Design News Feed (Facebook / Twitter Timeline)

> **Difficulty:** Medium | **Frequency:** ★★★★★ | **Companies:** Meta, Twitter, LinkedIn
> **Source:** Alex Xu Vol 1 Chapter 11

---

## 1. Requirements

### Functional
- User publishes a post → appears in followers' feeds
- Feed shows posts from followed users, sorted by time/relevance
- Support text, images, videos
- Infinite scroll pagination

### Non-Functional
- Feed generation < 500ms
- High availability
- Eventual consistency acceptable (1-2 second delay OK)

### Scale
- 300M DAU, average 500 friends/followers
- 1M posts/day from followed accounts

---

## 2. Two Core Problems

```
1. FEED PUBLISHING: User creates post → deliver to followers
2. FEED GENERATION: User opens app → show their personalized feed
```

---

## 3. Fan-Out Strategies

### Fan-Out on Write (Push Model)
```
  User A creates post → immediately push to ALL followers' feeds

  User A posts "Hello World"
       │
       ▼
  ┌──────────────┐
  │ Post Service │──► Store post in Posts DB
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐     A's followers: [B, C, D, E]
  │  Fan-out     │
  │  Service     │──► Write to B's feed cache
  │              │──► Write to C's feed cache
  │              │──► Write to D's feed cache
  │              │──► Write to E's feed cache
  └──────────────┘

  Each user's feed: Redis sorted set (score = timestamp)
  B's feed: { post_A_1: 1623456789, post_C_5: 1623456780, ... }

  Pros:
  ✓ Feed reads are super fast (pre-computed in cache)
  ✓ Great for users with few followers
  
  Cons:
  ✗ Celebrity problem: User with 10M followers → 10M writes per post!
  ✗ Wasted work for inactive followers
  ✗ Write-heavy
```

### Fan-Out on Read (Pull Model)
```
  User B opens feed → pull latest posts from all followed users

  User B opens app
       │
       ▼
  ┌──────────────┐     B follows: [A, C, D]
  │ Feed Service │
  │              │──► Get A's recent posts
  │              │──► Get C's recent posts
  │              │──► Get D's recent posts
  │              │──► Merge + Sort + Return top N
  └──────────────┘

  Pros:
  ✓ No wasted writes for inactive users
  ✓ Handles celebrities naturally
  
  Cons:
  ✗ Feed read is SLOW (must query multiple sources)
  ✗ Merging is expensive at read time
```

### Hybrid Approach (BEST — Used by Twitter/Facebook)
```
  ┌──────────────────────────────────────────────────────────┐
  │              HYBRID FAN-OUT STRATEGY                       │
  │                                                          │
  │  Regular users (< 10K followers):                         │
  │  ──────────────────────────────                          │
  │  Fan-out on WRITE (push to followers' caches)            │
  │  Fast reads for most users!                              │
  │                                                          │
  │  Celebrities (> 10K followers):                           │
  │  ─────────────────────────────                           │
  │  Fan-out on READ (followers pull at read time)           │
  │  Celebrity post NOT pushed to 10M caches                 │
  │                                                          │
  │  When User B opens feed:                                 │
  │  1. Read pre-computed feed from cache (regular users)    │
  │  2. Fetch latest posts from celebrities B follows        │
  │  3. Merge both lists                                     │
  │  4. Rank and return                                      │
  └──────────────────────────────────────────────────────────┘
```

---

## 4. Architecture

```
┌──────────┐     ┌──────────────┐     ┌──────────────────────────────┐
│  Client  │────►│ Load Balancer│────►│  API Servers                 │
└──────────┘     └──────────────┘     └──────────┬───────────────────┘
                                                 │
                        ┌────────────────────────┼────────────────┐
                        ▼                        ▼                ▼
                 ┌──────────────┐      ┌──────────────┐  ┌──────────────┐
                 │ Post Service │      │ Feed Service │  │ User Service │
                 │              │      │              │  │              │
                 │ Create post  │      │ Generate     │  │ Followers    │
                 │ Store post   │      │ user's feed  │  │ graph        │
                 └──────┬───────┘      └──────┬───────┘  └──────────────┘
                        │                     │
                        ▼                     ▼
                 ┌──────────────┐      ┌──────────────┐
                 │ Fan-out Svc  │      │ Feed Cache   │
                 │ (async via   │      │ (Redis)      │
                 │  Kafka)      │      │              │
                 └──────────────┘      │ user:B →     │
                                       │ [post_ids]   │
                 ┌──────────────┐      └──────────────┘
                 │  Posts DB    │
                 │ (post content│      ┌──────────────┐
                 │  + metadata) │      │ Social Graph │
                 └──────────────┘      │ (who follows │
                                       │  whom)       │
                                       └──────────────┘
```

---

## 5. Feed Ranking

```
Instead of pure chronological, rank by relevance:

RANKING SIGNALS:
  ┌────────────────────────────────────┐
  │ Signal          │ Weight           │
  ├────────────────────────────────────┤
  │ Recency         │ High             │
  │ User affinity   │ High (close      │
  │                 │ friends > others)│
  │ Post engagement │ Medium (likes,   │
  │                 │ comments, shares)│
  │ Content type    │ Medium (video >  │
  │                 │ text for some)   │
  │ Author quality  │ Low              │
  └────────────────────────────────────┘

  Score = w1*recency + w2*affinity + w3*engagement + w4*content_type
  
  ML models (more advanced):
  - Predict probability user will engage with post
  - Train on historical engagement data
```

---

## 6. Key Points for Interview

1. **Hybrid fan-out** is the key answer — push for regular users, pull for celebrities
2. **Redis sorted sets** for feed cache (score = timestamp)
3. **Kafka** for async fan-out (decouple post creation from feed distribution)
4. **Social graph** for follower lookups (who follows whom)
5. **Feed ranking** — mention relevance scoring beyond just chronological
6. **Pagination** — cursor-based (not offset-based) for real-time feeds
7. Celebrity problem: Lady Gaga posts → 80M followers → can't push to 80M caches
