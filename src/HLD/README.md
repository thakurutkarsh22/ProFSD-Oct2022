# System Design (HLD) Interview Preparation - Complete Guide

> **For:** 7+ years Backend Engineering experience  
> **Sources:** Alex Xu (ByteByteGo), Arpit Bhayani, LeetCode, Grokking System Design, FAANG Engineering Blogs  
> **Approach:** Easy → Medium → Hard | Concepts first, then Design Questions

---

## Learning Roadmap (Follow This Sequence)

```
┌──────────────────────────────────────────────────────────────────────┐
│                    SYSTEM DESIGN LEARNING PATH                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  PHASE 1: FOUNDATIONS (Week 1-2)                    [EASY]           │
│  ├── Networking (DNS, HTTP, TCP/UDP, WebSockets)                     │
│  ├── API Design (REST, GraphQL, gRPC)                                │
│  ├── Database Fundamentals (SQL vs NoSQL, ACID, Indexing)            │
│  ├── Caching Basics (Strategies, Redis, Memcached)                   │
│  └── Load Balancing (Algorithms, L4 vs L7)                           │
│                         │                                            │
│                         ▼                                            │
│  PHASE 2: BUILDING BLOCKS (Week 3-4)               [EASY-MEDIUM]    │
│  ├── CAP Theorem & Consistency Models                                │
│  ├── Database Scaling (Replication, Sharding, Partitioning)          │
│  ├── Consistent Hashing                                              │
│  ├── Message Queues (Kafka, RabbitMQ)                                │
│  ├── CDN & Proxies                                                   │
│  ├── API Gateway & Service Discovery                                 │
│  ├── Back-of-the-Envelope Estimation                                 │
│  └── Bloom Filters & Probabilistic Data Structures                   │
│                         │                                            │
│                         ▼                                            │
│  PHASE 3: ADVANCED CONCEPTS (Week 5-6)              [MEDIUM-HARD]   │
│  ├── Distributed Systems Fundamentals                                │
│  ├── Consensus (Paxos, Raft, ZAB)                                   │
│  ├── Leader Election                                                 │
│  ├── Distributed Transactions (2PC, Saga)                            │
│  ├── Rate Limiting Algorithms                                        │
│  ├── Search & Indexing (Inverted Index, Elasticsearch)               │
│  ├── Stream Processing (Kafka Streams, Flink)                        │
│  ├── Object Storage & Blob Storage                                   │
│  └── Microservices vs Monolith                                       │
│                         │                                            │
│                         ▼                                            │
│  PHASE 4: DESIGN QUESTIONS - EASY (Week 7-8)                        │
│  ├── Design URL Shortener (TinyURL)                                  │
│  ├── Design Paste Bin                                                │
│  ├── Design Rate Limiter                                             │
│  ├── Design Unique ID Generator                                      │
│  └── Design Key-Value Store                                          │
│                         │                                            │
│                         ▼                                            │
│  PHASE 5: DESIGN QUESTIONS - MEDIUM (Week 9-11)                     │
│  ├── Design Chat System (WhatsApp/Messenger)                         │
│  ├── Design Notification System                                      │
│  ├── Design News Feed (Facebook/Twitter)                             │
│  ├── Design Web Crawler                                              │
│  ├── Design Autocomplete / Typeahead                                 │
│  ├── Design YouTube / Video Streaming                                │
│  ├── Design Instagram / Photo Sharing                                │
│  ├── Design Twitter                                                  │
│  └── Design Distributed Cache                                        │
│                         │                                            │
│                         ▼                                            │
│  PHASE 6: DESIGN QUESTIONS - HARD (Week 12-14)                      │
│  ├── Design Google Maps                                              │
│  ├── Design Google Docs (Collaborative Editing)                      │
│  ├── Design Payment System (Stripe)                                  │
│  ├── Design Uber / Ride Sharing                                      │
│  ├── Design Zoom (Video Conferencing)                                │
│  ├── Design Stock Exchange / Trading System                          │
│  ├── Design Search Engine                                            │
│  ├── Design Food Delivery (DoorDash)                                 │
│  └── Design Distributed File System (Google Drive)                   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## File Index (Study in This Order)

### Phase 1: Foundations [Easy]
| # | Topic | File | Time |
|---|-------|------|------|
| 1 | Networking Basics | [01-Fundamentals/01-Networking.md](01-Fundamentals/01-Networking.md) | 2 hrs |
| 2 | API Design | [01-Fundamentals/02-APIDesign.md](01-Fundamentals/02-APIDesign.md) | 2 hrs |
| 3 | Database Fundamentals | [01-Fundamentals/03-Databases.md](01-Fundamentals/03-Databases.md) | 3 hrs |
| 4 | Caching | [01-Fundamentals/04-Caching.md](01-Fundamentals/04-Caching.md) | 2 hrs |
| 5 | Load Balancing | [01-Fundamentals/05-LoadBalancing.md](01-Fundamentals/05-LoadBalancing.md) | 1.5 hrs |

### Phase 2: Building Blocks [Easy-Medium]
| # | Topic | File | Time |
|---|-------|------|------|
| 6 | CAP Theorem & Consistency | [02-BuildingBlocks/01-CAPTheorem.md](02-BuildingBlocks/01-CAPTheorem.md) | 2 hrs |
| 7 | Database Scaling | [02-BuildingBlocks/02-DatabaseScaling.md](02-BuildingBlocks/02-DatabaseScaling.md) | 3 hrs |
| 8 | Consistent Hashing | [02-BuildingBlocks/03-ConsistentHashing.md](02-BuildingBlocks/03-ConsistentHashing.md) | 2 hrs |
| 9 | Message Queues | [02-BuildingBlocks/04-MessageQueues.md](02-BuildingBlocks/04-MessageQueues.md) | 2 hrs |
| 10 | CDN & Proxies | [02-BuildingBlocks/05-CDNAndProxies.md](02-BuildingBlocks/05-CDNAndProxies.md) | 1.5 hrs |
| 11 | Back-of-Envelope Estimation | [02-BuildingBlocks/06-Estimation.md](02-BuildingBlocks/06-Estimation.md) | 2 hrs |
| 12 | Bloom Filters & Data Structures | [02-BuildingBlocks/07-BloomFilters.md](02-BuildingBlocks/07-BloomFilters.md) | 1.5 hrs |

### Phase 3: Advanced Concepts [Medium-Hard]
| # | Topic | File | Time |
|---|-------|------|------|
| 13 | Distributed Systems | [03-AdvancedConcepts/01-DistributedSystems.md](03-AdvancedConcepts/01-DistributedSystems.md) | 3 hrs |
| 14 | Consensus & Leader Election | [03-AdvancedConcepts/02-Consensus.md](03-AdvancedConcepts/02-Consensus.md) | 2 hrs |
| 15 | Distributed Transactions | [03-AdvancedConcepts/03-DistributedTransactions.md](03-AdvancedConcepts/03-DistributedTransactions.md) | 2 hrs |
| 16 | Search & Indexing | [03-AdvancedConcepts/04-SearchAndIndexing.md](03-AdvancedConcepts/04-SearchAndIndexing.md) | 2 hrs |
| 17 | Stream Processing | [03-AdvancedConcepts/05-StreamProcessing.md](03-AdvancedConcepts/05-StreamProcessing.md) | 2 hrs |
| 18 | Microservices Architecture | [03-AdvancedConcepts/06-Microservices.md](03-AdvancedConcepts/06-Microservices.md) | 2 hrs |

### Phase 4: Design Questions - Easy
| # | Question | File | Frequency |
|---|----------|------|-----------|
| 19 | Design URL Shortener | [04-DesignEasy/01-URLShortener.md](04-DesignEasy/01-URLShortener.md) | ★★★★★ |
| 20 | Design Paste Bin | [04-DesignEasy/02-PasteBin.md](04-DesignEasy/02-PasteBin.md) | ★★★☆☆ |
| 21 | Design Rate Limiter | [04-DesignEasy/03-RateLimiter.md](04-DesignEasy/03-RateLimiter.md) | ★★★★★ |
| 22 | Design Unique ID Generator | [04-DesignEasy/04-UniqueIDGenerator.md](04-DesignEasy/04-UniqueIDGenerator.md) | ★★★★☆ |
| 23 | Design Key-Value Store | [04-DesignEasy/05-KeyValueStore.md](04-DesignEasy/05-KeyValueStore.md) | ★★★★☆ |

### Phase 5: Design Questions - Medium
| # | Question | File | Frequency |
|---|----------|------|-----------|
| 24 | Design Chat System | [05-DesignMedium/01-ChatSystem.md](05-DesignMedium/01-ChatSystem.md) | ★★★★★ |
| 25 | Design Notification System | [05-DesignMedium/02-NotificationSystem.md](05-DesignMedium/02-NotificationSystem.md) | ★★★★☆ |
| 26 | Design News Feed | [05-DesignMedium/03-NewsFeed.md](05-DesignMedium/03-NewsFeed.md) | ★★★★★ |
| 27 | Design Web Crawler | [05-DesignMedium/04-WebCrawler.md](05-DesignMedium/04-WebCrawler.md) | ★★★★☆ |
| 28 | Design Autocomplete | [05-DesignMedium/05-Autocomplete.md](05-DesignMedium/05-Autocomplete.md) | ★★★★☆ |
| 29 | Design YouTube | [05-DesignMedium/06-YouTube.md](05-DesignMedium/06-YouTube.md) | ★★★★★ |
| 30 | Design Instagram | [05-DesignMedium/07-Instagram.md](05-DesignMedium/07-Instagram.md) | ★★★★☆ |
| 31 | Design Twitter | [05-DesignMedium/08-Twitter.md](05-DesignMedium/08-Twitter.md) | ★★★★★ |
| 32 | Design Distributed Cache | [05-DesignMedium/09-DistributedCache.md](05-DesignMedium/09-DistributedCache.md) | ★★★★☆ |

### Phase 6: Design Questions - Hard
| # | Question | File | Frequency |
|---|----------|------|-----------|
| 33 | Design Google Maps | [06-DesignHard/01-GoogleMaps.md](06-DesignHard/01-GoogleMaps.md) | ★★★★☆ |
| 34 | Design Google Docs | [06-DesignHard/02-GoogleDocs.md](06-DesignHard/02-GoogleDocs.md) | ★★★★★ |
| 35 | Design Payment System | [06-DesignHard/03-PaymentSystem.md](06-DesignHard/03-PaymentSystem.md) | ★★★★★ |
| 36 | Design Uber | [06-DesignHard/04-Uber.md](06-DesignHard/04-Uber.md) | ★★★★★ |
| 37 | Design Zoom | [06-DesignHard/05-Zoom.md](06-DesignHard/05-Zoom.md) | ★★★★☆ |
| 38 | Design Stock Exchange | [06-DesignHard/06-StockExchange.md](06-DesignHard/06-StockExchange.md) | ★★★☆☆ |
| 39 | Design Search Engine | [06-DesignHard/07-SearchEngine.md](06-DesignHard/07-SearchEngine.md) | ★★★★☆ |
| 40 | Design Food Delivery | [06-DesignHard/08-FoodDelivery.md](06-DesignHard/08-FoodDelivery.md) | ★★★★☆ |
| 41 | Design Google Drive | [06-DesignHard/09-GoogleDrive.md](06-DesignHard/09-GoogleDrive.md) | ★★★★★ |

---

## The 4-Step Framework for ANY System Design Interview

```
┌─────────────────────────────────────────────────────────────────┐
│              SYSTEM DESIGN INTERVIEW FRAMEWORK                  │
│                   (45-60 minute interview)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  STEP 1: REQUIREMENTS & SCOPE (5 min)                           │
│  ├── Functional Requirements (What does the system DO?)         │
│  ├── Non-Functional Requirements (Scale, Latency, Availability) │
│  ├── Constraints & Assumptions                                  │
│  └── Back-of-envelope estimation                                │
│                                                                 │
│  STEP 2: HIGH-LEVEL DESIGN (10-15 min)                          │
│  ├── API Design (endpoints, params, responses)                  │
│  ├── Data Model (schema, relationships)                         │
│  ├── Core Architecture (draw the boxes & arrows)                │
│  └── Walk through a user flow end-to-end                        │
│                                                                 │
│  STEP 3: DEEP DIVE (15-20 min)                                  │
│  ├── Pick 2-3 interesting components to go deep                 │
│  ├── Discuss trade-offs for each decision                       │
│  ├── Handle edge cases & failure scenarios                      │
│  └── Show you know WHY, not just WHAT                           │
│                                                                 │
│  STEP 4: WRAP UP (5-10 min)                                     │
│  ├── Bottlenecks & how to address them                          │
│  ├── Monitoring, Alerting, Observability                        │
│  ├── Scaling strategies (what breaks at 10x, 100x?)             │
│  └── Future improvements                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Numbers to Memorize

```
┌────────────────────────────────────────────────────────────┐
│              LATENCY NUMBERS (Approximate)                  │
├────────────────────────────────────────────────────────────┤
│  L1 cache reference .................... 0.5 ns             │
│  L2 cache reference ....................   7 ns             │
│  Main memory reference ................. 100 ns             │
│  SSD random read ....................... 150 μs             │
│  HDD seek ............................. 10  ms              │
│  Send packet CA → Netherlands → CA ..... 150 ms             │
│  Read 1 MB sequentially from memory .... 250 μs             │
│  Read 1 MB sequentially from SSD ....... 1   ms             │
│  Read 1 MB sequentially from HDD ....... 20  ms             │
│  Round trip within same datacenter ..... 0.5 ms             │
├────────────────────────────────────────────────────────────┤
│              SCALE NUMBERS                                  │
├────────────────────────────────────────────────────────────┤
│  QPS a single web server can handle .... 1K-10K             │
│  QPS a single DB can handle ............ 1K-10K             │
│  QPS a single Redis can handle ......... 100K+              │
│  Characters in a URL ................... ~100 bytes          │
│  Characters in a tweet ................. ~300 bytes          │
│  Average web page ...................... ~2 MB               │
│  Average photo ......................... ~200 KB             │
│  Average short video ................... ~50 MB              │
│  1 Million requests/day ................ ~12 QPS             │
│  1 Billion requests/day ................ ~12K QPS            │
├────────────────────────────────────────────────────────────┤
│              STORAGE NUMBERS                                │
├────────────────────────────────────────────────────────────┤
│  1 KB  = 1,000 bytes                                        │
│  1 MB  = 1,000 KB    = 10^6 bytes                           │
│  1 GB  = 1,000 MB    = 10^9 bytes                           │
│  1 TB  = 1,000 GB    = 10^12 bytes                          │
│  1 PB  = 1,000 TB    = 10^15 bytes                          │
├────────────────────────────────────────────────────────────┤
│              AVAILABILITY NUMBERS                           │
├────────────────────────────────────────────────────────────┤
│  99%    (two 9s)  = 3.65 days/year downtime                 │
│  99.9%  (three 9s) = 8.77 hours/year                        │
│  99.99% (four 9s)  = 52.6 minutes/year                      │
│  99.999% (five 9s) = 5.26 minutes/year                      │
└────────────────────────────────────────────────────────────┘
```

---

## Top Engineering Blogs to Follow

| Company | Blog URL | Best For |
|---------|----------|----------|
| Netflix | medium.com/netflix-techblog | Streaming, Microservices, Resilience |
| Uber | eng.uber.com | Real-time, Geo, Marketplace |
| Meta | engineering.fb.com | Social, Scaling, Caching |
| Google | cloud.google.com/blog | Distributed Systems, Infra |
| Amazon | aws.amazon.com/blogs | Cloud Architecture, Scaling |
| Stripe | stripe.com/blog/engineering | Payments, API Design |
| Slack | slack.engineering | Messaging, Real-time |
| Discord | discord.com/blog | Real-time, Scaling |
| Spotify | engineering.atspotify.com | Streaming, Data Pipelines |
| Pinterest | medium.com/pinterest-engineering | Feed, Recommendations |
| LinkedIn | engineering.linkedin.com | Search, Social Graph |
| Twitter | blog.twitter.com/engineering | Timeline, Real-time |
| Airbnb | medium.com/airbnb-engineering | Search, Matching |
| Shopify | shopify.engineering | E-commerce, Scaling |
| Dropbox | dropbox.tech | Storage, Sync |

---

## Recommended Study Resources

| Resource | Type | Level |
|----------|------|-------|
| Alex Xu - System Design Interview Vol 1 | Book | Beginner-Medium |
| Alex Xu - System Design Interview Vol 2 | Book | Medium-Hard |
| ByteByteGo Newsletter | Newsletter | All |
| Arpit Bhayani - System Design for Beginners | Course | Beginner |
| Arpit Bhayani - System Design Masterclass | Course | Advanced |
| Grokking System Design (Educative) | Course | All |
| System Design Primer (GitHub) | Free | All |
| HelloInterview.com | Free Practice | All |
| Martin Kleppmann - Designing Data-Intensive Apps | Book | Advanced |
| LeetCode System Design Course | Course | All |
