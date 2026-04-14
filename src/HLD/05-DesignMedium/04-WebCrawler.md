# Design Web Crawler

> **Difficulty:** Medium | **Frequency:** ★★★★☆ | **Companies:** Google, Amazon, Microsoft
> **Source:** Alex Xu Vol 1 Chapter 9

---

## 1. Requirements

### Functional
- Given seed URLs, crawl the web by following links
- Store downloaded pages for indexing
- Handle politeness (don't overload websites)
- Detect and avoid duplicate content

### Non-Functional
- Crawl 1 billion pages per month
- Scalable, robust, extensible

### Scale
- 1B pages/month ÷ 30 days ÷ 86,400 ≈ 400 pages/second
- Average page: 500 KB → storage: 500 TB/month

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        WEB CRAWLER                                    │
│                                                                      │
│  ┌────────────┐     ┌──────────────┐     ┌────────────────┐         │
│  │  Seed URLs │────►│  URL         │────►│  URL           │         │
│  │            │     │  Frontier    │     │  Deduplicator  │         │
│  └────────────┘     │  (Priority   │     │  (Bloom Filter)│         │
│                     │   Queue)     │     └────────┬───────┘         │
│                     └──────────────┘              │                  │
│                           ▲                       ▼                  │
│                           │              ┌────────────────┐         │
│                    New URLs               │  DNS Resolver  │         │
│                    extracted              │  (with cache)  │         │
│                           │              └────────┬───────┘         │
│                           │                       ▼                  │
│                     ┌─────┴──────┐       ┌────────────────┐         │
│                     │   Link     │       │  Fetcher       │         │
│                     │  Extractor │◄──────│  (HTTP client) │         │
│                     └────────────┘       │  Respect       │         │
│                           │              │  robots.txt    │         │
│                           │              └────────┬───────┘         │
│                     ┌─────┴──────┐                │                  │
│                     │  Content   │                ▼                  │
│                     │  Parser    │       ┌────────────────┐         │
│                     └────────────┘       │  Content Seen? │         │
│                                          │  (Simhash/     │         │
│                                          │   checksum)    │         │
│                                          └────────┬───────┘         │
│                                                   │                  │
│                                                   ▼                  │
│                                          ┌────────────────┐         │
│                                          │  Storage       │         │
│                                          │  (S3 + DB)     │         │
│                                          └────────────────┘         │
└──────────────────────────────────────────────────────────────────────┘

CRAWL LOOP:
  1. Pick URL from frontier (priority queue)
  2. Check robots.txt (respect crawl rules)
  3. Fetch page (HTTP GET)
  4. Check if content already seen (dedup)
  5. Parse HTML, extract links
  6. Filter + dedup new URLs (Bloom filter)
  7. Add new URLs to frontier
  8. Store page content
  9. Repeat
```

---

## 3. Key Components

### URL Frontier (Priority Queue)
```
  Prioritization:
  - Important pages first (PageRank, domain authority)
  - Freshness (frequently updated pages crawled more often)
  
  Politeness:
  - Per-domain queue (max 1 request per second to any domain)
  
  ┌────────────────────────────────────────┐
  │  Priority Queue                        │
  │  ┌──────────┐  ┌──────────┐           │
  │  │ High Pri │  │ Low Pri  │           │
  │  │ google.. │  │ blog..   │           │
  │  └──────────┘  └──────────┘           │
  │                                        │
  │  Per-Host Queues (politeness):         │
  │  ┌──────────┐  ┌──────────┐           │
  │  │ host: A  │  │ host: B  │           │
  │  │ url1,url2│  │ url3     │           │
  │  │ rate:1/s │  │ rate:1/s │           │
  │  └──────────┘  └──────────┘           │
  └────────────────────────────────────────┘
```

### URL Deduplication (Bloom Filter)
```
  Before adding URL to frontier:
  Check Bloom filter → "Definitely not seen" → Add to frontier
                     → "Probably seen" → Skip

  Billions of URLs → Bloom filter uses only a few GB of memory
  vs. storing all URLs in a set → hundreds of GB
```

### Content Deduplication
```
  Same content at different URLs (mirrors, syndication)
  
  Approach: Simhash (locality-sensitive hashing)
  - Compute fingerprint of page content
  - Compare fingerprints: similar content → similar hash
  - Threshold: if hamming distance < 3 → consider duplicate
```

---

## 4. Key Points for Interview

1. **BFS approach** with a priority queue (URL frontier)
2. **Bloom filter** for URL deduplication (space-efficient)
3. **Robots.txt** compliance (politeness)
4. **Per-host rate limiting** to avoid overloading sites
5. **DNS caching** to avoid DNS lookups per request
6. **Content deduplication** via Simhash/checksums
7. **Distributed crawling** with multiple workers pulling from shared frontier
