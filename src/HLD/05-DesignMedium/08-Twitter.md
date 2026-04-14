# Design Twitter

> **Difficulty:** Medium | **Frequency:** ★★★★★ | **Companies:** Twitter, Meta, Google

---

## 1. Requirements

### Functional
- Post tweets (280 chars + media)
- Follow/unfollow users
- Home timeline (tweets from followed users)
- User timeline (all tweets by a user)
- Search tweets
- Trending topics

### Scale
- 300M DAU, 500M tweets/day
- Average user follows 200 people
- Timeline read:write = 100:1

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         TWITTER SYSTEM                                │
│                                                                      │
│  ┌──────┐  ┌────┐  ┌──────────┐                                    │
│  │Client│─►│ LB │─►│API Server│                                    │
│  └──────┘  └────┘  └────┬─────┘                                    │
│                          │                                           │
│       ┌──────────────────┼────────────────────────┐                  │
│       ▼                  ▼                        ▼                  │
│ ┌──────────┐    ┌──────────────┐         ┌──────────────┐           │
│ │  Tweet   │    │  Timeline   │         │   Search     │           │
│ │ Service  │    │  Service    │         │   Service    │           │
│ │          │    │             │         │              │           │
│ │ Post     │    │ Home feed   │         │ Elasticsearch│           │
│ │ Delete   │    │ generation  │         │              │           │
│ └────┬─────┘    └──────┬──────┘         └──────────────┘           │
│      │                 │                                             │
│      ▼                 ▼                                             │
│ ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────┐   │
│ │ Tweets   │    │ Timeline │    │ Social   │    │  Trending    │   │
│ │ DB       │    │ Cache    │    │ Graph    │    │  Service     │   │
│ │(Cassandra│    │ (Redis)  │    │ (DB)     │    │  (Kafka +    │   │
│ │ /MySQL)  │    │          │    │          │    │   Redis)     │   │
│ └──────────┘    └──────────┘    └──────────┘    └──────────────┘   │
└──────────────────────────────────────────────────────────────────────┘

TWEET FLOW:
  1. User posts tweet → Tweet Service stores in Tweets DB
  2. Async: Fan-out Service reads follower list
  3. For non-celebrity followers: push tweet_id to their timeline cache
  4. For celebrity's followers: pull at read time (hybrid fan-out)
  5. Index tweet in Elasticsearch for search
  6. Publish tweet event to Kafka for trending analysis

TIMELINE READ:
  1. Fetch pre-computed timeline from Redis cache
  2. For followed celebrities: fetch latest tweets on-the-fly
  3. Merge, rank, paginate
  4. Return
```

---

## 3. Trending Topics

```
TRENDING DETECTION PIPELINE:

  Tweets ──► Kafka ──► Stream Processor (Flink/Kafka Streams)
                              │
                       ┌──────┴───────┐
                       │ Sliding      │
                       │ Window       │
                       │ Count        │
                       │              │
                       │ Count tweets │
                       │ per hashtag  │
                       │ in last 5min │
                       └──────┬───────┘
                              │
                       ┌──────▼───────┐
                       │ Anomaly      │
                       │ Detection    │
                       │              │
                       │ Is this      │
                       │ hashtag      │
                       │ growing      │
                       │ abnormally?  │
                       └──────┬───────┘
                              │
                       ┌──────▼───────┐
                       │ Trending     │
                       │ Cache        │
                       │ (Redis)      │
                       │              │
                       │ Top 10       │
                       │ per region   │
                       └──────────────┘
  
  Trending = spikes above normal volume, not just high volume
```

---

## 4. Key Points for Interview

1. **Hybrid fan-out** is the core of Twitter's timeline design
2. **Redis sorted sets** for timeline cache (tweet_id, score=timestamp)
3. **Cassandra** for tweet storage (write-heavy, time-ordered)
4. **Elasticsearch** for tweet search (full-text search on content)
5. **Kafka + stream processing** for trending topic detection
6. **Snowflake IDs** for globally unique, time-sortable tweet IDs
7. **Celebrity handling**: separate path (fan-out on read) for high-follower accounts
