# Design YouTube — Simplified

> **Difficulty:** Hard &nbsp;|&nbsp; **Companion to:** [`06-DesignYouTube.md`](./06-DesignYouTube.md) (the full ~3000-line treatment).
>
> **Why this file exists.** The full doc covers everything — ContentID, DRM, Premieres, Super Chat, Kids profile, multi-language ASR, monetisation pipelines, the lot. That's great for revision but too much for a 45-min interview or a first read. This file keeps **only the load-bearing essentials** — the things you'd actually whiteboard — and pairs each one with the **real-world failure** that motivates the design choice. If a section doesn't appear here, it's because you can survive an interview without it.
>
> **What we cut from the full version.** DRM (rare path), ContentID deep dive, multi-language ASR, Premieres / Shorts / Live chat / Super Chat / Channel memberships / Premium downloads, the moderation human-review console, the analytics dashboards, the trending Count-Min Sketch, the search re-ranker DNN, the offline ML training infra, the legal / DMCA workflow, the lazy ladder backfill, the per-region royalty pipeline. All of those exist in the full doc; none of them is on the critical path for "explain how YouTube works".
>
> **What we keep.** Upload → transcode → store. Watch → manifest → CDN → segments. View counter (the famous one). Recommendation (the two-stage shape, no ML deep-dive). Live streaming (the latency story). Comments / likes / notifications (one paragraph each). And then a long section of **real-world failures** that show why each design choice exists.

---

## Table of Contents

1. [Problem & Scale (the only numbers you need)](#1-problem--scale-the-only-numbers-you-need)
2. [Functional Requirements (the essential five)](#2-functional-requirements-the-essential-five)
3. [Architecture in One Diagram](#3-architecture-in-one-diagram)
4. [The Storage Layer (one table per concern)](#4-the-storage-layer-one-table-per-concern)
5. [Upload + Transcoding Pipeline](#5-upload--transcoding-pipeline)
6. [Watch Flow — The 1-Second TTFF Budget](#6-watch-flow--the-1-second-ttff-budget)
7. [The View Counter (the famous interview question)](#7-the-view-counter-the-famous-interview-question)
8. [Recommendation in Two Stages](#8-recommendation-in-two-stages)
9. [Live Streaming — Why It's a Different Pipeline](#9-live-streaming--why-its-a-different-pipeline)
10. [Engagement — Comments, Likes, Subscriptions](#10-engagement--comments-likes-subscriptions)
11. [Real-World Failures (the heart of this doc)](#11-real-world-failures-the-heart-of-this-doc)
12. [Tech-Stack Cheat-Sheet](#12-tech-stack-cheat-sheet)
13. [Top 10 Q&A](#13-top-10-qa)

---

## 1. Problem & Scale (the only numbers you need)

Design a planet-scale UGC video platform. Anyone uploads, anyone watches, recommendations personalise, view counts are honest, comments don't drown, live works.

Memorise these eight numbers — every architectural choice falls out of them:

| Metric | Value | Why it forces a design decision |
|--------|-------|---------------------------------|
| DAU | **800 M** | API gateway must be regionally sharded, not a single cluster |
| Peak concurrent watchers | **200 M** | One CDN can't serve this; **multi-CDN + ISP-cache mandatory** |
| Watch-hours / day | **~1 B** | Avg 3 Mbps × 200 M peak ≈ **250 Tbps egress** — > 95% must come from edge cache |
| Upload rate | **500 hours / minute** | Transcoding must be **GOP-distributed** (split video into 10s chunks, encode in parallel) |
| Total videos at rest | **~10 B** | Recommendation candidate-gen can't enumerate; needs **two-stage retrieval + ranker** |
| Storage at rest | **~5 EB** (after CMAF + cold tiering) | Use **CMAF source** (one encode, two manifests — HLS + DASH) to halve storage |
| View beacons | **~500 K / s peak** | Cannot `UPDATE counter += 1` in Postgres; needs **Redis HLL + Flink rollup** |
| TTFF (tap → first frame) | **p99 ≤ 1 s** | Forces **manifest pre-built**, **CDN PoP within 50 km**, **TLS 1.3 + 0-RTT** |

> **The cardinality story** is the entire problem: Netflix has ~50 K titles, YouTube has ~10 B. That five-orders-of-magnitude gap is why nothing can be enumerated, pre-warmed everywhere, or kept in one shard. Say this on the whiteboard in the first 60 seconds.

---

## 2. Functional Requirements (the essential five)

Stripped to the bone:

1. **Upload** — creator selects a file → resumable chunked upload to object store → server kicks off transcoding → video is "Published" within ~60 s for a 5-min 1080p.
2. **Watch** — viewer taps a video → server returns a signed manifest URL → player streams ABR segments from a CDN edge → first frame on screen within 1 s.
3. **View counter** — count a "view" exactly once per `(user, video, day)` after ≥ 30 s of monotonic playback; surface the count to the watch page within 60 s.
4. **Recommendation** — render a personalised home feed within 100 ms; "Up Next" panel shown alongside the player.
5. **Engagement** — comments, likes, subscriptions; new-upload notifications fan out to subscribers within ~5 s.

Everything else (Live, Search, ContentID, DRM, monetisation, captions, downloads, Kids, Shorts) is layered on the same primitives. We design **only the five above** in detail; Live gets a brief section because it's a different pipeline shape.

### Non-functional must-haves

| Concern | Target |
|---------|--------|
| Watch availability | **99.99 %** (52 min/year budget) |
| Watch TTFF | **p99 ≤ 1 s** cache-hit, ≤ 1.8 s cold |
| Watch API (manifest) | **p99 ≤ 80 ms** |
| Recommendation | **p99 ≤ 100 ms** |
| Upload-to-publish (5 min 1080p) | **p99 ≤ 60 s** |
| View count freshness | **p99 ≤ 60 s** |
| Metadata consistency | **strong** (Spanner) — privacy flips must be instant |
| View / comment / reco consistency | **eventual** (≤ 60 s / ≤ 5 s / ≤ 5 min) |

---

## 3. Architecture in One Diagram

> **Editable diagram:** [`assets/06b-design-youtube-simplified-hld.drawio`](./assets/06b-design-youtube-simplified-hld.drawio) — open in Cursor/VS Code with the `hediet.vscode-drawio` extension, or in [diagrams.net](https://app.diagrams.net/). The ASCII version below is the same shape, suitable for whiteboarding.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                  CLIENTS                                             │
│   Mobile · Web · Smart TV · Console · Creator Studio · Live Encoder (OBS)            │
└──┬──────────────────────────────────────────────┬───────────────────────────────────┘
   │ control plane (REST: /watch, /upload, /home) │ data plane (segments, manifests)
   │  ~5 % of bytes                               │ ~95 % of bytes
   ▼                                              ▼
┌──────────────┐         ┌────────────────────────────────────────────────────┐
│ API Gateway  │         │  Multi-CDN (Akamai · CloudFront · Cloudflare)       │
│  · TLS term  │         │  + ~15 K ISP-resident cache appliances              │
│  · JWT auth  │         │  Hit ratio ≥ 95 %                                   │
│  · GeoIP     │         └──────────────────┬─────────────────────────────────┘
│  · Rate-limit│                            │ on cache miss
└─┬──┬──┬──┬──┘                             ▼
  │  │  │  │                         ┌──────────────┐
  │  │  │  └──→ Engagement Svc       │ Origin Shield │   (regional NGINX/Varnish)
  │  │  └────→ Recommendation Svc    │  → GCS / S3   │
  │  └──────→ Watch / Manifest Svc   └──────────────┘
  └─────────→ Upload Svc
        │
        ▼ (writes to)
┌──────────────────────────────────────────────────────────────────────────────────┐
│                            DATA STORES                                            │
│  Spanner: videos, channels, subscriptions  (strong, sharded by video_id)          │
│  Bigtable: comments, watch_events, view_counters  (high-write, eventual)          │
│  Redis: hot metadata, view-counter HLL, reco rows, dedup sets, rate-limit tokens  │
│  GCS / S3: raw uploads + transcoded CMAF segments                                 │
│  Elasticsearch + Vespa: search index                                              │
└──────────────────────────────────────────────────────────────────────────────────┘
        ▲                                ▲
        │ produce                        │ consume
        │                                │
┌───────┴────────────────────────────────┴────────────────────────────────────────┐
│                       Kafka backbone (RF = 3)                                    │
│  video.uploaded · video.transcoded · watch.events · engagement.events ·          │
│  notify.events · features.stream · audit.events · live.segments                  │
└─────────────────┬────────────────────┬────────────────────┬─────────────────────┘
                  ▼                    ▼                    ▼
        ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────────┐
        │ Transcoding pool │  │ Flink streaming │  │ Notification fan-out     │
        │ (GPU farm,       │  │ (view counter,  │  │ (FCM / APNS / SES / WS)  │
        │  GOP-parallel)   │  │  feature store) │  │                          │
        └──────────────────┘  └─────────────────┘  └──────────────────────────┘
```

**Six things to point at on the whiteboard:**

1. **Two planes** — control plane (REST APIs, ~5 % of bytes) vs data plane (CDN segments, ~95 % of bytes). The CDN is *the* product; the API is just a manifest signer.
2. **Multi-CDN + ISP-cache.** No single CDN handles 250 Tbps; a CDN PoP outage during a marquee event would crater 30 % of viewers. We run 3+ CDNs in parallel + ~15 K appliances *inside* ISPs.
3. **Origin shield** sits between the CDNs and the object store — without it, every CDN miss hits GCS, multiplying origin egress 200×.
4. **Two databases by access pattern** — Spanner for low-write strong-consistency metadata, Bigtable for high-write eventual-consistency events.
5. **Kafka is the backbone** — every domain event (upload, transcode-complete, view, like, comment) is produced once and fanned out to many consumers.
6. **Transcoding pool is its own scaling axis** — independent of watch traffic. A viral upload spikes the encoder pool, not the watch pool, and vice versa.

---

## 4. The Storage Layer (one table per concern)

Three tables you can write on a napkin:

### `videos` (Spanner — strong)

```sql
CREATE TABLE videos (
  video_id        STRING(64) NOT NULL,           -- ULID; sortable by time
  channel_id      STRING(64) NOT NULL,
  title           STRING(MAX),
  description     STRING(MAX),
  duration_ms     INT64,
  privacy         STRING(16),                    -- PUBLIC / UNLISTED / PRIVATE
  ingest_status   STRING(16),                    -- UPLOADING / PROCESSING / PUBLISHED / FAILED
  country_block   ARRAY<STRING(2)>,              -- e.g., ['DE', 'IN']
  age_restricted  BOOL,
  created_at      TIMESTAMP,
  published_at    TIMESTAMP,
) PRIMARY KEY (video_id);
```

Sharded by `video_id` (ULID — first 48 bits are time, so writes spread). The privacy flip ("make this private") must be a strong write — a viewer must not see a private video even if our cache says PUBLIC. We invalidate the cache key explicitly on the write.

### `comments` (Bigtable — eventual, wide-row)

```
Row key: video_id + '#' + reverse_ts_ms + '#' + comment_id
Columns: c:user_id, c:text, c:parent_id, c:like_count, c:reply_count, c:moderation_state
```

Reverse timestamp puts newest first; `Top` ordering needs a secondary refresh column. A viral video lives on one row range — Bigtable splits the range automatically when it gets hot.

### `view_counters` (Bigtable + Redis HLL)

```
Bigtable: row=video_id, col=day_YYYYMMDD_shard_N (16 shards)
Redis:    HINCRBY view_counters_hot:{video_id} {day} +N    (TTL 30 s on display copy)
Redis:    PFADD viewers:{video_id}:{day} {user_id}         (HLL for unique viewers)
```

Sharded across 16 columns to spread the hot row's write load (a viral video gets 100 K/sec of increments). See §7 for the full story.

That's it. Spanner has 3-4 more tables (`channels`, `subscriptions`, `users`) modelled the same way; Bigtable has `watch_events` (raw beacon append-only). Search lives in Elasticsearch + Vespa, fed by a Kafka indexer; ML feature store lives in Bigtable. None of those decisions are interesting at the simplification level.

---

## 5. Upload + Transcoding Pipeline

The pipeline does six things, in order:

```
[Creator]
   │ 1. POST /upload/init → server returns signed GCS resumable URL + chunk_size 8 MB
   ▼
[Browser / Mobile]
   │ 2. PUT chunks DIRECTLY to GCS (bypassing our app servers)
   │    Each chunk has Content-Range: bytes A-B/Total
   │    GCS reports progress via Pub/Sub → Redis tracks last_completed_offset (TTL 30 d)
   ▼
[On final chunk]
   │ 3. POST /upload/complete → Upload Svc verifies SHA, INSERTs videos row
   │    (ingest_status='UPLOADING'), produces video.uploaded to Kafka
   ▼
[Workflow Orchestrator (Argo / Temporal)]
   │ 4. Fans out three parallel branches:
   │    a. Transcoding pool — splits video into 10-s GOPs, encodes each in parallel
   │       across N workers, produces 7 ABR rungs (144p / 240p / 360p / 480p / 720p
   │       / 1080p / 1440p) as CMAF segments
   │    b. ASR captions — speech-to-text per language
   │    c. Thumbnail picker — CV model picks 3 candidate frames, A/B test in-the-wild
   ▼
[Packager (Shaka)]
   │ 5. Wraps CMAF segments into HLS .m3u8 + DASH .mpd manifests (one encode, two manifests)
   ▼
[Publish]
   │ 6. UPDATE videos SET ingest_status='PUBLISHED'
   │    Produce video.transcoded to Kafka → search index updates, notifications fan out,
   │    CDN pre-warm (only for predicted-viral videos)
```

### Why each piece exists

- **Direct-to-GCS chunked upload.** If we routed 10 PB/day through our own pods, bandwidth alone costs more than the whole company makes. GCS scales to terabytes/sec; our app servers don't. Bonus: resumability — a dropped chunk just retries from `last_completed_offset` in Redis.
- **GOP-level parallelism.** A 1-hour 1080p video at 1× real-time on one machine = 1 hour wall-clock. The user expects ≤ 60 s for a 5-min 1080p. Split into ~360 × 10-s GOPs, encode each in parallel across 30 workers → ~3 min wall-clock for the 1-hour video, ~50 s for the 5-min one.
- **CMAF source, two manifests.** Pre-CMAF, you encoded once for HLS (`.ts`) and again for DASH (`.m4s`), doubling storage. CMAF defines a single fragmented MP4 (`.cmfv`) that both HLS and DASH point to. Cuts our packaged storage from ~10 EB to ~5 EB.
- **Lazy ladder generation.** 90 % of uploads get < 100 lifetime views. Encoding 7 rungs for all of them wastes ~70 % of GPU-hours. Initial encode = 360p + 720p + 1080p only; backfill the rest if `views_24h > 1 K`.

### The single most important pipeline property

**Don't serialise — parallelise.** Transcoding, ASR, thumbnail extraction, and ContentID matching all run in parallel, not in sequence. Each can fail independently and be retried independently. The pipeline is a DAG, not a queue.

---

## 6. Watch Flow — The 1-Second TTFF Budget

```
[User taps a video]
   │ 1. GET /watch?v=<video_id>
   ▼
[API Gateway]
   │ 2. JWT validate (5 ms), GeoIP lookup (in-process, 1 ms)
   ▼
[Watch / Manifest Svc]
   │ 3. Read video metadata — Redis hit ~95 % (~2 ms); Spanner fallback (~30 ms)
   │ 4. Privacy / geo / age check — fail-fast 404 / 451 if blocked
   │ 5. Sign manifest URL (HMAC, 6-h TTL) — pre-built manifest in GCS at fixed path,
   │    we just sign the URL at request time (~1 ms)
   │ 6. Pick CDN PoP — per-region routing config in pod, refreshed every 30 s
   │ 7. Emit Kafka watch.events.PLAY_REQUESTED (async, fire-and-forget)
   ▼
   Returns: { manifest_url, captions_urls, up_next: [...] } in ~80 ms p99
   ▼
[Player]
   │ 8. Fetches manifest_url from CDN — ~80 ms cache hit
   │ 9. Parses .m3u8, picks initial bitrate (conservative 720p start)
   │10. Fetches init.mp4 + first media segment — ~200 ms each
   │11. Decoder warm-up + first frame on screen — ~100 ms
   │
   │ TOTAL p99 ≈ 1 s (cache hit) / 1.8 s (cold cache)
```

### The TTFF budget in detail

| Stage | p99 | Killed by |
|-------|-----|-----------|
| TLS handshake to PoP | 100 ms | Cold connection, TLS 1.2; mitigated by TLS 1.3 + 0-RTT |
| API Gateway + auth | 15 ms | JWT cache miss (rare) |
| Watch Svc metadata | 30 ms | Redis miss + Spanner read |
| Manifest sign + CDN pick | 25 ms | Routing config refresh stall |
| Mobile RTT to player | 80 ms | Network, not us |
| Player parses manifest | 100 ms | Player startup; no server fix |
| First segment fetch | 400 ms | **CDN cache miss** — adds 150 ms shield round-trip |
| Decoder warm-up | 200 ms | Hardware decoder init; no server fix |
| **Total p99** | **~1 s** | |

**The biggest knob is CDN cache-hit ratio.** Every percentage point you lose on edge hit-rate adds ~10 ms to p99 (because the misses go to shield, which is ~150 ms slower). Pre-positioning the head of the long tail (top 0.1 % of videos serve ~50 % of all traffic) at every PoP is non-negotiable.

### What the manifest looks like (HLS)

```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360p/index.m3u8?sig=abc&expires=...
#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
720p/index.m3u8?sig=def&expires=...
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
1080p/index.m3u8?sig=ghi&expires=...
```

The player picks a rung based on its current throughput estimate, then adapts (down on rebuffer, up on excess headroom) every few seconds. This is **ABR (Adaptive Bitrate Streaming)** — every `.ts` / `.m4s` segment is independently fetchable, so the player can switch rungs at every segment boundary.

---

## 7. The View Counter (the famous interview question)

This is the YouTube system-design canonical. Get it right and you've shown you understand hot-row contention, eventual consistency, and probabilistic data structures in 5 minutes.

### Why naive doesn't work

```python
# DON'T DO THIS
def on_play(video_id):
    db.execute("UPDATE videos SET view_count = view_count + 1 WHERE id = %s", video_id)
```

Three failures, each a classic interview gotcha:

1. **Hot-row contention.** A video at 1 M views/min sustains 1 M write locks per minute on one row → Postgres / Spanner caps at ~5 K writes/sec → 95 % of writes time out. The shard becomes a queue.
2. **No deduplication.** A user reloading the page = 100 fake views. Bots trivially inflate the count.
3. **No watch-quality signal.** A 0.5-second tap-and-leave shouldn't count. YouTube's definition: a "view" is **≥ 30 s of monotonic playback** OR completion (whichever first), deduped by `(user_id, video_id, day)`.

### The three-layer solution

```
   ┌─────────────────────────────────────────────────────────────────┐
   │  Layer 1: Beacon collector — dedup + 30-s threshold + Kafka     │
   └────────────────────────┬────────────────────────────────────────┘
                            │  watch.events.VIEW
                            ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  Layer 2: Flink — tumbling 1-min windows, INCR Bigtable + Redis │
   └────────────────────────┬────────────────────────────────────────┘
                            │
                            ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  Layer 3: Read-through Redis hot cache (TTL 30 s)               │
   └─────────────────────────────────────────────────────────────────┘
```

**Layer 1 — the beacon endpoint:**

```python
def on_beacon(user_id, video_id, cumulative_play_ms, day):
    if cumulative_play_ms < 30_000:
        return 204  # not yet a view

    # Dedup per user per video per day in a Redis Set
    added = redis.SADD(f"seen_views:{day}:{user_id}", video_id)
    if added == 0:
        return 204  # already counted today
    redis.EXPIRE(f"seen_views:{day}:{user_id}", 90_000)  # 25h TTL

    kafka.produce("watch.events", key=user_id, value={
        "type": "VIEW", "video_id": video_id, "user_id": user_id, "day": day,
    })
    return 204
```

The Set + threshold removes ~95 % of the load before it hits Kafka. The remaining ~150 K legitimate views/sec go through.

**Layer 2 — Flink rollup:**

```
source: kafka("watch.events").filter(type == "VIEW")
keyBy(video_id, day)
window: tumbling(1 min)
aggregate:
  COUNT(*) → INCR Bigtable view_counters[video_id][day]_shard_N += count
  HLL_AGG(user_id) → PFADD Redis viewers:{video_id}:{day}
  emit features.stream {video_id, count_delta_1min}
```

The 1-min window collapses many writes into one INCR. A video with 1 M views in a minute → one INCR of +1 M, not 1 M individual INCRs. The shard suffix (`_shard_0` … `_shard_15`) spreads the write across 16 columns of one Bigtable row — Bigtable splits the row when it gets hot.

**Layer 3 — read-through cache:**

```python
def get_view_count(video_id):
    vc = redis.GET(f"view_counters_hot:{video_id}")
    if vc is None:
        vc = sum(bigtable.read_columns("view_counters", video_id, "day_*_shard_*"))
        redis.SETEX(f"view_counters_hot:{video_id}", 30, vc)
    return vc
```

Total freshness: beacon → Flink window (≤ 1 min) → Bigtable + Redis → Watch page (TTL 30 s) → **≤ 60 s p99 visible to user**. That's the famous "view count is eventually consistent" SLO.

### Unique viewers via HyperLogLog

Storing `Set<user_id>` per video would cost 1.6 GB per day for a 100 M-viewer video. **HyperLogLog**:

- Per `(video_id, day)` store a single ~14 KB sketch.
- `PFADD viewers:{video_id}:{day} {user_id}` — O(1) cardinality update.
- `PFCOUNT viewers:{video_id}:{day}` — O(1) read, ±1 % error.
- `PFMERGE viewers:{video_id}:lifetime viewers:{video_id}:day1 viewers:{video_id}:day2 ...` — associative roll-up across days.

That's how Creator Studio shows "unique viewers today" without an OLAP cube.

### The famous "301 → 302" view freeze

In 2014, YouTube view counts got stuck at 301 for hours after a video went viral. The reason: above 301 views, every increment had to pass an extra anti-fraud batch check (bot detection: traffic source, IP entropy, watch pattern). The check ran hourly; the count "froze" until the batch caught up.

We replicate the *spirit*: a video spiking abnormally (e.g., view rate > 100× baseline) gets routed to a slower fraud-check path. The display shows last verified count + "verifying…" for the lag period. Cosmetic but contractually required for ad revenue accuracy.

---

## 8. Recommendation in Two Stages

Hand-wave the ML, but get the **shape** right — that's what the interviewer cares about.

```
   ┌───────────────────────────────────────────────────────────────┐
   │  Stage 1: Candidate Generation (recall, ~10 B → ~1000)        │
   │  - Collaborative filtering (users who watched X also watched Y)│
   │  - Content-based (embedding ANN over title+description+tags)  │
   │  - Subscription feed (videos from channels you sub'd to)      │
   │  - Trending in your region                                    │
   │  → Union of ~1000 candidates                                  │
   └───────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
   ┌───────────────────────────────────────────────────────────────┐
   │  Stage 2: Ranker (precision, ~1000 → top-N)                   │
   │  - Two-tower DNN: (user_features, video_features) → score      │
   │  - Watch-time prediction (the optimisation objective)         │
   │  - Diversity penalty (don't show 5 videos from the same channel)│
   │  - Freshness boost (new uploads get a temporary lift)          │
   │  → top-N ranked list                                          │
   └───────────────────────────────────────────────────────────────┘
```

### Why two stages?

You can't run an expensive DNN ranker over 10 B videos per home-page load (800 M loads/day × 10 B candidates × 1 µs per forward pass = ~10⁹ GPU-seconds/day, which is impossible). So **stage 1** uses cheap retrieval (ANN nearest-neighbours, simple collaborative-filter joins) to cut 10 B → 1000 candidates; **stage 2** runs the expensive ranker on those 1000. It's the same retrieval-then-rerank pattern as web search.

### The freshness story

Recommendations have **two refresh loops**:

- **Offline (nightly Spark + TF training):** retrains user feature vectors and the ranker model. Produces the home rows for every user, written to Redis as `home:{user_id}` (HSET of `row_id → [video_ids]`).
- **Online (Flink streaming):** consumes `watch.events` + `engagement.events`, updates per-user features (last 5 plays, current session topic) within seconds, and re-ranks the *top* of each row at request time using a small "context model".

The read path is intentionally trivial: `GET /home` → Redis `HGETALL home:{user_id}` → fast online re-rank → return. **p99 ≤ 100 ms.**

### Cold start

A brand-new user has no history. We fall back to:
- Per-country popularity row.
- Trending row (Count-Min Sketch over the last hour of `watch.events`).
- Topic exploration row (rotate through ~20 broad topics like "music", "gaming", "news").

Within ~10 watches, the offline pipeline catches up and personalises the home.

---

## 9. Live Streaming — Why It's a Different Pipeline

VOD is *latency-tolerant, cache-friendly*. Live is *latency-sensitive, cache-hostile* (segments are minted in real time and must propagate in seconds). Different pipeline, different ingest, different CDN behaviour.

### The 5-second glass-to-glass budget

```
Camera → Encoder (OBS / mobile)               100 ms
Encoder → RTMP/SRT Ingest Gateway              100 ms   (SRT preferred for lossy nets)
Ingest → Live Transcoder (NVENC GPU)            50 ms
Live Transcoder (CMAF chunked, 200ms partials)  500 ms
Packager → CDN origin                           200 ms   (HTTP/2 push)
CDN propagation to PoP                          1 s     (origin shield → regional → PoP)
PoP → player (LL-HLS partial)                  500 ms
Player buffer + decode + render                1 s
─────────────────────────────────────────────────────
TOTAL p99                                       ~3.5 s   (under the 5 s SLO)
```

### Two design choices that matter

1. **LL-HLS, not WebRTC.** WebRTC is ~200 ms but tops out at hundreds of viewers per stream (every viewer is a peer; SFU compute scales linearly). LL-HLS scales to **millions per stream** because it's HTTP-cacheable. We accept the 5 s latency for the cost saving.

2. **Active/standby ingest per stream.** Two ingest pods receive the same RTMP feed; only the **active** pod's output flows downstream. etcd lease (3 s TTL) for active election. On primary failure, the standby promotes itself within 3 s; the encoder doesn't notice (it's pushing to an LB), the viewer sees a ~1 s glitch (one segment skipped). Without this, every encoder restart on our side = the stream dies for everyone.

### DVR window + post-event archive

The Live Transcoder writes 6-s segments to GCS for the entire stream duration. Late viewers can scrub back up to 4 hours. After the stream ends, a background job re-encodes the DVR archive into proper VOD ladders → the live event becomes a permanent VOD video.

---

## 10. Engagement — Comments, Likes, Subscriptions

Three patterns, one paragraph each.

**Comments.** Bigtable wide-row table keyed by `video_id + reverse_ts + comment_id`. Newest-first ordering = contiguous range scan. Top-ordering needs a secondary "score" column (likes + replies + recency) refreshed every few minutes by a Flink job. Toxicity model runs synchronously before persist (~10 ms p95); high-toxicity → `moderation_state='HOLDED'`.

**Likes.** Eventually consistent counters. The like button shows optimistic UI immediately ("you liked this"); the actual increment is `INCR like_counter:{video_id}` in Redis (sharded across 16 shards for hot videos), flushed to Bigtable every 5 s. A double-click is deduped client-side and again server-side via `(user_id, video_id)` membership in a Redis Set.

**Subscriptions.** Spanner table — strong consistency because subscription state gates notifications and the home feed. ~30 subs per avg user × 2 B users = 60 B rows, sharded by `user_id`. The fan-out on a new upload is the hard part — see "Mr Beast notification fan-out" in §11.

**Notifications.** Kafka-driven. A `video.transcoded` event for a channel with N subscribers fans out to N notify-events. For Mr Beast (~250 M subs), naive fan-out = 250 M Kafka writes within seconds. We batch by partition (~256 partitions, ~1 M/partition), produce in parallel, and use APNS/FCM batch-send APIs (1 K tokens per call). A 250 M push-notification job completes in ~5 minutes; the user-visible push lands within ~1 minute for the first batch.

---

## 11. Real-World Failures (the heart of this doc)

Every design choice above is motivated by a *thing that broke in production at this scale*. Here are the failures, what they look like, what caused them, and how the design absorbs them.

### Failure 1 — The hot-row view counter (the 301 freeze, 2014)

- **Symptom.** A viral video's view counter is stuck at 301 for hours; creators see "0 new views" on Creator Studio while their phone buzzes with a million notifications.
- **Root cause.** Single-row `UPDATE counter += 1` on a relational DB. Lock contention on the hot row caps writes at ~5 K/sec. At 100 K views/sec, 95 % of increment attempts time out and are silently dropped.
- **What we do now.** Redis HLL + Flink 1-min rollup + Bigtable sharded counter (16 shards per row). The 5 K/sec ceiling becomes ~80 K/sec per shard × 16 shards = 1.3 M/sec capacity per video. Surface lag becomes visible to the viewer (≤ 60 s) but is *honest*.
- **What still goes wrong.** A bot army at 5 M views/sec on one video can still push past the per-shard limit. We auto-detect rate > 100× baseline → route through fraud-check (slower) and show "verifying…" on the count.

### Failure 2 — Thundering herd on a viral upload (cold cache stampede)

- **Symptom.** A creator uploads, posts to Twitter, and 1 M viewers tap within 30 seconds. The first segment of the video is at the origin shield, not at any PoP. All 1 M players miss the PoP cache simultaneously → 1 M requests to one shield node → shield falls over → CDN serves 503s → "video unavailable" trends on Twitter.
- **Root cause.** No CDN pre-warm + no request coalescing at the shield.
- **What we do now.** Two layers:
  1. **Predicted-virality pre-warm.** A model on `video.transcoded` predicts likely first-hour views; if > 100 K, the CDN Pre-warmer pushes the first 30 s of segments to all PoPs in the creator's primary region (or globally for top-100 channels) *before* the manifest is published.
  2. **Request coalescing at shield.** NGINX `proxy_cache_lock on` — when 1 M requests arrive for the same uncached object, only the first goes to origin; the rest wait for the response and fan it out. Cuts origin load by 1 M ×.
- **What still goes wrong.** A non-creator can't trigger pre-warm (e.g., a Reddit post going viral on an old video). The shield handles it via coalescing but the *first* 1 M viewers see ~500 ms cold latency. Acceptable.

### Failure 3 — CDN PoP outage during a marquee event

- **Symptom.** During the Champions League final, Akamai's London PoP fails. 30 % of UK viewers see a buffering spinner; complaints to Twitter spike; advertisers demand a refund.
- **Root cause.** Single-CDN dependency for a regional PoP.
- **What we do now.** **Multi-CDN with real-time switching.** Per-region weighting in etcd, refreshed every 30 s based on `cdn.qoe` metric (rebuffer ratio, throughput, error rate). Akamai London going dark drops its weight to 0; CloudFront and Cloudflare absorb the traffic within 30 s. The viewer sees a 1-s rebuffer (player switches PoPs at the next manifest refresh) but the stream survives.
- **What still goes wrong.** If two CDNs fail simultaneously in the same region, the third gets 3× load. We pre-provision each CDN at 50 % normal capacity (so any one can absorb a peer failure) but a *triple* failure is unsurvivable. We accept this and pay for ISP-cache appliances as a fourth tier.

### Failure 4 — The Mr Beast notification fan-out

- **Symptom.** Mr Beast uploads a video at 9 PM ET. 250 M subscribers should get a push notification. Naive implementation: 250 M Kafka messages in the next minute → Kafka brokers go red → notify backlog grows → users start getting "Mr Beast uploaded" notifications 6 hours late, after they've already watched the video.
- **Root cause.** No batching, no partitioning by recipient, no rate-limiting per channel.
- **What we do now.**
  - The publisher writes a single `video.uploaded` event with `channel_id`, not 250 M individual events.
  - The Notification Service consumes that event, queries the `subscriptions` table for subscribers in batches of 10 K, and produces `notify.events` partitioned by `recipient_user_id` (so the downstream worker pool can parallelise).
  - APNS/FCM batch-send APIs accept up to 1 K tokens per call; we batch within a partition.
  - **Total wall-clock for 250 M:** ~5 minutes; first batch hits user devices in ~30 s.
  - **Per-user dedupe** in a Redis 24-h Set so the user doesn't get 10 push notifications if Mr Beast uploads 10 videos that day (we send a daily digest above a threshold).
- **What still goes wrong.** Apple's APNS rate-limits us at ~1 M/sec; we can't push faster than that for iOS. We accept the 5-min spread and stagger the order (highest-engagement subscribers first).

### Failure 5 — The transcoding pipeline backlog

- **Symptom.** During a global event (Olympics, election), upload rate spikes 5×. The transcoding queue backs up; new uploads sit in PROCESSING for 45 minutes; creators tweet "where is my video".
- **Root cause.** Fixed GPU pool, no priority queueing, no spot-instance overflow.
- **What we do now.**
  - **Three-priority queues:** P0 = live streams (always-on capacity), P1 = paid creators / news partners (SLO-bound), P2 = catch-up / lazy-ladder backfill (best-effort).
  - **Auto-scale on backlog depth.** Backlog > 10 min → scale up GPU pool by 50 %; backlog < 1 min → scale down. Cooldown 5 min to avoid thrashing.
  - **Spot-instance overflow.** P2 work runs on spot GPUs at 70 % cost; on spot reclaim, the work is checkpointed at the GOP boundary and resumed.
  - **Lazy ladder.** P2 uploads only get 360p + 720p + 1080p initially (saves ~70 % of GPU-hours); other rungs encoded later if `views_24h > 1 K`.
- **What still goes wrong.** A *single* 10-hour 8K HDR upload can monopolise dozens of workers for 30+ minutes. We per-video cap concurrent rungs (~16) and split very long uploads into 1-hour shards for parallel encode + stitch.

### Failure 6 — DRM license server outage (rare but catastrophic for Premium)

- **Symptom.** YouTube Premium subscribers in EU can't play movies for 45 minutes. App store reviews rage; subscribers cancel.
- **Root cause.** The Widevine license server in `eu-west` had a deployment that broke license-issuance; failover to `us-east` failed because of a misconfigured region map.
- **What we do now.**
  - **Multi-region license servers**, active-active, with health-check-driven DNS failover (~30 s).
  - **Player retry with backoff + jitter** — players retry license fetch up to 3 times with 1 s, 2 s, 4 s waits, randomised, so the recovery doesn't cause a thundering herd.
  - **Pre-issue licenses on watch start** for the *next* 5 videos in Up Next; if license server is briefly down, the user can keep watching their queue.
- **What still goes wrong.** A regional outage during a launch (e.g., a Movies premiere) is still painful — license servers carry per-session state that has to warm up.

### Failure 7 — Cache stampede on the home page

- **Symptom.** A Redis node restarts during peak. Suddenly all `home:{user_id}` reads for a 5 % slice of users miss → fallback to recompute the row from offline pipeline → recompute is expensive (~500 ms p99) → Recommendation Service pool exhausts → cascades to Watch Svc (which calls Reco for "Up Next").
- **Root cause.** Cold cache + no recompute throttle + no fallback.
- **What we do now.**
  - **Tiered fallback** — if Redis miss, return a **regional popularity row** from a second cache (warm always); enqueue a recompute job; serve the popular row this once.
  - **Recompute throttle** — at most 5 K recomputes/sec across the fleet (token bucket in Redis); above this, return the popularity row.
  - **Redis cluster** with replicas + slow-rebalance — a node restart drains traffic to its replica without TTL invalidations.
- **What still goes wrong.** A user sees popular-but-not-personal recos for a few minutes. Better than spinning forever.

### Failure 8 — Bot traffic inflating views

- **Symptom.** A creator's video shows 2 M views but $0 ad revenue. They open a support ticket; investigation reveals the views came from a bot farm hitting `/beacon` directly.
- **Root cause.** No fraud detection on the beacon path; the SADD dedupe trusts the `user_id`.
- **What we do now.**
  - **Beacon path requires a valid play-session token** issued at `/watch` time, signed with HMAC, expires in 6 h, single-use per video.
  - **Session must include realistic playback signals** — heartbeats every 10 s, position monotonic, not jumping.
  - **IP entropy check** — a single /24 doing 10 K views/min on one video = suspicious; flagged, dropped from count, persisted in audit.
  - **Behavioural fingerprint** — no mouse movement, no scroll, identical user-agent across 100 K accounts = bot farm; account flagged, views invalidated retroactively.
- **What still goes wrong.** Sophisticated bot farms (rotating residential proxies, headless Chrome with simulated mouse movement) still get through. We accept ~1 % of view fraud and rely on the ad-counting pipeline (a stricter, separately-counted metric) for revenue.

### Failure 9 — Live ingest failover during a high-stakes stream

- **Symptom.** During a Mr Beast Live, the active ingest pod OOMs. The stream goes black for viewers for ~8 seconds. Twitter loses its mind.
- **Root cause.** etcd lease was 30 s (too long), so failover took 30 s. Standby pod hadn't been pre-warmed (cold transcoder, ~10 s to start).
- **What we do now.**
  - **etcd lease 3 s TTL** (3× heartbeat). Failover detected within 3 s.
  - **Standby pod pre-warmed** — already receiving the RTMP feed, GPU encoder running in shadow mode (output discarded). On promotion, it starts emitting downstream within 200 ms.
  - **Sequence-number persistence** — segment-id continues monotonically across the failover. Player sees a 1 s gap, not a "stream restarted from scratch" event.
  - **Encoder's PoV doesn't change** — both pods are behind one anycast LB; the encoder keeps pushing to the same URL.
- **What still goes wrong.** If the ingest LB itself dies, both pods are unreachable. We mitigate with anycast across 3 regions (encoder DNS resolves to the closest healthy ingest LB) but haven't eliminated the single point of failure entirely.

### Failure 10 — Mobile upload chunk failures on flaky networks

- **Symptom.** A creator on Indian Rajdhani train wifi tries to upload a 200 MB video. After 80 % progress, the network drops; the app retries from 0 %; battery dies before completion. Creator quits in frustration.
- **Root cause.** No resumable upload; chunks too large for flaky connections.
- **What we do now.**
  - **Resumable chunked upload** (TUS / GCS resumable protocol) — every 8 MB chunk acknowledged independently; on failure, retry only that chunk.
  - **Adaptive chunk size** — drop from 8 MB to 256 KB when client throughput < 500 Kbps. A small chunk completes in ~5 s on bad networks; failure costs only 5 s of retry.
  - **Background upload** on mobile — the OS-level upload-task API keeps the upload alive when the app is backgrounded; resumes on Wi-Fi.
  - **Resume state in Redis** — `(upload_id, last_completed_offset)` survives the client process dying. Open the app a day later, click "Resume", picks up from byte X.
- **What still goes wrong.** If the client device fails completely (phone lost), the upload is gone — Redis TTL is 30 days but the client SHA reference is on the lost device. Acceptable.

### Failure 11 — ABR rebuffer storm on a degraded ISP

- **Symptom.** Comcast in Atlanta has a transit issue. 2 M viewers see the player drop from 1080p to 144p, then to "buffering"; QoE dashboards spike red; complaints flood.
- **Root cause.** Player ABR algorithm is reactive; it adapts but can't tell the difference between transient packet loss and sustained degradation. Without help from the network, every player drops to 144p simultaneously, pulling 2 M extra requests through Comcast's already-saturated transit.
- **What we do now.**
  - **CMCD (Common Media Client Data) headers** — the player sends `BL` (buffer length), `BR` (current bitrate), `RTP` (requested throughput). The CDN edge can shed load (return 503 Slow Down), and our QoE pipeline can detect a regional incident in < 30 s.
  - **Multi-CDN auto-failover** (see Failure 3) — if Akamai's QoE drops in Atlanta, CloudFront takes over within 30 s.
  - **ISP-cache appliances** — an Open-Connect-style box inside Comcast's data centre serves ~50 % of bytes locally, immune to Comcast's transit issue.
- **What still goes wrong.** If the ISP's last-mile is the bottleneck (DSL, congested wifi), no CDN can save us. The player drops gracefully to 240p and shows a "low quality due to network" toast.

### Failure 12 — Comment spam attack

- **Symptom.** A bot army posts the same scam comment ("Free V-bucks at evil-link.com") on the top 1000 trending videos. Creators / viewers complain; trust degrades.
- **Root cause.** No per-channel rate-limit; no cross-channel similarity detection on comment text.
- **What we do now.**
  - **Toxicity + spam classifier** synchronously before persist (~10 ms) — drops obvious cases.
  - **Per-account rate-limit** (token bucket in Redis): 10 comments/min, 100/day for new accounts; raised for established accounts.
  - **Cross-channel velocity check** — same comment text on > 5 different videos in 5 min → all instances quarantined; account flagged.
  - **MinHash similarity** on near-duplicate text (URLs swapped) — buckets near-duplicates within 30 s of posting.
  - **Creator opt-in** "approved-users-only" mode — new commenters are held for review.
- **What still goes wrong.** Sophisticated spam (rotating phrasings, residential proxies) takes hours to detect and rollback. We rely on creator reports + nightly batch sweeps for the long tail.

### Failure 13 — Storage cost explosion from over-encoding

- **Symptom.** Quarterly cloud bill jumps 40 % YoY. Investigation shows we encoded 7 ABR rungs for every UGC upload; 90 % of those uploads got < 100 lifetime views; we stored ~6 EB of bytes that nobody watched.
- **Root cause.** No lazy ladder; encoded everything optimistically.
- **What we do now.**
  - **Lazy ladder** — initial encode = 360p + 720p + 1080p only.
  - **Backfill if `views_24h > 1 K`** — kick off the rest of the ladder (144p / 240p / 480p / 1440p) on a low-priority queue.
  - **AV1 + HEVC only for the top 1 %** (`views_total > 100 K`) — these codecs cost ~3× to encode but save ~30 % bandwidth, only worth it on videos that get millions of views.
  - **Cold-tier the long tail** — if `views_30d == 0`, move to nearline storage (10× cheaper, ~1 s first-byte penalty); rehydrate on first watch.
  - **Original raw uploads → archive after 180 d** (re-derivable from the highest rung if ever needed).
- **What still goes wrong.** A long-tail video that suddenly goes viral pays a ~1 s rehydration tax for the first 100 viewers. Acceptable.

### Failure 14 — Recommendation feedback loop / filter bubble collapse

- **Symptom.** Watch-time is up but DAU is down. Surveys show users feel the feed is "boring" and "the same five channels". Long-form goes down.
- **Root cause.** The watch-time-only objective creates a degenerate optimum — the ranker learns to recommend whatever the user has watched before, killing exploration.
- **What we do now.**
  - **Diversity penalty** in the ranker — penalise consecutive videos from the same channel, same topic, same creator.
  - **Freshness boost** — new uploads get a temporary lift in their first 48 h.
  - **Exploration row** — every Nth slot is from a topic the user *hasn't* watched (epsilon-greedy bandit).
  - **Multi-objective training** — the ranker optimises (watch_time + λ₁·session_length + λ₂·next_day_return), tuned on long-term retention not just per-session watch-time.
- **What still goes wrong.** Tuning the multi-objective weights is more art than science; the wrong weights tank a metric we care about. We A/B test every change for 2 weeks before rollout.

### Failure 15 — Geo-block bypass via VPN

- **Symptom.** A music label files a complaint: their content was geo-blocked in Germany due to GEMA licensing, but viewers in Berlin can watch via NordVPN. License revenue dispute.
- **Root cause.** GeoIP based on connection IP only; VPN exit nodes look like the VPN's region (often US).
- **What we do now.**
  - **MaxMind ASN list** flags known VPN exit ranges; treat them as "unknown country" and apply the *most* restrictive of (claimed country, source country, VPN-detected country).
  - **Account-level country** (from billing address / payment method) overrides IP geo for signed-in users.
  - **DRM-tied geo-check** — Premium content is DRM-protected; the license server checks geo at license-issue time, not just at manifest time. License is short-lived (~30 min).
- **What still goes wrong.** Determined viewers using residential VPN proxies can't be detected by IP heuristics. We accept ~1 % bypass rate and rely on contractual notice-and-take-down for the rest.

### Failure 16 — Watch Service deploy breaks the play path

- **Symptom.** A bug in a Watch Svc deploy returns 500 on every `/watch` call for a 90-second window. **No new video plays start** during that window.
- **Root cause.** Bad deploy, no canary.
- **What we do now.**
  - **Canary at 1 % → 10 % → 50 % → 100 %** with auto-rollback on 5xx-rate spike.
  - **Already-playing streams keep playing.** This is the design choice that saves us — manifests have a 6 h TTL and segments are CDN-cached, so existing viewers see no impact. Only *new* watches fail. Even at 200 M concurrent, ~1 M of those tap "next video" per second; a 90-second outage = 90 M failed tries (people retry).
  - **Watch Svc fail-degraded mode** — on Spanner outage, return cached metadata + skip privacy check (fail-open for public-default). Bad for privacy but better than total downtime.
- **What still goes wrong.** Fail-open is a tradeoff — a private video could leak during a Spanner outage. We accept this for the rare metadata-DB-down case.

---

## 12. Tech-Stack Cheat-Sheet

If you draw this on the whiteboard with reasoning, you've finished the interview.

| Concern | Choice | Why |
|---------|--------|-----|
| Object store (raw + transcoded) | **GCS / S3 multi-region** | 11-nines durability, scales to EB |
| Catalogue metadata | **Spanner** (or Vitess) | Strong consistency, multi-region, sharded |
| Comments + watch_events + view_counters | **Bigtable** | High-write append-only, wide-row, eventual |
| Hot caches (metadata, reco rows, view counts, session) | **Redis cluster** | µs reads, atomic INCR, HLL, Sets for dedup |
| Async backbone | **Kafka** RF=3, idempotent producer | Domain events, replay, fan-out |
| Stream processing | **Flink** (or Beam) | Tumbling windows for view rollup, feature streams |
| Search | **Elasticsearch** (BM25) **+ Vespa** (vector ANN) | Hybrid retrieval |
| ML serving | **TF-Serving** (or Vertex AI) | Online ranker for reco |
| Workflow orchestration | **Temporal** (or Argo / Cloud Workflows) | Transcoding DAG, retries, durability |
| API gateway | **Envoy / Kong** | TLS term, JWT, rate-limit, canary routing |
| Multi-CDN | **Akamai + CloudFront + Cloudflare + ISP-cache** | No single point of CDN failure |
| Origin shield | **NGINX / Varnish** regional cluster | Coalesce CDN misses; cuts origin egress 200× |
| Live transcoder | **GPU farm (NVENC)**, CMAF chunked | Hardware-encode 7 rungs in real time |
| Monitoring | **Prometheus + Grafana + Tempo** | Time-series, traces, dashboards |
| Logs / audit | **GCS / S3 WORM** + BigQuery | 7-yr retention, queryable |

---

## 13. Top 10 Q&A

**Q1. Why not just one progressive MP4 per video?**
The user on 3G stalls; the user on fibre wastes bandwidth on 480p. ABR (Adaptive Bitrate Streaming) is mandatory — the player picks a rung per segment based on real-time throughput. Single-bitrate is what TV broadcast did; the internet adapts.

**Q2. Why CMAF source instead of separate HLS + DASH encodes?**
CMAF defines a single fragmented MP4 (`.cmfv`) that both HLS and DASH manifests can point to. One encode, two manifests → halves storage from ~10 EB to ~5 EB. CDN cache footprint also halved.

**Q3. Why is the view counter eventually consistent?**
Strong consistency on `view_count += 1` would require a row lock on Spanner per increment — cap ~5 K writes/sec per row. A viral video at 100 K views/sec = 95 % of writes time out. We use Redis HLL + Flink rollup + Bigtable INCR with 16 shards per row → ~1.3 M/sec capacity per video, at the cost of ≤ 60 s display lag. Honest trade.

**Q4. How does multi-CDN work without flapping?**
etcd holds per-region weights (e.g., us-east: Akamai 50 %, CloudFront 30 %, Cloudflare 20 %), refreshed every 30 s based on `cdn.qoe` (rebuffer ratio, throughput, error rate). A misbehaving CDN's weight drops to 0 within 30 s; the player picks a new PoP at the next manifest refresh; viewer sees a 1-s rebuffer.

**Q5. Why direct-to-GCS chunked upload instead of routing through your app?**
Cost (10 PB/day egress through our pods would bankrupt us), throughput (GCS scales to TB/sec; our app pods don't), and resumability (a dropped chunk retries from `last_completed_offset` in Redis, not from byte 0).

**Q6. How do you handle a Mr Beast subscriber notification fan-out (250 M subs)?**
The publisher writes one `video.uploaded` event with `channel_id`. The Notification Service queries `subscriptions` in batches of 10 K, produces per-recipient `notify.events` partitioned by `recipient_user_id`, and uses APNS/FCM batch-send (1 K tokens per call). Wall-clock: ~5 min for all 250 M; first batch lands in ~30 s. Per-user dedupe in a 24-h Redis Set prevents notification bombing.

**Q7. Why is recommendation two-stage?**
You can't run a DNN ranker over 10 B candidates per home-page load (compute is ~10⁹ GPU-sec/day, infeasible). Stage 1 (cheap retrieval: ANN + collab-filter) cuts 10 B → 1000; stage 2 (expensive DNN) ranks the 1000. Same shape as web search: retrieve then rerank.

**Q8. How does the player choose initial bitrate?**
Conservative start (720p) regardless of declared throughput — declared throughput is often wrong. Adapt within 4 s based on first-segment-fetch time. Drop on rebuffer; raise on excess buffer headroom. Players send CMCD headers (`BL`, `BR`, `RTP`) so the CDN edge knows what they're doing.

**Q9. What happens when the Watch Service is down?**
**Already-playing streams keep playing** — manifests have a 6 h TTL, segments are CDN-cached, no calls to Watch Svc are needed for in-progress watches. Only *new* watches fail. Watch Svc has a fail-degraded mode (cached metadata + skip privacy check, fail-open for public-default) for Spanner outages. Canary deploys + auto-rollback on 5xx-rate spike prevent bad pushes.

**Q10. Why LL-HLS for live, not WebRTC?**
LL-HLS = ~3-5 s latency, scales to millions per stream (HTTP-cacheable). WebRTC = ~200 ms but tops out at hundreds (every viewer is a peer; SFU compute scales linearly). For a Mr Beast Live with 1 M concurrent viewers, WebRTC would need a thousand SFU servers; LL-HLS uses our normal CDN.

---

## See also

- The **full version** lives in [`06-DesignYouTube.md`](./06-DesignYouTube.md) — go there for ContentID, DRM, multi-language ASR, monetisation, the moderation console, the search re-ranker, the offline ML training infra, the analytics dashboards, and the per-region royalty pipeline.
- The **Netflix/OTT design** (curated catalogue + DRM-everywhere + 50 M concurrent live) is in [`05-DesignOTTPlatform.md`](./05-DesignOTTPlatform.md) — it's the *opposite* axis of difficulty (small catalogue, strict licensing) and worth reading alongside this one.
