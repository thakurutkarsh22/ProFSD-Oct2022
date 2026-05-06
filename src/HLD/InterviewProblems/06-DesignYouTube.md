# Design YouTube (User-Generated Video at Planet Scale)

> **Difficulty:** Hard &nbsp;|&nbsp; **Frequency:** ★★★★★ &nbsp;|&nbsp; **Companies:** YouTube, TikTok, Instagram (Reels), Facebook Watch, Twitch, Vimeo, Dailymotion, Bilibili, Kuaishou, Snap (Spotlight), X (formerly Twitter — video), Reddit (video), Pinterest, Twitch Clips, JioCinema (UGC creator-tier), MX TakaTak.
>
> **Real-world analogues:** the headline shape is **YouTube** — a global, ad-supported, creator-uploaded long-form video platform with a billion-dollar recommendation engine, copyright-aware ingest pipeline, and an opinionated "watch-time" objective function. Variants: short-form vertical (TikTok / Reels / Shorts), live streaming (Twitch / YouTube Live), niche / community-led (Bilibili / Vimeo).

> **A note on scope.** This document designs the **YouTube-style UGC video platform** end-to-end: **upload** (resumable multipart → object store), **process** (transcode ladder + ASR captions + thumbnail picks + ContentID copyright match + safety scan), **deliver** (multi-tier CDN + ISP-resident YouTube-Cache appliances + adaptive HLS / DASH), **discover** (search + recommendation + trending), **engage** (comments / likes / subscriptions / notifications), **count** (the famously hard "1 B view counter" problem), **monetize** (ads + Premium subs + Super Chat — sketched, not built), and **moderate** (ML triage + human review + DMCA / legal). It does **not** design the codecs themselves (FFmpeg / x264 / VP9 / AV1 are off-the-shelf), the GFE / Google-Cloud-CDN datacentre fabric (we *use* that tier), or the deep ML training infrastructure (TFX / Vertex AI / TPU pods are out-of-scope).

> **YouTube vs the OTT (Netflix) doc — important distinction.** The neighbouring [`05-DesignOTTPlatform.md`](./05-DesignOTTPlatform.md) is the *Netflix shape*: a small, **curated** catalogue (~50K editorially-licensed titles), strict **DRM** on every byte, fixed ABR ladders chosen per title, no comments, no creator pipeline, no ContentID — its hard problem is **per-region licensing + 50 M concurrent live**. The YouTube shape is the *opposite* axis of difficulty: a **planet-scale UGC firehose** (~500 hours uploaded per minute; ~10 B total videos at rest), **DRM mostly absent** (hot path is open HLS/DASH), and the hard problems are **transcoding throughput**, **recommendation latency under cardinality**, **hot-video view-count contention**, and **ContentID + safety at 30 K rendition-hours/hour**. Read both — together they cover the two extremes of the "design a video platform" problem space.

> **Acronym reference.** UGC, HLS, DASH, LL-HLS, CMAF, DRM, ABR, TTFF, CTR, MAU, DAU, GOP, ASR, NSFW, DMCA, CDN, PoP, ISP, HLL, TF-Serving, ANN, BM25, CMCD, RTMP, SRT, GCS — every acronym is expanded in [§22 — Acronyms & Abbreviations Cheat-Sheet](#22-acronyms--abbreviations--cheat-sheet) with a one-line meaning.

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Clarifying Questions](#2-clarifying-questions-always-ask-these-first)
3. [Requirements (FR + NFR)](#3-requirements)
4. [Capacity Estimation](#4-capacity-estimation-back-of-the-envelope)
5. [The Latency Budget — Time-To-First-Frame (TTFF)](#5-the-latency-budget--time-to-first-frame-ttff)
6. [Why *Not* Just MP4 + S3 / Skip the Transcoding Pipeline / One Big Postgres](#6-why-not-just-mp4--s3--skip-the-transcoding-pipeline--one-big-postgres)
7. [Data Model](#7-data-model)
8. [High-Level Architecture (HLD)](#8-high-level-architecture-hld)
9. [Component Deep-Dives](#9-component-deep-dives)
10. [Video Upload & Transcoding Pipeline](#10-video-upload--transcoding-pipeline)
11. [End-to-End Flows](#11-end-to-end-flows)
12. [CDN, Multi-CDN & ISP-Cache Strategy](#12-cdn-multi-cdn--isp-cache-strategy)
13. [Recommendation, Search, Trending, Personalisation](#13-recommendation-search-trending-personalisation)
14. [The View Counter at Scale (the famous interview question)](#14-the-view-counter-at-scale-the-famous-interview-question)
15. [Live Streaming (LL-HLS / LL-DASH / RTMP / SRT Ingest)](#15-live-streaming-ll-hls--ll-dash--rtmp--srt-ingest)
16. [Comments, Likes, Subscriptions, Notifications (Engagement)](#16-comments-likes-subscriptions-notifications-engagement)
17. [Content-ID, NSFW, DMCA, Moderation Pipeline](#17-content-id-nsfw-dmca-moderation-pipeline)
18. [Edge Cases & Gotchas](#18-edge-cases--gotchas)
19. [Security, Monetisation, Privacy, Geo-Compliance](#19-security-monetisation-privacy-geo-compliance)
20. [Observability & Quality-of-Experience Telemetry](#20-observability--quality-of-experience-telemetry)
21. [Technology Choices — Final Verdict](#21-technology-choices--final-verdict)
22. [Q&A Defense — Top 25 Tough Interview Questions](#22-qa-defense--top-25-tough-interview-questions)
23. [Acronyms & Abbreviations — Cheat-Sheet](#23-acronyms--abbreviations--cheat-sheet)

---

## 1. Problem Statement

Design a **planet-scale, user-generated long-form video platform** that lets **anyone with an account upload a video**, lets **anyone in the world play it within seconds** on every device, **discovers the right video for every viewer** via personalised recommendations, **counts every meaningful view** with sub-minute freshness, **respects copyright** (ContentID), **suppresses harmful content** (NSFW / hate / CSAM / spam), and **pays creators** based on monetisable watch-time.

**Concrete SLOs:**

- **Scale:** **~2 B registered users**, **~800 M DAU**, **~1 B watch-hours / day**, **~500 hours of video uploaded per minute** (~30 K hours / hour ≈ ~10 PB / day raw bytes), **~10 B total videos at rest**.
- **Concurrency:** **~200 M concurrent watchers** at peak (evening US + EU overlap, or marquee live event), **~10 M concurrent uploads** at peak.
- **Throughput (egress):** sustained **~50–100 Tbps** average peak (~3 EB/day egress) across the multi-CDN + ISP-cache footprint; **~95%** of bytes served from **edge / ISP-cache**, **<5%** of bytes ever touch our own origin shield.
- **Latency targets:**
  - **Time-To-First-Frame (TTFF) p99 ≤ 1 s** "tap → first decoded frame on screen" (mobile, cache-hit). p99 ≤ 1.8 s on cold cache.
  - **Watch API p99 ≤ 80 ms** (`GET /watch?v=…` returns a manifest URL).
  - **Search p99 ≤ 200 ms** (typeahead p99 ≤ 50 ms).
  - **Recommendation serve p99 ≤ 100 ms** (home page row generation).
  - **Live glass-to-glass p99 ≤ 5 s** (LL-HLS sports / live music; ≤ 10 s acceptable for non-time-sensitive).
  - **Upload-to-publish p99 ≤ 60 s** for a 1080p / 5-min video; ≤ 10 min for a 1-hour 4K HDR video.
  - **View count freshness p99 ≤ 60 s** (count visible to viewer ≤ 1 minute after the watch beacon fires).
- **Durability:** master raw upload — **11 nines on GCS / S3 + cold archive** after 180 d cold; transcoded ABR rungs — **recoverable by re-running the pipeline** so cheaper standard-storage-with-replication is fine.
- **Availability:** **99.99%** for browse + watch (a ~52-min annual budget); **99.9%** for upload-completion (uploads can resume if a chunk fails — eventual consistency tolerated).
- **Consistency contract:**
  - **Video metadata** (title / description / owner / privacy) → **strong** (Spanner / Vitess); a creator who flips a video to "private" must be effective immediately.
  - **Subscriptions / channel ownership** → **strong** (auth-critical).
  - **DRM entitlement** (YouTube Premium / Movies / Kids gating) → **strong** (read at request time).
  - **Comments / likes** → **eventual** (a comment may take ~3 s to appear on the recipient's page; OK).
  - **View counter** → **eventual ≤ 60 s** (the famous 301 → 302 → 303 view-count behaviour).
  - **Recommendations** → **stale-OK** (TTL ~5 min hot cache; refreshed within minutes of new signals).
  - **Trending** → **eventual ≤ 10 min** (tumbling-window aggregation).

> **The interview-defining tension at YouTube scale.** A typical OTT (Netflix) has ~50 K titles; YouTube has **~10 B videos**. That's a **5-orders-of-magnitude difference in catalogue cardinality**, and it's the source of every hard problem: the recommender can't enumerate candidates, the search index can't fit on one node, the CDN can't pre-warm anything beyond the head, the storage can't fit on hot tier, the ContentID matcher must run on **every** upload, and a single viral video can spike one shard's view counter by 10⁶× over baseline. **Cardinality + UGC pace is the entire problem.**

---

## 2. Clarifying Questions (always ask these first)

Score points by asking these *before* drawing boxes:

1. **Long-form, short-form, or both?** → *Long-form is the headline shape (3–15 min average, but 30–60 min long-tail). Shorts (≤ 60 s vertical) reuse the same ingest + watch pipeline but add a different recommendation path (in-feed instead of personalised home).*
2. **VOD only, or VOD + Live?** → *Both. VOD is bulk traffic; Live (gameplay streams, premieres, sports) reuses ingest gateway + LL-HLS packager. We design Live as a first-class flow.*
3. **Geographic scope?** → *Global. Per-country content policies (German hate-speech laws, Indian IT Rules 2021, US DMCA, EU GDPR + Digital Services Act). Geo-block / age-restrict at metadata + manifest level.*
4. **What ABR formats / codecs?** → *HLS for Apple/iOS/Safari; DASH for Android/web/CTV. CMAF source so segments are shared. Codecs: H.264 (universal), VP9 (Android/web), AV1 (newer devices, ~30% bandwidth saving), HEVC (Apple TV / 4K). We encode H.264 + VP9 + AV1 + HEVC for the 1080p+ ladder.*
5. **DRM?** → *Mostly NO. The vast majority of UGC is open HLS/DASH (no DRM). DRM (Widevine/PlayReady/FairPlay) only kicks in for **YouTube Premium-only**, **Movies & TV** (rentals), and **Kids profile-restricted** content. The hot path assumes no DRM.*
6. **Devices?** → *iOS, Android, Web (HTML5 + Media Source Extensions / hls.js / Shaka), Smart TVs (Tizen / WebOS / Android TV / Roku / Fire TV / Chromecast), game consoles, embedded players in third-party sites.*
7. **What does "view" mean?** → *Critical interview question.* **YouTube's definition: a "view" is counted when a user watches at least 30 seconds OR completes the video (whichever first), with anti-spam dedupe by `(user_id, video_id, day)`.* Ad views have a different (stricter) definition.
8. **Authentication tiers?** → *Anonymous (can watch most public videos, no recommendations beyond popularity), authenticated (full personalisation, can upload, like, comment, subscribe), Premium (ad-free + downloads + Music tier), Kids (gated catalogue, no comments, restricted recommendations).*
9. **Ads (AVOD) vs Premium (SVOD)?** → *Hybrid. Ads are the bulk of revenue; Premium is the ad-free + offline + Music bundle. Ads are server-side ad insertion (SSAI) for live, client-side ad insertion (CSAI) for VOD (so the player can render skippable / overlay creatives correctly).*
10. **Resumable / chunked upload?** → *Yes — TUS or Google's resumable-upload protocol; client-side chunking at 8 MB (configurable to 256 KB on flaky mobile). Each chunk has a `Content-Range`; server tracks `(upload_id, last_completed_offset)` in Redis with 30-day TTL.*
11. **What about copyright (ContentID)?** → *Mandatory for our scale. Every upload runs a fingerprint match (audio: chromaprint / phash; video: pHash + perceptual hash) against the rights-holder reference DB. Match → block / monetise (ad revenue routes to rights-holder) / mute audio. Non-trivial false-positive handling — see §17.*
12. **Live latency target?** → *5 s glass-to-glass for general-purpose live (LL-HLS); 30 s acceptable for non-time-sensitive replay-style live. We are NOT building a 200 ms WebRTC interactive system here.*
13. **Recommendation freshness?** → *Two layers — offline candidate-gen + ranker training (nightly Spark + TF training); online streaming features (Flink → feature store) updated every few seconds; per-user cached row in Redis with 5-min TTL.*
14. **Multi-language audio + subtitles?** → *Yes. Creator-uploaded subtitles + auto-generated ASR captions (multi-language ML model) + community contributions (deprecated as of 2020 but the data is still there) + auto-translated subtitle layer.*
15. **Content moderation — strict or "platform"?** → *Tiered. Auto-block: CSAM (PhotoDNA hash match — instant), pure spam, obvious malware. Auto-flag → human review: NSFW boundaries, hate-speech borderline, election / health misinformation. Keep but demote: low-quality / clickbait / borderline.*
16. **What about creator monetisation?** → *Yes. Ad revenue share (AdSense / DV360 stitched into the watch flow), Super Chat (paid live-chat highlights), channel memberships, Premium revenue allocation by watch-time. Out of scope: detailed payout pipeline; in scope: the data model.*
17. **Geo-restriction?** → *Per-video country allow / block list (creator-controlled or auto via ContentID rights). Manifest server geo-checks IP via MaxMind on every play decision.*
18. **What about uploads from low-bandwidth markets?** → *Resumable chunks at 256 KB; aggressive 240p / 360p ladder generation; "slow-upload" notification when client throughput is poor; backgrounded upload on mobile (continues if app is in background, with system upload-task APIs).*

---

## 3. Requirements

### 3.1 Functional

- **F1.** **Watch / Browse** — `GET /watch?v={video_id}`, `GET /home`, `GET /trending`, `GET /channel/{id}`, `GET /search?q=`. Personalised home, trending row, "Up Next" panel, channel page, watch-page comments + likes + subs.
- **F2.** **Authentication** — Google Sign-In + email + phone + SSO (Apple, Facebook). **YouTube SSO via Google account.** OAuth2 + OIDC. Passkeys (WebAuthn). Long-lived refresh token (30 d) → short-lived access JWT (1 h). 2FA via TOTP / passkey for sensitive actions (channel deletion, payout).
- **F3.** **Channel / Profile** — every authenticated user has a channel (auto-created on sign-up). Channel page lists uploaded videos, playlists, subscribers count, descriptions, social links.
- **F4.** **Upload** — `POST /upload/init` returns a resumable URL; client `PUT /upload/{id}` chunks (8 MB default, 256 KB on slow connections); on completion, server stitches and triggers transcoding pipeline. Background uploads (mobile). Schedule publish at future date.
- **F5.** **Transcoding** — produce ABR ladder (144p / 240p / 360p / 480p / 720p / 1080p / 1440p / 2160p / 4K HDR / 8K experimental); split into HLS .ts + DASH .m4s segments via CMAF source. Codecs: H.264 (universal), VP9 (default), AV1 (next-gen), HEVC (Apple). Auto-pick highest quality the source supports.
- **F6.** **Live** — `POST /live/start` returns RTMP / SRT push URLs + stream key; live transcoder produces LL-HLS / LL-DASH ladder on the fly; viewers consume via the same `/watch` endpoint with live-mode flag.
- **F7.** **Search** — full-text + semantic (vector ANN). Multi-language. Typeahead suggestions (≤ 50 ms). Filter by upload time / duration / quality. Personalised re-rank based on watch history.
- **F8.** **Recommendation** — at minimum: home (personalised rows), Up Next (post-video), trending (per-region per-category), Shorts feed (vertical infinite scroll). Two-stage: candidate-gen (collab + content) + ranker (DNN two-tower).
- **F9.** **Engagement** — `POST /comment`, `POST /reply`, `POST /like` (toggle), `POST /dislike` (private since 2021 — only to creator), `POST /subscribe`, `POST /unsubscribe`, `POST /share` (no-op telemetry).
- **F10.** **Watch progress** — every 10 s the player heartbeats `(user_id, video_id, position_ms, duration_ms)`. Stored in Bigtable; powers "Continue Watching" + Up Next + recommender features.
- **F11.** **View counting** — every 30 s of monotonic playback fires a "view beacon"; server dedupes by `(user_id, video_id, day)`; counts aggregated to view counter (Bigtable + Redis HLL); displayed on the watch page within 60 s of the beacon.
- **F12.** **Notifications** — new upload from a subscribed channel, replies to your comments, milestone achievements (10 K subs, etc.), live-stream-going-live, premiere-starting-soon. APNS / FCM / email / in-app.
- **F13.** **Playlists** — create, edit, share, mix; private / public / unlisted. Auto-playlists ("Watch Later", "Liked Videos", "Mix").
- **F14.** **Captions** — auto-ASR (server-generated), creator-uploaded SRT/VTT, auto-translation (ML), community contributions (legacy).
- **F15.** **ContentID & DMCA** — every uploaded asset is fingerprinted (audio + video) and matched against rights-holder DB; matches route to one of: block / monetise (revenue routes to claimant) / track (analytics only). Counter-claim workflow for creators.
- **F16.** **Moderation** — auto-detect CSAM / NSFW / hate / spam; queue borderline content for human review; surface DMCA complaints + counter-claims; geo-block per local law.
- **F17.** **Monetisation** — ad rolls (pre-roll / mid-roll / display banner), Super Chat in live, channel memberships, Premium subscription revenue allocation, Shopping (creator product links).
- **F18.** **Analytics** — Creator Studio dashboards: watch-time by day / region / device / source-of-traffic / age-gender; revenue breakdown; impression CTR; subscriber growth; comments sentiment.
- **F19.** **Offline downloads** — Premium-only feature; server vends DRM-protected downloadable bundle valid 30 d.
- **F20.** **Premieres / scheduled** — creator can schedule a video to "premiere" at a future time (everyone watches together with live chat overlay).

### 3.2 Non-Functional

| Attribute                            | Target                                                                                          |
|--------------------------------------|-------------------------------------------------------------------------------------------------|
| Registered users                     | **~2 B**                                                                                        |
| MAU                                  | ~1.5 B (~75% of registered)                                                                     |
| DAU                                  | **~800 M**                                                                                      |
| Concurrent watchers (peak)           | **~200 M**                                                                                      |
| Watch-hours / day                    | **~1 B** hours                                                                                   |
| Upload rate (peak)                   | **~500 hours / minute** = ~30 K hours / hour ≈ **~10 PB raw bytes / day**                        |
| Total videos at rest                 | **~10 B**                                                                                       |
| Storage at rest                      | ~5 EB packaged ABR variants (after dedup, cold tiering)                                          |
| Egress                               | ~50–100 Tbps avg peak; **~250 Tbps live peak** during marquee events                             |
| **TTFF (watch-start)**               | **p99 ≤ 1 s** (cache-hit); p99 ≤ 1.8 s (cold cache)                                              |
| Watch API latency                    | **p99 ≤ 80 ms**                                                                                  |
| Search latency                       | **p99 ≤ 200 ms**; typeahead p99 ≤ 50 ms                                                          |
| Recommendation serve latency         | **p99 ≤ 100 ms**                                                                                |
| Glass-to-glass live latency          | p99 ≤ 5 s (LL-HLS); ≤ 10 s acceptable                                                            |
| Upload-to-publish (1080p, 5 min)     | p99 ≤ 60 s                                                                                       |
| View count freshness                 | **p99 ≤ 60 s**                                                                                  |
| CDN cache-hit ratio                  | **≥ 95%** (edge + ISP-cache aggregate)                                                          |
| Availability (browse + watch)        | **99.99%**                                                                                       |
| Consistency                          | metadata / DRM / subs → strong; comments / likes / views / reco → eventual                       |
| DRM                                  | optional (Premium / Movies / Kids only); Widevine + PlayReady + FairPlay when applicable          |
| Multi-CDN                            | At least 3 CDNs + Open-Connect-style ISP-resident appliances (~15 K globally)                    |
| Encoding ladder                      | 7–10 rungs (144p–4K HDR), CMAF source, packaged HLS + DASH                                       |
| DR                                   | Multi-region active-active for control plane; multi-CDN for data plane; raw uploads in 3 regions |
| Compliance                           | GDPR + DSA (EU), DMCA + CCPA (US), India IT Rules 2021, COPPA (Kids), per-country hate-speech     |

---

## 4. Capacity Estimation (back of the envelope)

These numbers are quoted in the rest of the doc, on the diagram, and in the Q&A. Internalise them.

| Metric                                              | Calculation                                                                                                  | Value                                |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------|
| Registered users                                    | stated                                                                                                       | **2 B**                              |
| MAU                                                 | ~75% of registered                                                                                           | ~1.5 B                               |
| DAU                                                 | ~50% of MAU                                                                                                  | **800 M**                            |
| Avg watch-time / DAU / day                          | empirical                                                                                                    | ~75 min                              |
| **Watch-hours / day**                               | 800 M × 75 min ÷ 60                                                                                          | **~1 B**                             |
| Avg concurrent watchers                             | 1 B watch-hrs ÷ 24 h                                                                                         | ~42 M average                        |
| **Peak concurrent watchers**                        | 4× average                                                                                                   | **~200 M**                           |
| Avg bitrate (mix of 360p–4K)                        | weighted: 50% 720p (3 Mbps), 30% 1080p (5 Mbps), 15% 480p (1 Mbps), 5% 4K (15 Mbps)                          | **~3 Mbps**                          |
| **Egress (steady)**                                 | 42 M × 3 Mbps                                                                                                | **~120 Tbps (avg) → 50–100 Tbps after CDN coalescing** |
| **Egress (peak)**                                   | 200 M × 3 Mbps                                                                                               | **~600 Tbps theoretical → ~250 Tbps after edge / ISP-cache** |
| Upload rate                                         | 500 hrs / min                                                                                                 | **30 K hrs / hr** (~720 K hrs / day) |
| Avg upload bitrate (1080p/4K mix)                   | weighted ~10 Mbps (4K HDR pushes 25 Mbps; mobile / talking-head uploads at 2–4 Mbps)                          | ~10 Mbps                             |
| **Raw upload bytes / day**                          | 720 K hrs × 3600 s × 10 Mbps ÷ 8                                                                              | **~3 PB / day** (raw); after final-cut & dedup → **~10 PB / day** ladder         |
| Total videos at rest                                | YouTube reports >800 M public; internal long-tail + private + age-restricted                                  | **~10 B**                            |
| Avg duration / video                                | weighted (3-min Shorts dominates count; long-form dominates bytes)                                            | ~12 min                              |
| Hot-tier storage                                    | top ~5% videos serve ~80% watch traffic                                                                       | ~500 PB hot                           |
| Total storage (incl cold)                           | empirical                                                                                                    | **~5 EB**                            |
| Watch beacons                                       | 800 M DAU × ~5 sessions × 3 beacons / session                                                                 | ~12 B / day → **~150 K / s avg, ~500 K / s peak** |
| **Comments / day**                                  | ~5% of DAU comment, avg 1.5 comments / day                                                                    | ~60 M / day → **~700 / s avg, ~3 K / s peak** |
| Likes / day                                         | ~50% of DAU likes, avg 3 / day                                                                                | ~1.2 B / day → **~14 K / s avg, ~50 K / s peak** |
| **Search QPS**                                      | 800 M × ~5 home loads × 0.5 search / home                                                                     | ~2 B / day → **~25 K QPS avg, ~100 K QPS peak** |
| **Recommendation QPS**                              | 800 M × ~10 home / Up-Next refreshes / day                                                                    | ~8 B / day → **~100 K QPS avg, ~400 K QPS peak**      |
| Subscriptions (avg per user)                        | empirical                                                                                                    | ~30                                  |
| Notifications fan-out                               | 1 M-subscriber channel uploads → 1 M push notifs                                                              | bursty                               |
| ContentID match rate                                | ~30% of uploads have at least one match                                                                       | ~150 hours / min match rate          |
| **Transcoding compute**                             | 30 K hrs / hr input × 7 ladder rungs                                                                          | **~210 K rendition-hrs / hr**        |
| Transcoding GPU-hr per 1080p hour                   | empirical: ~5 min on 8× A100 (~12× real-time)                                                                  | ~0.7 GPU-hrs / hour 1080p            |
| **Steady-state GPU fleet**                          | 210 K rendition-hrs / hr × ~0.5 GPU-hrs / rendition-hr                                                        | **~30–50 K GPUs**                    |
| Kafka partitions (`watch.events`)                   | peak 500 K / s ÷ ~2 K / s / partition                                                                         | **~256 partitions**                  |
| Kafka partitions (`engagement.events`)              | peak 50 K / s                                                                                                  | **~128 partitions**                  |
| Kafka partitions (`features.stream`)                | peak 1 M / s                                                                                                  | **~256 partitions**                  |
| Metadata DB (Spanner)                               | 10 B videos × ~2 KB                                                                                          | **~20 TB**                           |
| Comments DB (Bigtable)                              | ~10 B comments × ~500 B                                                                                      | **~5 TB sharded**                    |
| Search index (Elasticsearch + Vespa)                | 10 B videos × ~2 KB enriched                                                                                  | **~20 TB**                           |
| Feature store (online: Bigtable)                    | 2 B users × ~10 KB feature vector + 10 B videos × ~5 KB                                                       | **~70 TB**                           |
| CDN cache footprint per PoP                         | working-set ~5 % of catalogue                                                                                | ~250 GB / PoP                        |
| Multi-CDN regional caches                           | ~200 PoPs × ~5 CDNs                                                                                           | ~1 K cache locations                 |
| **ISP-cache appliances (Open-Connect-style)**       | empirical (Netflix-Open-Connect-comparable)                                                                  | **~15 K globally, ~256 TB each**     |

> **Why the numbers matter for the design.** Two of these dominate every architectural choice: **30 K hours / hour ingest** forces the transcoding pipeline to be horizontally distributed at the **GOP level** (a 1-hour video is split into ~360 × 10-second GOPs, each encoded in parallel by a different worker — that's how you get 12× real-time per video on an 8-GPU box), and **~250 Tbps peak egress** forces ~95 % of bytes to come from **edge / ISP-cache** before they touch our origin shield. Skip either of those and the cluster size to support the load goes from "50 K nodes" to "you can't build it".

---

## 5. The Latency Budget — Time-To-First-Frame (TTFF)

**Total budget: 1 s p99 from "tap Play" to "first decoded frame on screen".** Spend it explicitly:

| Stage                                                     | Target  | p99    | Notes                                                                 |
|-----------------------------------------------------------|---------|--------|-----------------------------------------------------------------------|
| Tap → DNS + TCP + TLS to nearest PoP                      | 50 ms   | 100 ms | ALB/GFE in nearest PoP; TLS 1.3 + 0-RTT for return visitors           |
| API GW: TLS terminate, JWT validate, geo-check             | 5 ms    | 15 ms  | Token cache 1 m; MaxMind GeoIP in-process                             |
| Watch Svc: read video metadata (Spanner / Redis)           | 10 ms   | 30 ms  | Redis hit (~95 %); Spanner fallback (~30 ms)                           |
| Watch Svc: build manifest URL + sign (HMAC, 6-h TTL)       | 5 ms    | 15 ms  | Pre-built per-rendition manifest in GCS; URL just signed at request   |
| Watch Svc: pick CDN PoP (ASN-based + QoE-driven router)    | 5 ms    | 10 ms  | Routing table cached in pod, refreshed every 30 s                     |
| API GW → Player: response (≤ 2 KB JSON)                    | 50 ms   | 80 ms  | Mobile RTT                                                             |
| Player parses manifest, picks initial bitrate (ABR)        | 30 ms   | 100 ms | Player-side; init algorithm: start at 720p, adapt within 4 s          |
| **Player fetches `init.mp4` from CDN** (cache hit)         | 80 ms   | 200 ms | PoP cache hit ~95%; cold-path adds ~150 ms shield round-trip          |
| **Player fetches first media segment** (cache hit)          | 200 ms  | 400 ms | A 4-s 720p segment ≈ 1 MB; bandwidth-bound for first 200ms             |
| Decoder warm-up + first decoded frame                     | 100 ms  | 200 ms | Hardware decoder init                                                  |
| **TOTAL p50 / p99**                                       | **~535 ms / ~1 s** |  |                                                                       |

> **The single biggest knob is CDN cache-hit ratio.** A 95% PoP hit ratio means 5% of users go to **origin shield** which is ~150 ms slower; this dominates the p99. **Pre-positioning the head of the long tail** (the top 0.1% of videos that serve ~50% of all watch traffic) at every PoP and every ISP-cache appliance is non-negotiable. Cold-cache long-tail videos pay the ~200–500 ms shield-or-origin tax, but represent ~5% of plays so the *p99* tolerates it.

### 5.1 What blows this budget in practice

- **CDN cold cache (rare / new video)** — adds 200–500 ms (PoP → regional shield → origin). Mitigation: pre-fetch via CMCD `nor` (next-object-request) hint, ~80 ms ahead. For unrelated cold videos, accept the tail.
- **Manifest mis-routing** — wrong region's PoP picked because the user's IP is geographically far from their ASN. Mitigation: bake the user's preferred PoP into JWT at login (refreshed at sign-in), invalidate on roaming.
- **TLS handshake on cold connection** — first-ever connect to a PoP is ~3 RT for TLS 1.2. Mitigation: TLS 1.3 + 0-RTT on resume; pre-warm during app launch.
- **Initial bitrate choice too high** — player picks 1080p, can't sustain, switches to 360p, rebuffers. Mitigation: start at conservative 720p, adapt up/down based on the first 4 s of throughput measurement.
- **Live "thundering herd"** — 100 K viewers tap "Watch live" the moment a Mr Beast premiere starts. Mitigation: stagger the manifest TTL (jitter ±2 s), pre-warm PoPs 30 s before the announced start, push notification with "the premiere is starting".

---

## 6. Why *Not* Just MP4 + S3 / Skip the Transcoding Pipeline / One Big Postgres

Interviewers will press on every shortcut. Honest comparison:

| Candidate shortcut                                              | What's tempting                          | Why it breaks at YouTube scale                                                  |
|-----------------------------------------------------------------|------------------------------------------|---------------------------------------------------------------------------------|
| **One progressive MP4 per video (no ABR)**                      | simple `<video src=…>` tag              | The user on 3G stalls; the user on fibre wastes bandwidth on 480p. **One bitrate** is what TV broadcast did; the internet adapts per-segment. Mandatory ABR. |
| **Stream directly from S3 (no CDN)**                            | one storage tier, no cache invalidation  | S3 has no edge presence; egress from us-east-1 to a viewer in Lagos is 250 ms RTT and saturates at ~100 Gbps regional. **CDN is mandatory.** |
| **Single CDN (Akamai only)**                                    | one contract                             | A PoP outage during a Champions League stream takes down 30% of viewers. **Multi-CDN + ISP-cache** is the only way to absorb regional outages and 250-Tbps live bursts. |
| **Encode once at 4K, let player downsample**                    | save CPU                                 | Bandwidth doesn't downsample — the user on 3G still pulls 50 Mbps. **ABR ladder is mandatory.** |
| **No ContentID — let DMCA notices catch up**                    | simpler ingest                           | Major labels would sue us into bankruptcy within a year. ContentID is contractual. Without it, no licensing partnerships, no monetisation. |
| **One Postgres for video metadata + comments + view counts**    | one source of truth                      | View beacons are 500 K writes / s — Postgres WAL chokes. Use Bigtable for high-write streams; Spanner for low-write metadata. |
| **No ASR captions — let creators upload them**                  | save GPU                                 | ~70% of uploads have no captions at upload time → accessibility regression + 15% of view-time loss for hearing-impaired users + missed search-recall on caption-text. ASR is non-negotiable. |
| **Compute recommendations on every page-load**                  | always fresh                             | 800 M home-page loads / day × ~50 ms ranker call × ~10 KB feature vector = 8 GB / s memory bandwidth. Pre-compute hourly into Redis → 5 ms read. |
| **Sync write-then-replicate for view counter**                  | strong consistency                       | A video at 1 M views/min would lock the row at one Spanner shard → throughput ceiling ~5 K writes / s. **Use Redis HLL + Bigtable + Flink rollup, eventually consistent ≤ 60 s.** |
| **No transcoding distribution (one machine encodes one video)** | simple                                   | A 1-h 4K HDR video at 1× real-time on one GPU machine = 1 hour wall-clock to publish; user expectation is ≤ 60 s for 1080p. **Split video by GOP, parallelise encode across N workers.** |
| **No outbox; OMS produces directly to Kafka**                   | simpler                                  | If Kafka is down, the order is lost (or you split-brain — DB has it, Kafka doesn't). Outbox + transactional consumer is mandatory for "exactly once into store". |
| **Live = same pipeline as VOD**                                 | one codepath                             | VOD is *latency-tolerant; cache-friendly* (a video is the same for everyone). Live is *latency-sensitive; cache-hostile* (segments minted real-time, must propagate in seconds). They share components but the pipelines are distinct. |
| **Build our own CDN**                                           | save licensing fees                      | A global CDN is ~10 K nodes in 100 countries, $1B+ infra. Buy from Akamai / Cloudflare; *augment* with Open-Connect-style appliances inside ISPs for cheap egress on the long tail. |
| **No safety / NSFW scan**                                       | simpler ingest                           | Within a week we'd have CSAM, hate, and gore on the home page. App stores would delist. Mandatory. |

### Final architectural decisions (consequence of the above)

- **Source of truth for raw upload:** **GCS / S3 multi-region** with versioning + 11-nines durability + KMS-at-rest; **180-day retention** then deletion-eligible.
- **Source of truth for transcoded ABR variants:** **GCS / S3 standard storage** with cross-region replication; cheaper class because re-derivable.
- **Catalogue DB:** **Spanner / Vitess** (multi-region, strong consistency, sharded by `video_id`).
- **Comments / likes:** **Bigtable / Cassandra** (per-region, partitioned by `(video_id, ts_desc)`, eventual consistency).
- **View counter:** **Redis HLL (atomic INCR + HyperLogLog for unique-viewer)** + **Bigtable** for persistent counters; **Flink** rollup every 60 s.
- **Hot cache:** **Redis** for video metadata (TTL 5 min), reco rows (TTL 5 min), session lookups, rate-limit token buckets.
- **Async backbone:** **Kafka** with topics `video.uploaded`, `video.transcoded`, `video.indexed`, `watch.events`, `engagement.events`, `notify.events`, `audit.events`, `live.segments`, `features.stream`, `moderation.events`, `*.dlq`. RF=3, idempotent producer, transactional consumer + outbox pattern.
- **Real-time analytics:** **Apache Flink / Beam** consuming Kafka → **Druid / Pinot** for QoE + Creator Studio dashboards; **Spark** for nightly batch (recommendation training, royalty calc, retention metrics).
- **Multi-CDN:** **Akamai + CloudFront + Cloudflare + Limelight + Open-Connect-style ISP appliances**. Real-time switching via `cdn.qoe`.
- **DRM (only when applicable):** **Widevine + PlayReady + FairPlay** with shared CMAF ciphertext (CENC). Most public videos: no DRM, signed URLs only.
- **Recommendation:** offline pipeline (Spark + TF training, weekly model retrain) → online ranker (TF-Serving / Vertex AI) + per-session real-time re-rank from streaming features.
- **Live:** RTMP/SRT ingest → live transcoder (NVENC GPU farm) → packager → multi-CDN with origin-shielded LL-HLS / LL-DASH at ~5 s glass-to-glass.
- **Search:** **Elasticsearch** (BM25 / inverted index for keyword) + **Vespa** (vector ANN for semantic search); rebuild nightly + delta.
- **ContentID:** in-house service (Google has the gold-standard implementation), reads rights-holder fingerprint DB, writes match → block / monetise / track.

---

## 7. Data Model

### 7.1 `Video` (the catalogue row — the creator's truth)

```
Video {
  string  video_id            // YouTube uses 11-char base64 ID; here: UUIDv7 prefix-truncated
  string  channel_id          // owning channel (== creator user_id mostly)
  string  title               // ≤ 100 char
  string  description         // ≤ 5000 char
  string  thumbnail_url       // CDN-hosted; auto + creator-uploaded variants
  long    duration_ms
  string  category            // 'music' | 'gaming' | 'news' | 'edu' | 'comedy' | ...
  string[] tags               // creator-supplied
  string  language_primary    // 'en', 'hi', 'es', ...
  string[] languages_audio    // ASR-detected + uploaded audio tracks
  string[] languages_subtitle
  string  privacy             // 'PUBLIC' | 'UNLISTED' | 'PRIVATE'
  bool    is_kids             // COPPA flag
  bool    age_restricted
  string  visibility_country_block_list[]  // per-country block (DMCA / hate)
  string  visibility_country_allow_list[]  // optional whitelist (premium / regional)
  // Pipeline state
  string  ingest_status       // 'UPLOADING' | 'TRANSCODING' | 'PUBLISHED' | 'BLOCKED' | 'DELETED'
  string  raw_gcs_uri         // raw mezzanine
  // Monetisation
  bool    monetisation_eligible
  string  monetisation_state  // 'GREEN' | 'YELLOW' (limited ads) | 'RED' (no ads)
  string  contentid_state     // 'CLEAR' | 'MATCHED' | 'BLOCKED' | 'CLAIMED'
  // Audit
  long    upload_started_ms
  long    upload_completed_ms
  long    published_at_ms
  long    last_modified_ms
}
```

### 7.2 `VideoVariant` (the encoded ABR rungs — populated by the pipeline)

```
VideoVariant {
  uuid    variant_id
  string  video_id
  string  format                // 'HLS' | 'DASH'
  string  manifest_gcs_uri      // master playlist / MPD (.m3u8 / .mpd)
  string  manifest_url          // CDN-distributable URL (signed at watch-time)
  jsonb   ladder                // [{ rung: '1080p', codec: 'h264', bitrate_kbps: 5000, segments: 360 }, ...]
  string  cmaf_init_uri         // shared init segment
  string  drm_widevine_pssh     // null if no DRM (most public videos)
  string  drm_playready_pssh
  string  drm_fairplay_pssh
  string  encryption_kid        // hex 16-byte key id (null if no DRM)
  long    bytes_total
  long    duration_ms
  long    packaged_at_ms
}
```

### 7.3 `Channel` and `User`

```
User {
  uuid    user_id
  string  email
  string  phone
  string  display_name
  string  language_pref
  string  country_home          // billing / regulatory country, geo-locked at signup
  string  signup_source         // 'web' | 'ios' | 'android' | 'tv'
  bool    is_kids_account       // COPPA-protected
  long    created_at_ms
  long    last_login_at_ms
}

Channel {                       // 1 user → 1 default channel; 1 user → many "brand" channels
  uuid    channel_id
  string  user_id
  string  channel_handle        // '@mrbeast'
  string  display_name
  string  description
  string  avatar_url
  string  banner_url
  long    subscriber_count       // denormalised; refreshed every ~minute
  long    created_at_ms
  bool    monetisation_enabled
  string  monetisation_country
}

Subscription {                  // user_id subscribes to channel_id; bell-notification config
  uuid    user_id
  uuid    channel_id
  string  notification_pref     // 'ALL' | 'PERSONALISED' | 'NONE'
  long    subscribed_at_ms
  // PRIMARY KEY ((user_id), channel_id)  -- per-user subscriptions list query
  // SECONDARY INDEX on (channel_id) -- for fan-out "channel uploaded → notify subscribers"
}
```

### 7.4 `WatchSession` and `WatchEvent` (high-write, eventually consistent)

```
WatchSession {                   // one per (user_id, video_id, session)
  uuid    session_id
  string  user_id                // null if anonymous (use anonymous_id from cookie)
  string  video_id
  string  device_id
  string  device_kind            // 'ios' | 'android' | 'web' | 'smart_tv'
  string  geo_country
  string  user_agent
  long    session_start_ms
  long    session_end_ms
  long    cumulative_play_ms
  long    cumulative_pause_ms
  string  source                 // 'home' | 'search' | 'channel' | 'up_next' | 'external_link' | 'shorts_feed'
  string  source_video_id        // if source='up_next', the previous video
  // Bigtable: PRIMARY KEY ((user_id), session_start_ms desc, session_id)
}

WatchEvent {                    // append-only beacon; one per ~10 s of play
  uuid    event_id
  string  session_id
  string  user_id                // for per-user dedup
  string  video_id
  long    event_ts_ms
  string  event_type             // 'PLAY_START' | 'PROGRESS' | 'PAUSE' | 'RESUME' | 'SEEK' | 'COMPLETE' | 'BUFFER_START' | 'BUFFER_END' | 'BITRATE_CHANGE'
  long    position_ms
  jsonb   payload                // current_bitrate_kbps, throughput_kbps, dropped_frames, rebuffer_ms
  // Bigtable: PRIMARY KEY ((user_id), event_ts_ms desc, event_id)
  // also published to Kafka watch.events keyed by user_id
}
```

### 7.5 `Comment`, `Like`, `Subscription` (engagement)

```
Comment {
  uuid    comment_id
  string  video_id
  string  user_id                 // commenter
  string  parent_comment_id       // null for top-level; set for replies
  string  text                    // ≤ 10000 char
  long    created_at_ms
  long    last_edited_at_ms
  bool    is_pinned               // creator-pinned
  bool    is_hearted              // creator-hearted
  bool    is_creator_reply
  long    like_count              // denormalised; refreshed every ~5 s via Flink rollup
  long    reply_count             // denormalised; refreshed similarly
  string  moderation_state         // 'OK' | 'HOLDED_FOR_REVIEW' | 'HIDDEN' | 'DELETED'
  // Bigtable: PRIMARY KEY ((video_id), created_at_ms desc, comment_id)
  // Secondary index: (user_id, created_at_ms desc, comment_id) for "your comments" view
}

Like {                            // user likes a video / comment
  string  user_id
  string  target_id               // video_id or comment_id
  string  target_kind             // 'VIDEO' | 'COMMENT'
  long    liked_at_ms
  // Bigtable: PRIMARY KEY ((target_id), user_id)  -- supports "did this user like X?" query
  // Note: dislike on videos is private since 2021; only the creator sees aggregate dislike count
}
```

### 7.6 `ViewCount` (the famous one)

```
ViewCount {                       // Bigtable: PRIMARY KEY (video_id), single-row counter
  string  video_id
  long    total_views             // monotonic; eventually consistent, ≤ 60 s lag
  long    unique_viewers_today    // HLL approximation
  long    unique_viewers_alltime  // HLL approximation
  long    estimated_watch_seconds // for monetisation
  long    last_updated_ms
}

// Hot reads: video metadata page → Redis cache (TTL 30 s); fallback to Bigtable
// Hot writes: View Aggregator (Flink) batches 1-min tumbling windows, INCR per key
```

### 7.7 `RecommendationRow` (pre-computed)

```
RecommendationRow {              // Redis HSET: home:{user_id} -> { row_id -> [video_ids] }
  uuid    user_id
  string  row_id                 // 'home_main' | 'continue_watching' | 'subs_uploads' | 'because_you_watched_<vid>' | 'trending_in_country'
  int     row_position           // ordering on home page
  string[] video_ids              // ranked
  long    generated_at_ms
  long    valid_until_ms         // TTL = 5 min
  string  algorithm_version
}
```

### 7.8 `Live` and `LiveStream`

```
LiveStream {
  uuid    stream_id
  string  channel_id
  string  title
  string  description
  string  ingest_url             // RTMP / SRT push URL
  string  stream_key             // long-lived secret
  string  state                  // 'CREATED' | 'LIVE' | 'ENDED' | 'ARCHIVED'
  long    scheduled_start_ms
  long    actual_start_ms
  long    actual_end_ms
  long    peak_concurrent_viewers
  string  vod_video_id           // if archived → permanent VOD title
  string  dvr_window_seconds     // typically 4 h
}
```

### 7.9 Sample Spanner DDL — `videos` (the catalogue table)

```sql
CREATE TABLE videos (
    video_id              STRING(11)      NOT NULL,
    channel_id            STRING(36)      NOT NULL,
    title                 STRING(100)     NOT NULL,
    description           STRING(5000),
    thumbnail_url         STRING(MAX),
    duration_ms           INT64           NOT NULL,
    category              STRING(32),
    tags                  ARRAY<STRING(50)>,
    language_primary      STRING(8),
    languages_audio       ARRAY<STRING(8)>,
    languages_subtitle    ARRAY<STRING(8)>,
    privacy               STRING(16)      NOT NULL DEFAULT ('PUBLIC'),
    is_kids               BOOL            NOT NULL DEFAULT (FALSE),
    age_restricted        BOOL            NOT NULL DEFAULT (FALSE),
    visibility_block_list ARRAY<STRING(2)>,
    visibility_allow_list ARRAY<STRING(2)>,
    ingest_status         STRING(16)      NOT NULL DEFAULT ('UPLOADING'),
    raw_gcs_uri           STRING(MAX),
    monetisation_eligible BOOL            NOT NULL DEFAULT (FALSE),
    monetisation_state    STRING(16),
    contentid_state       STRING(16)      NOT NULL DEFAULT ('PENDING'),
    upload_started_ms     INT64           NOT NULL,
    upload_completed_ms   INT64,
    published_at_ms       INT64,
    last_modified_ms      INT64           NOT NULL,
) PRIMARY KEY (video_id);

CREATE INDEX videos_by_channel ON videos (channel_id, published_at_ms DESC);
CREATE INDEX videos_published_idx ON videos (published_at_ms DESC) WHERE ingest_status = 'PUBLISHED';
CREATE INDEX videos_by_category ON videos (category, published_at_ms DESC);
```

### 7.10 Sample Bigtable schema — `watch_events` (high-write append-only)

```
Table: watch_events
Row key: <user_id_hex> + '#' + <reverse_ts_ms> + '#' + <event_id>
Column families:
  e: {
    type: STRING,           -- PROGRESS | PAUSE | SEEK | ...
    video_id: STRING,
    session_id: STRING,
    position_ms: INT,
    bitrate_kbps: INT,
    throughput_kbps: INT,
    rebuffer_ms: INT,
    device_kind: STRING,
    geo_country: STRING,
  }
GC policy: { max_age: 90 days }    -- hot tier; 90 d → S3/GCS cold tier via export

Table: comments
Row key: <video_id_hex> + '#' + <reverse_ts_ms> + '#' + <comment_id>
Column families:
  c: {
    user_id: STRING,
    text: BYTES,            -- compressed (Snappy) for long comments
    parent_comment_id: STRING,
    is_pinned: BOOL,
    is_hearted: BOOL,
    is_creator_reply: BOOL,
    like_count: INT,
    reply_count: INT,
    moderation_state: STRING,
    created_at_ms: INT,
  }
```

> **Why Bigtable / Cassandra for watch events + comments?** **500 K writes / s** of watch beacons + **3 K writes / s** of comments cannot be served by Spanner without either painful sharding or unacceptable latency tail. Bigtable's wide-row layout is purpose-built: writes are append-only to a tablet; reads are partition-bounded ("give me video X's comments, paginate by ts desc" is one tablet scan). Spanner stays for the **strongly-consistent** stuff (video metadata, channel ownership, monetisation state) where the cost of cross-region 2-phase commit is justified by correctness.

---

## 8. High-Level Architecture (HLD)

![YouTube — High-Level Architecture](./assets/06-design-youtube-hld.drawio)

> **Editable source:** [`assets/06-design-youtube-hld.drawio`](./assets/06-design-youtube-hld.drawio). Open with the **hediet.vscode-drawio** extension in Cursor / VS Code, or in [diagrams.net](https://app.diagrams.net/) (`File → Open from device`). To embed-as-svg in GitHub, save as `.drawio.svg` (the SVG carries the editable XML inside it).
>
> The diagram is laid out top-to-bottom along the **request flow**: clients at the top, edge / CDN + API GW + WS GW + RTMP, core services row (Upload / Watch-Manifest / Search / Recommendation / Engagement / Notification / Ads / Live-Transcoder / View-Beacon / DRM), Kafka backbone, processing workers (transcoding pool / thumbnail / ASR / ContentID / safety / view-aggregator / trending / reco-pipeline / search-indexer / notification-worker / outbox publisher), the storage plane, the analytics + observability plane, and external actors (rights-holders DB, ad exchanges, payment partners, DMCA partners). The right-hand panels are the 4 sticky-note invariants (TTFF budget, transcoding throughput, CDN egress, consistency contract), the 8 numbered/colour-coded flows (Upload, Transcoding, Watch, Recommendation, Engagement, View-Count, Live, Search), the style legend, and the capacity-back-of-envelope. Each step in §11 is annotated with the matching arrow id (e.g., `T3`, `W1`).

### 8.1 Component roster (every box on the canvas)

#### Edge plane

| Component | Role |
|-----------|------|
| **Mobile App / Web SPA / Smart TV / Console / Creator Studio / Live Encoder (OBS / ffmpeg)** | end-user surfaces; each ships a native player (ExoPlayer on Android, AVPlayer on iOS, Shaka Player / hls.js on web), a QoE telemetry SDK, and (for Creator) a chunked resumable upload client |
| **CDN Edge (Akamai / CloudFront / Cloudflare / Limelight + Open-Connect-style YouTube-Cache appliances at ISP)** | terminates TLS for *segment* requests; caches HLS / DASH segments + manifests; serves ~95 %+ of egress |
| **Origin Shield (regional)** | a single regional cache layer in front of the multi-CDN to dedupe origin pulls; without it, 5 CDNs × 200 PoPs = 1 K origin pulls per cold segment |
| **API Gateway (Envoy / GFE)** *(N pods, multi-region active-active)* | TLS, OAuth2 + JWT validate, **per-user + per-IP rate-limit** (token bucket in Redis), **Idempotency-Key** for writes (24 h dedup), **geo-IP** (MaxMind), routes to REST services |
| **WebSocket Push Gateway** *(M pods, sticky)* | live chat, live notifications, upload-progress, "new video from sub" pushes; heartbeat every 15 s, ring-buffer 30 s for resume |
| **RTMP / SRT Ingest Gateway** | regional anycast; primary + standby per stream; auth via stream key; forwards to Live Transcoder |
| **Auth Service** | OAuth2 / OIDC (Google Sign-In primary), TOTP / passkey 2FA, refresh-token 30 d, access-JWT 1 h |

#### Core service row (user-facing, REST)

| Component | Role |
|-----------|------|
| **Upload Service**           | issues resumable upload URL (TUS / Google resumable protocol), validates chunks (sha256 of each chunk + total file), assembles, INSERTs `videos` row with `ingest_status='UPLOADING'`, produces `video.uploaded` |
| **Watch / Manifest Service** | `GET /watch?v=…` → returns metadata + signed manifest URL + (if applicable) DRM license URL; checks privacy / geo / age-restriction; picks CDN PoP via ASN-routed table |
| **Search Service**           | thin Elasticsearch + Vespa client; multi-language tokeniser, BM25 + ANN vector hybrid, personalisation re-ranker, typeahead suggestions |
| **Recommendation Service**   | reads pre-computed Redis rows; on-the-fly re-rank using session features; loads ranker model from model store |
| **Engagement Service**       | `POST /comment`, `/like`, `/subscribe`; writes to Bigtable + outbox row in same transaction; publishes `engagement.events` |
| **Notification Service**     | consumes `notify.events`; fans out to FCM / APNS / SES / WebSocket; rate-caps per recipient (avoid spam) |
| **Ads Decisioning**          | thin layer over external ad exchanges (DV360, AdSense); decides pre-roll / mid-roll slot; reports impressions; out-of-scope for deep design |
| **View / Beacon Collector**  | accepts player view-beacons (≥ 30 s watch-time triggers a "view"); dedupes by `(user_id, video_id, day)`; produces `watch.events` |
| **Live Transcoder**          | NVENC GPU pool; LL-HLS / LL-DASH packager; chunks at 1 s for low latency; writes to live origin GCS |
| **DRM License Service**      | Widevine / FairPlay / PlayReady; HSM-backed master signing keys; regional delegate-cert replicas; ~30-min license TTL |
| **User / Profile Service**   | small CRUD over Spanner — `users`, `channels`, `subscriptions`, watch-history preferences |

#### Pipeline plane (left side of canvas)

| Component | Role |
|-----------|------|
| **Transcoding Workers (GPU + CPU pool, ~30–50 K nodes)** | FFmpeg / x264 / VP9 / AV1 / HEVC; **split video into N GOP chunks** → distribute → per-segment parallel encode; outputs HLS .ts + DASH .m4s; ladder = 144p · 240p · 360p · 480p · 720p · 1080p · 1440p · 2160p (4K HDR) |
| **Thumbnail Service**            | extracts N candidate frames (t=10/30/50/70/90% of duration) → passes through CV "click-worthy-ness" classifier → picks 3 best; creator can override with custom upload |
| **ASR / Captions Service**       | speech-to-text (Whisper / proprietary); auto-generates subtitles per language; auto-translates to top-10 languages; writes `.vtt` to GCS |
| **ContentID Engine**             | audio fingerprint (chromaprint) + video fingerprint (pHash) → matches against rights-holder reference DB → decides block / monetise / track; updates `videos.contentid_state` |
| **Safety / NSFW Scan**           | image classifier (ResNet-style) + audio classifier; PhotoDNA hash match for CSAM (instant block, NCMEC report); hate / violence detector; outputs to `moderation.events` |
| **View Aggregator (Flink / Beam)** | tumbling 1-min windows per `(video_id, day)`; HyperLogLog for unique-viewer count; INCR Bigtable + Redis counters; emits `features.stream` updates |
| **Trending Engine**              | Top-K via Count-Min Sketch + Heap; per-region per-category windows; refreshes every 10 min |
| **Recommendation Pipeline (Spark + Flink + TF)** | offline candidate-gen training (matrix factorisation + collab + content-based); offline ranker training (DNN two-tower / multi-task); A/B framework; publishes to feature store + model store |
| **Search Indexer**               | consumes `video.indexed`; builds inverted index (Elasticsearch) + ANN vector index (Vespa); rebuild nightly + delta indexing within minutes |
| **Notification Worker**          | consumes `notify.events`; fans out to APNS / FCM / SES / WebSocket; back-off + DLQ on failures; dedupe within 24 h |
| **Outbox Publisher** *(per shard)* | tails `*_outbox` rows in Spanner / Bigtable → produces to Kafka with `at-least-once + idempotent dedupe`; the lynchpin for "exactly once into store" semantics |
| **Stuck-Job Sweeper**            | every 5 min: any `videos` row with `ingest_status='UPLOADING'` older than 30 min without a `video.transcoded` event → re-enqueue; safety net for lost messages |

#### Async backbone (Kafka)

| Topic                   | Partitions | Producer                                | Consumer                                                                    | Notes |
|-------------------------|-----------|-----------------------------------------|-----------------------------------------------------------------------------|-------|
| `video.uploaded`         | 48        | Upload Service                          | Transcoding Workers                                                         | NEW / MULTIPART_DONE; keyed by `video_id` |
| `video.transcoded`       | 48        | Transcoding Workers                     | Watch Service (mark PUBLISHED), CDN Pre-warmer                              | LADDER_READY / THUMB_READY |
| `video.indexed`          | 24        | ASR + ContentID + Search Indexer        | Search Indexer (downstream)                                                 | text + ASR + tags ready |
| `watch.events`           | 256       | View Beacon Collector                   | View Aggregator (Flink), Recommendation feature pipeline, ClickHouse export | view-beacon · seek · skip; key=`user_id` |
| `engagement.events`      | 128       | Engagement Service (via Outbox)         | Notification Worker, Recommendation feature pipeline, Audit                 | like · comment · subscribe; key=`user_id` |
| `notify.events`          | 128       | derived from engagement + upload events | Notification Worker                                                         | new-upload · reply · milestone; key=`recipient_user_id` |
| `audit.events`           | 32        | every service                           | Audit ingestor → S3 / GCS WORM                                              | every login / upload / DMCA / admin action; 7 y retention |
| `*.dlq`                  | 8 each    | every consumer                          | manual triage                                                               | poison messages |
| `live.segments`          | 64        | Live Transcoder                         | CDN Edge (via packager), DVR recorder                                       | 1-s LL-HLS chunk events; key=`stream_id` |
| `features.stream`        | 256       | View Aggregator + Engagement            | Recommendation Pipeline (online learner)                                    | real-time ML features (impressions, CTR, watch-time deltas) |
| `moderation.events`      | 16        | ContentID + Safety + DMCA partners      | Moderation Console                                                          | NSFW · copyright hit · hate-speech flag |

#### Storage plane

| Store | What | Why |
|-------|------|-----|
| **GCS / S3 — Origin RAW (3-region, 11-nines, KMS-encrypted, 180-day retention)** | original master upload | the only irreplaceable artifact; recoverable for re-encode if codec / ladder strategy changes |
| **GCS / S3 — Origin TRANSCODED (standard storage, cross-region replicated)** | HLS .ts + DASH .m4s segments + manifests + thumbnails + .vtt captions | re-derivable from RAW so cheaper class is fine; fronted by CDN |
| **Spanner / Vitess — Video Metadata DB** | `videos`, `channels`, `users`, `subscriptions`, `playlists`, `monetisation` | OLTP, multi-region, strong consistency, sharded by `video_id` (videos) and `user_id` (users) |
| **Bigtable / Cassandra — Comments & Likes** | `comments`, `likes`, `dislikes` | high-write append-mostly, partitioned by `video_id`, eventual cross-region |
| **Bigtable + Redis HLL — View Counters** | per-video view counts + unique-viewer HLL approximations | hot atomic INCR in Redis; flush every 60 s to Bigtable; HLL for unique-viewer dedupe |
| **Elasticsearch + Vespa — Search Index** | inverted index over title / desc / ASR captions / tags + ANN vector index over title+desc embeddings | BM25 keyword + ANN semantic; rebuild nightly + delta minutes |
| **Bigtable (online) + BigQuery (offline) — Feature Store** | user features, video features, context features for the ranker | online: sub-ms reads; offline: nightly Spark training |
| **Model Store (versioned)** | DNN ranker checkpoint files | A/B-flag promotion; TF-Serving / Vertex AI loads from here |
| **Redis Hot Cache** | video metadata (TTL 30 s–5 min), per-user reco lists (TTL 5 min), view counters (atomic INCR), rate-limit token buckets, session lookups | sub-ms reads on the hot path |
| **GCS Coldline / S3 Glacier — Cold Archive** | raw uploads >180 d, transcoded variants of videos with <1 view/day | cost ~1/10th of hot tier; rehydrate on demand (~minutes) |
| **Audit Log (ClickHouse + S3 cold)** | login / upload / DMCA / admin actions | 7-year retention for compliance; queryable via ClickHouse for recent, archived to GCS for old |

#### Analytics / observability plane

| Component | Role |
|-----------|------|
| **Data Warehouse (BigQuery / Snowflake)** | watch.events + engagement aggregated daily; feeds offline reco training, royalty calc, creator analytics dashboard |
| **Real-time OLAP (Druid / Pinot)** | low-latency aggregations over `watch.events` + `engagement.events`; powers Creator Studio dashboards |
| **Prometheus + Grafana + Jaeger** | TTFF p50/p95/p99, cache_hit_ratio per PoP per video, transcode_lag_seconds, reco_serve_latency_ms, kafka_consumer_lag, … |
| **Stuck-job Sweeper** | at-least-once safety net (described above) |
| **Moderation Console** | humans + ML triage; flagged content review workflow; DMCA / NSFW / hate / legal hold |

#### External actors

| Actor | Role |
|-------|------|
| **Rights-Holders DB** (record labels, studios) | publishes audio + video fingerprint reference set to ContentID Engine; receives match feed for monetisation routing |
| **Ad Exchanges** (DV360, AdSense, third-party SSPs) | real-time bidding; reporting (impressions, clicks); the actual ad creative is fetched from the bidder |
| **Payment Partners** (Stripe, Google Pay, UPI, Razorpay) | YouTube Premium subscription processing; Super Chat micro-payments |
| **Legal / DMCA Partners** | takedown notices, counter-claims, transparency reports; routes to Moderation Console |

### 8.2 Layered view (ASCII, for the whiteboard)

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                       YOUTUBE                                            │
│                                                                                          │
│   Mobile · Web · TV · Console · Creator Studio · Live Encoder                            │
│        │ HTTPS                  │ Segment GET (~95 % of all bytes)                       │
│        ▼                        ▼                                                        │
│   ┌──────────┐            ┌───────────────────────────────────────────────┐              │
│   │ API GW   │            │  Multi-CDN: Akamai · CloudFront · Cloudflare ·│              │
│   │ + WS GW  │            │  Limelight · YouTube-Cache (ISP-resident,     │              │
│   │ + RTMP   │            │  Open-Connect-style — ~15 K appliances)       │              │
│   │ (N pods, │            └─────────────┬─────────────────────────────────┘              │
│   │ multi-   │                          │                                                │
│   │ region)  │                          │ origin-shield miss                             │
│   └────┬─────┘                          ▼                                                │
│        │                          ┌─────────────────────────┐                            │
│        │                          │ Origin Shield (regional) │                           │
│        │                          │  →  GCS / S3 transcoded   │                          │
│        │                          └─────────────────────────┘                            │
│        ▼                                                                                 │
│   ┌──────────────────────────────────────────────────────────┐                           │
│   │ Upload · Watch · Search · Recommendation · Engagement ·  │                           │
│   │ Notification · Ads · View Beacon · Live Transcoder · DRM │                           │
│   └──┬───────────────────────────────────────────────┬───────┘                           │
│      │ (REST)                                         │ produce                          │
│      │                                                ▼                                  │
│      │     ┌────────────────────────────────────────────────────────────────┐            │
│      │     │   Kafka backbone (RF=3)                                         │            │
│      │     │   video.uploaded · video.transcoded · video.indexed ·            │            │
│      │     │   watch.events · engagement.events · notify.events ·             │            │
│      │     │   audit.events · live.segments · features.stream · moderation     │            │
│      │     └────┬───────────────────────────────────────┬─────────────────────┘            │
│      │         │                                       │                                 │
│      ▼         ▼                                       ▼                                 │
│  ┌───────────┐ ┌──────────────────────────────────┐  ┌──────────────────────────────┐    │
│  │ Spanner   │ │ Bigtable / Cassandra             │  │ Recommendation Pipeline      │    │
│  │ videos /  │ │ comments / likes / watch_events  │  │ Spark batch + online ranker  │    │
│  │ channels /│ │ view_counters (with Redis HLL)   │  │ (TF-Serving / Vertex AI)     │    │
│  │ subs / ... │ └──────────────────────────────────┘  └─────────┬───────────────────┘    │
│  └───────────┘                                                  │                        │
│                                                                  ▼                        │
│   ┌─────────────────────────┐    ┌──────────────────────────────────────────┐            │
│   │ Transcoding pool +      │    │ Live Transcode (GPU) → LL-HLS Packager → │            │
│   │ Thumbnail + ASR +       │    │ Origin Shield → Multi-CDN                │            │
│   │ ContentID + Safety +    │    │                                           │            │
│   │ View Aggregator         │    └──────────────────────────────────────────┘            │
│   └─────────────────────────┘                                                            │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 The control plane (etcd / Spanner-meta / Consul)

The control plane is small and intentionally **not** on the watch path:

- **Live ingest leader election** — primary vs standby ingest pod per stream (`/yt/live/{stream_id}/leader`).
- **CDN-router config** — per-region multi-CDN weights (refreshed every 30 s based on `cdn.qoe`).
- **Feature flags & per-region kill-switches** — read-cached in every pod, fail-closed on errors.
- **Encoder pool autoscaler signals** — backlog depth per priority queue (P0 live > P1 new release > P2 catch-up).
- **A/B experiment assignment** — sticky per-user bucket (sha1(user_id) % 1000); cached in JWT.

### 8.4 The 9 invariants (printed on the diagram, panel "Invariants")

If you can rattle off these nine on a whiteboard you have narrated the entire correctness story:

1. **CDN serves the bytes; origin serves the metadata.** ~95 % of bytes egress are video segments from CDN / ISP-cache. Origin / our app servers see < 5 % of byte traffic. Confusing the two scales us out of business.
2. **One CMAF source, two manifest formats.** HLS and DASH share the *same* CMAF segments. We don't double-encode; we double-package. Saves 50 % storage.
3. **Every upload runs ContentID + Safety in parallel with transcoding.** Don't serialise them. ContentID is *the* contractual gate for monetisation; Safety is the contractual gate for app-store + advertiser-presence.
4. **View counts are eventually consistent ≤ 60 s.** Famous "301 → 302" view freezes were a side effect of strict deduplication. We use Redis HLL + Flink rollup; never block a view-page render on a synchronous count read.
5. **Comments are eventual; metadata is strong.** A comment may take ~3 s to appear. A creator flipping a video to "private" must be effective immediately (Spanner strong-read).
6. **DRM is rare.** ~99% of public videos serve open HLS/DASH. DRM only kicks in for Premium-only / Movies / Kids gating. Don't over-engineer the hot path.
7. **Manifest URLs are short-lived signed URLs (~6 h TTL).** Re-signed on each watch. Forces every play to go through Watch Svc → privacy / geo check.
8. **Live ingest is active/standby per stream, with sequence-number persistence.** A failover during a Mr Beast stream must not cause a viewer-visible glitch >1 s.
9. **Every upload, every play, every comment, every DMCA action emits an audit event.** Append-only S3-WORM, 7-year retention. Used for legal compliance + creator analytics + revenue-share calc.

### 8.5 Plain-English Walkthrough — Every Box on the Diagram

#### 8.5.1 The Edge Plane

##### Mobile App / Web SPA / Smart TV / Console / Creator Studio / Live Encoder

These are six faces of the same platform. Each ships:

- **A native player** (ExoPlayer on Android, AVPlayer on iOS, Shaka or hls.js on web, native on TV/console) that knows how to download an HLS / DASH manifest, choose the right rung of the ABR ladder, fetch segments, optionally push them through the **EME** (Encrypted Media Extensions) pipeline to the platform's **DRM** (rare — only for Premium / Movies content), and render frames.
- **A small in-app HTTP client** for `/watch`, `/search`, `/home`, `/comment`, `/like`, `/subscribe`. Routes through API Gateway.
- **A QoE telemetry SDK** that beacons rebuffer events, dropped frames, throughput estimates, bitrate switches, and the famous **30-s "view" beacon** every ~10 s while the user watches.
- **Creator Studio** additionally ships a **resumable multipart upload client** that reads the source file in 8 MB chunks (256 KB on poor networks), computes per-chunk SHA-256, retries individual chunks on failure, and exposes upload progress via WebSocket to the studio UI. The mobile YouTube app has a slim version of this for "Upload from phone".
- **Live Encoder** (OBS / ffmpeg / mobile-go-live) pushes RTMP or SRT to a **regional anycast** ingest URL. The encoder client is shipped by the user (we don't own OBS); we publish a cookbook + recommended settings.

The reason every device has its own native player (not a single web-view) is that decoding 4K HDR HEVC at 60 fps requires hardware acceleration that only the platform's native API exposes. We can't paper over them with one web-view player; we ship five+, with a shared protocol layer.

##### CDN Edge (Multi-CDN + ISP-cache)

This is where ~95 % of our egress lives. A user requesting `https://yt-cdn.example/v/{video_id}/720p/seg00042.ts` is served from a PoP within ~50 km — probably on Akamai's edge or, more often, our own **YouTube-Cache appliance inside the user's ISP** (Reliance Jio, Comcast, Verizon, etc.). The CDN never goes to GCS unless its cache misses; cache is keyed by URL path + segment number, with `Cache-Control: public, max-age=31536000, immutable` (segments are immutable — same content forever — so cache-forever is safe).

The PoP also terminates TLS, runs WAF rules ("block IPs with > 1000 req/s"), and emits its own access logs that we Kafka-ingest for analytics.

The **multi-CDN dimension** is the subtle part: a single CDN's PoP outage during a marquee event would take down 30 % of viewers. We run **5 CDNs in parallel** plus our **15 K ISP-cache appliances**, with a per-region weighting that updates every 30 seconds based on QoE. The ISP-cache is the true workhorse — it serves ~50 % of all bytes globally because it's *inside* the ISP's data centre and the ISP doesn't pay transit / peering on it.

##### Origin Shield

A single regional cache layer in front of the multi-CDN. Every CDN's "cache miss" goes to the *regional shield*, not directly to GCS. Without it, 5 CDNs × 200 PoPs each = 1 000 origin requests per cold segment. With shield: 1 000 → ~5 (one per CDN region). Cuts origin egress by ~200×. Implementation: NGINX/Varnish cluster per region (us-east, eu-west, ap-south), 1–2 TB SSD per node, ~10 nodes per region.

##### API Gateway

Front door for REST. Validates the JWT, rate-limits per-user and per-IP (token bucket in Redis), attaches the `country` (from MaxMind GeoIP lookup) and `subscription_plan` to internal request headers, then routes to the matching service. Idempotency-Key (24 h TTL in Redis) protects writes from retries. The single most important property: **API GW is never in the segment path**. Round-trip to the API is only for "watch decision" + "watch progress beacon", once per play and every 10 s respectively.

##### WebSocket Push Gateway

Sticky long-lived connections (sticky LB by `user_id` hash). 5–10 K connections per pod. Used for: live chat (~10K msg/sec per stream), live notifications ("a friend just commented"), upload-progress streaming back to Creator Studio, "Up Next" pre-fetch hints. Heartbeats every 15 s; per-user 30-s ring buffer for resume after a network blip (mobile reconnect on subway exit).

##### RTMP / SRT Ingest Gateway

Regional anycast endpoints — `rtmp://ingest-us-east.yt.example/{stream_key}`. Production trucks / Mr Beast / casual mobile-go-live all push here. SRT preferred (re-transmits dropped UDP packets, better for unreliable internet); RTMP for legacy. Active/standby per stream key (etcd lease, 3 s TTL); on primary failure, standby promotes itself; from the encoder's POV nothing happened.

##### Auth Service

Stateless OAuth2 server. Issues access tokens (short, 1 h) + refresh tokens (long, 30 d). The refresh token lifetime is 30 d so smart TVs that haven't been turned on for 3 weeks still resume signed-in. Sessions live in Redis as `session:{token_jti}` so we can revoke; the JWT itself carries `user_id`, `country_home`, `subscription_plan`, `is_kids`. Every other service trusts the JWT.

#### 8.5.2 The Service Plane

##### Upload Service

The hot path that creators experience. When a creator clicks "Upload":

1. `POST /upload/init { filename, size, sha256, codec_hint }` → returns `{ upload_id, resumable_url, chunk_size: 8388608 }`. The `resumable_url` is a **signed GCS URL** the client uploads chunks **directly to GCS**, bypassing our app servers. We do this because routing 10 PB / day through our app pods would burn a fortune in bandwidth and CPU.
2. Client `PUT {resumable_url}` with `Content-Range: bytes <start>-<end>/<total>`. GCS handles the actual chunk receipt; on each chunk, GCS fires an event we catch via **Pub/Sub** that updates `(upload_id, last_completed_offset)` in Redis with 30-day TTL.
3. On final chunk, GCS auto-stitches into a single object; client sends `POST /upload/complete { upload_id }`; Upload Service verifies the assembled SHA-256, INSERTs `videos` row with `ingest_status='UPLOADING'`, produces `video.uploaded` to Kafka.
4. Returns `{ video_id, status: 'PROCESSING' }` to client.

Why direct-to-GCS chunked upload? Three reasons:
- **Cost** — bypasses our egress.
- **Throughput** — GCS scales to TB/s; our app pods don't.
- **Resume** — if the user's network drops, the next chunk just retries from `last_completed_offset` in Redis. No need to start over.

Idempotency: Upload Service dedupes on `(user_id, sha256)` — if you upload the same file twice, you get the same `video_id` back (no double-storage). This breaks the "I want two separate copies" intent for ~0.1% of users (re-encoding edge cases); they can append a byte and try again.

##### Watch / Manifest Service

The **gatekeeper of every watch**. When a viewer taps a video, the player calls `GET /watch?v={video_id}`:

1. **Read video metadata** — Redis cache hit (~95 %); fallback to Spanner (~30 ms p95). Returns `{ video_id, title, duration, owner_channel, privacy, age_restricted, country_block_list, monetisation_state, contentid_state }`.
2. **Privacy check** — if `privacy='PRIVATE'` and viewer is not in `allowed_users` list → 404. If `privacy='UNLISTED'` → must come with a direct link (no list / search).
3. **Geo check** — viewer's IP geo (from JWT or MaxMind in-process) compared to `country_block_list` / `country_allow_list` → 451 if blocked.
4. **Age check** — if `age_restricted` and viewer has no age verified → 451 with "must be signed in and >= 18".
5. **Build manifest URL** — pre-built per-rendition manifest in GCS (`gs://yt-transcoded/{video_id}/master.m3u8`); we just **sign** the URL with HMAC + 6 h TTL: `https://yt-cdn.example/v/{video_id}/master.m3u8?expires=...&sig=...`.
6. **Pick CDN PoP** — read the per-region CDN routing config from etcd / Consul (cached locally, refreshed every 30 s); pick the best CDN by weighted random (default: ISP-cache 50 %, Akamai 30 %, CloudFront 20 %); rewrite the manifest URL to the chosen CDN host.
7. **DRM** (rare path, only if Premium-only / Movies / Kids-restricted) — emit a DRM license URL the player can challenge; server-side, the DRM Service mints a per-session license valid 30 m.
8. **Emit Kafka `watch.events.PLAY_REQUESTED`** — for analytics; partition-keyed by `user_id`.
9. **Return** `{ manifest_url, drm_license_url?, captions_urls: [...], up_next_video_ids: [...] }` to the player.

Whole thing is < 80 ms p99. Every part is optimised for tail latency: subscription / privacy is cached in Redis, manifest URL is just HMAC-signed in-memory, CDN routing config is in-pod.

If we lost this service, no new watches would start, but **already-playing streams continue uninterrupted** because they have manifest + segments from CDN. Watch Svc failure is degrading, not catastrophic.

##### Search Service

A thin layer over Elasticsearch + Vespa. The interesting parts:

- **Multi-language tokenisation** — Hindi, Tamil, Telugu, Bangla, English, Spanish, Korean, Japanese — each language has its own analyzer (ICU + stemmer + synonym list).
- **Hybrid retrieval** — BM25 over title + description + ASR captions + tags (Elasticsearch) **+** ANN nearest-neighbours over the title-description embedding (Vespa). Combine via score fusion (weighted sum or learning-to-rank).
- **Personalisation re-rank** — top-50 candidates from retrieval → re-ranked by a small DNN that takes (user_features, video_features, query_features) → returns top-10.
- **Typeahead suggestions** — Trie + Top-K sketch over recent queries; refreshed every 10 min from `search.queries.stream`. p99 ≤ 50 ms.
- **Voice-friendly** — TVs send transcribed voice queries that are noisy ("the office" comes through as "thee offiss"); we lowercase + remove duplicate vowels + retry.

##### Recommendation Service

The thing that makes YouTube *YouTube*. The read path is intentionally trivial:

1. `GET /home` → reads pre-computed Redis row `home:{user_id}` (HSET of `row_id → [video_ids]`). Sub-millisecond.
2. **Online re-rank** — top of each row re-ranked using session features: last 5 plays, current local time, just-watched-now signal. Small DNN service (TF-Serving) takes ~5 ms p95.
3. **Cold start** (new user, or first home page after long absence) → fall back to per-country popularity row + trending row. Eventually the offline pipeline catches up and personalises.

The expensive offline pipeline (Spark + TF training) is described in §13.

##### Engagement Service

Handles `POST /comment`, `/like`, `/subscribe`. Three things to remember:

- **Toxicity moderation hook** — every comment runs through an ML classifier (~10 ms p95) before being persisted; high-toxicity → held for review (`moderation_state='HOLDED'`); the user sees a "your comment is being reviewed" notice.
- **Rate-limit per channel** — a channel suffering a comment-bot attack gets per-source-IP rate-limited; legitimate creators can opt into "approved-users-only" mode.
- **Outbox pattern (mandatory)** — comment INSERT into Bigtable + outbox row in the same Spanner transaction (or, at Bigtable scale, atomic mutation across a single row); Outbox Publisher tails outbox and produces `engagement.events`. Without outbox, we'd lose ~0.01 % of comments to Kafka outages — enough to anger creators.

##### Notification Service

Stateless consumers of multiple Kafka topics. Each event maps to 0..N notifications based on user prefs:

```
engagement.events.comment_reply →
   if prefs.email && !muted("replies"): send email via SES
   if prefs.push && !muted("replies"): send push via FCM/APNS
   if prefs.in_app: WebSocket push frame
```

Idempotency: each notification carries `(event_id, channel, user_id)` as a hash; INSERT into Bigtable `notifications_sent` with that as PRIMARY KEY. A redelivered Kafka message no-ops on the second insert.

Rate-limiting: dedupe within a 24-hour window per (user_id × channel × kind) so a user doesn't get bombed with "new release" notifications if a subscribed channel uploads 10 videos the same day; we batch into a daily digest unless the user opted in to immediate.

Fan-out for big channels: a Mr Beast upload notifies ~300 M subscribers; we fan-out via per-shard async workers (sharded by `recipient_user_id`) producing to `notify.events` (key=`recipient_user_id`); Notification Worker consumes per-shard. ~30 min to drain the full fan-out — acceptable for "new upload" notifs.

##### Ads Decisioning

A thin layer over external ad exchanges (DV360, AdSense, third-party SSPs). Decides whether to insert a pre-roll / mid-roll / banner; hits the bidder; gets back the ad creative URL; embeds it in the manifest (SSAI for live; CSAI for VOD). Reports impressions / clicks back via a beacon. We don't own the bidders or the creatives; we own the *insertion decision* and the *targeting features* (which come from the same feature store the recommender uses).

##### View / Beacon Collector

The endpoint that catches the famous "30-second view" beacon. The player heartbeats `/beacon` every 10 s while playing; if cumulative play-time crosses 30 s for `(user_id, video_id, day)` and that combo hasn't already counted today, we mint a "view" event:

```
POST /beacon { user_id, video_id, session_id, position_ms, throughput_kbps, bitrate_kbps, ... }
   ↓
View Beacon Collector:
   1. Dedup: SADD seen_views:{day}:{user_id} {video_id}; if not added → already counted today → no-op
   2. Produce watch.events.VIEW (key = user_id, payload = { video_id, day, ... })
   3. Return 204 No Content
```

For anonymous viewers (no `user_id`), dedupe by `anonymous_id` cookie. Lossy because cookies clear; that's the SLA we sign up for.

##### Live Transcoder

Same FFmpeg / NVENC binaries as VOD but configured for **chunked encoding** with `keyframe_interval = 1 s`, output **CMAF chunked fragments of 200 ms**. The pipeline runs in a tight loop: as bytes arrive from the ingest gateway, encode, push to packager. End-to-end latency from encoder to packager is ~500 ms.

Five-rung live ladder (240p / 480p / 720p / 1080p / 4K-if-source-supports). Each rung runs on a dedicated GPU partition; encoder fans out from a single source in shared memory.

##### DRM License Service

For the rare case of Premium-only / Movies / Kids-gated content. Three flavours, one master signing chain:

- **Widevine** — Google's, used on Android, Chrome, ChromeOS, Cast, most Smart TVs.
- **PlayReady** — Microsoft's, used on Edge, Xbox, many Smart TVs and STBs.
- **FairPlay** — Apple's, used on iOS, macOS, tvOS, Safari.

Master keys live in Cloud HSM (Thales / AWS KMS + HSM). Per-region license replicas hold *delegate* signing certs (rotated daily) so a regional outage of HSM doesn't tank license issuance globally — we keep ~24 h of pre-signed delegates.

##### User / Profile Service

Tiny CRUD service over Spanner. Account hierarchy: `User → 1 default Channel → optional N "brand" Channels`. The interesting bit is **subscriber count denormalisation** — we cache per-channel subscriber counts in a Redis ZSET refreshed every minute via Flink rollup over `engagement.events.subscribe`. Reading "how many subscribers does Mr Beast have" is a sub-ms Redis ZSCORE; writing a new subscription is an immediate Bigtable INSERT + an async ZINCRBY.

#### 8.5.3 The Pipeline Plane (left side)

##### Transcoding Workers

The most CPU-intensive layer in the entire system. ~30–50 K GPU-equipped workers (NVENC for H.264 / H.265 / AV1 / VP9). **Priority queues**:

- **P0 (live)** — sub-second, dedicated capacity
- **P1 (new release / next-day publish)** — minutes
- **P2 (catch-up / re-encode for new codec)** — hours, runs on spot instances overnight

The trick at YouTube scale: **video-level parallelism is too coarse**. A 1-h 4K HDR video at 1× real-time on one GPU machine = 1 hour wall-clock. We need ≤ 60 s end-to-end for a 5-min 1080p upload.

Solution: **GOP-level parallelism**. A 1-hour video has ~360 × 10-second GOPs (closed-GOP requirement so each can be encoded independently). The Transcoding Workflow:

1. **Demux** the raw upload into 360 × 10-s segments (using ffmpeg `-segment_time 10`).
2. **Distribute** segments to N workers (one per segment, ideally) via the Kafka `video.transcoded.todo` queue (or work-stealing pool).
3. Each worker encodes one segment at one rung in real-time → ~1 s wall-clock per 10-s segment per rung on an A100.
4. **Reassemble** the per-segment outputs into a single fragmented MP4 / `.ts` chain.
5. **Package** with HLS playlist + DASH manifest (via Shaka Packager).

Result: 1-h video × 7-rung ladder = 360 × 7 = 2520 segment-encodes; on a 30-worker pool that's ~84 segment-encodes per worker = ~84 s wall-clock. Total publish time ≈ ~2 min for a 1-h 4K HDR video. For a 5-min 1080p the math gives ~30 s.

##### Thumbnail Service

Extracts N candidate frames at fixed time-points (10/30/50/70/90% of duration). Passes each through a **CV "click-worthy-ness" classifier** (a fine-tuned ResNet that predicts CTR on the home page). Picks top-3. Creator can override with a custom upload via Studio. Stored in `gs://yt-thumbnails/{video_id}/{0,1,2}.jpg` with sizes (default, hq720, maxres).

##### ASR / Captions Service

Speech-to-text. Whisper-style multilingual model fine-tuned on YouTube-specific noise (background music, multi-speaker, low-volume). Per-uploaded-language audio track → `.vtt` subtitle file. Auto-translates to top-10 languages via a separate translation model. Writes to `gs://yt-captions/{video_id}/{lang}.vtt`. ~1 GPU-min per audio-hour.

##### ContentID Engine

The contractual gate for monetisation. Every uploaded asset:

1. **Audio fingerprint** — extract perceptual chroma features every 10 ms; produce a compact (~1 KB / minute) fingerprint vector.
2. **Video fingerprint** — extract pHash + DCT features per keyframe; produce a fingerprint vector.
3. **Match** against the **rights-holder reference DB** (~50 M reference fingerprints). Algorithm: locality-sensitive hashing → top-K candidates → exact alignment. ~30 GPU-seconds per matched video.
4. **Decide** based on matched rights:
   - **Block** — the entire video is taken down (rare; only for full-content matches with strict claimants).
   - **Mute audio** — for music-only matches with the "mute" rights option.
   - **Monetise** — ads run, revenue routes to claimant (default for major-label music). Most common case.
   - **Track** — analytics only, no ad insertion (claimant just wants to know).
5. Writes to `videos.contentid_state` and emits `moderation.events.contentid_match` for downstream notifications.

Counter-claim: creator can dispute via Studio; routes to Moderation Console for manual review by both parties. Policy: 30-day window before a contested claim is auto-released.

##### Safety / NSFW Scan

Multi-modal pipeline:

- **PhotoDNA hash match** — instant CSAM detection; auto-blocks + auto-reports to NCMEC.
- **NSFW image classifier** — over keyframes; outputs `nsfw_score ∈ [0,1]`; high → age-restrict; very high + CSAM-adjacent → block + human review.
- **Hate-speech detector** — over title + description + comments + ASR captions; routes to human review for borderline.
- **Misinformation classifier** (election / health / war) — high-impact topics get extra attention; demotion in recommender, info-panel attached on watch page.

Scores written to `videos.safety_score` and emitted to `moderation.events`.

##### View Aggregator (Flink / Beam)

Tumbling 1-min windows over `watch.events`. For each `(video_id, day)`:
- INCR Bigtable `view_counters` row (cumulative)
- Update per-video Redis HLL for unique-viewer count (`PFADD viewers:{video_id}:{day} <user_id>`)
- Emit `features.stream` deltas (CTR, watch-time, completion-rate) for the recommender online learner
- Watermark = `max(processing_time, event_time + 5 min late)` to absorb late beacons

Watermarking is critical because mobile clients can be ~minutes late (offline → reconnect → flush queued beacons). Without watermark slack, view counts perpetually under-count.

##### Trending Engine

Top-K via **Count-Min Sketch + Heap** (the textbook streaming Top-K algorithm). For each (region × category) we maintain a CM sketch that ingests `(video_id)` from `features.stream`; every 10 min we extract the top-100; this becomes the trending row. Refresh cadence is intentionally low (10 min) to keep "trending" stable from the user's perspective — nobody wants the trending list to flip every second.

##### Recommendation Pipeline (Spark + Flink + TF)

The brain. Two stages:

1. **Candidate generation** — for each user, produce a ~hundred-or-thousand candidate video pool. Three sources combined:
   - **Collaborative filtering** (matrix factorisation over user-video watch matrix; trained nightly on Spark).
   - **Content-based** (cosine similarity of user-history-embedding × video-embedding; embeddings trained via two-tower DNN).
   - **Subscription-based** (recent uploads from subscribed channels).

2. **Ranking** — DNN ranker (multi-task: predict CTR, watch-time, satisfaction); takes `(user_features, video_features, context_features)` → outputs a score per candidate; sorts top-N.

Training cadence:
- **Online learner** consumes `features.stream` → updates feature store (Bigtable + BigQuery) within seconds.
- **Nightly Spark batch** retrains the ranker on the last 90 d of data; A/B-tests new model against control before promotion.
- **Weekly deep retrain** rebuilds the embeddings from scratch.

Publish: feature store gets a fresh per-user feature vector every few minutes; model store gets a new ranker version daily (dark-launch first, A/B for 1 week, then promote).

##### Search Indexer

Consumes `video.indexed` (which is the join of `video.transcoded` + ASR-completed + ContentID-decided). Builds:
- Inverted index in Elasticsearch (BM25 over title + desc + ASR + tags + comments-summary).
- ANN vector index in Vespa (over title+desc embedding + thumbnail embedding).

Bulk indexing in batches of 1000; latency from publish to searchable: ~5 min p99. Rebuilds the entire index nightly to absorb deletions / privacy changes.

##### Notification Worker

Consumes `notify.events`; fans out to APNS / FCM / SES / WebSocket. Key invariants: idempotent on `event_id`, rate-cap per recipient (max 5 push/day from any single channel), respect "do-not-disturb" window per user (10 PM–7 AM local).

##### Outbox Publisher

The lynchpin of "exactly once into Kafka" semantics. For each shard of Spanner / Bigtable, a publisher tails the `*_outbox` rows (rows written in the same transaction as the user-visible state). Produces to Kafka with `at-least-once + idempotent dedupe` (per-message `idempotency_key` so the consumer can dedupe on replay).

Run **2 replicas per shard** (HA topology). Use `FOR UPDATE SKIP LOCKED` (Spanner) / atomic mutations (Bigtable) to ensure no double-publish across replicas.

##### Stuck-Job Sweeper

Every 5 min: scan for `videos` rows with `ingest_status='UPLOADING'` older than 30 min without a `video.transcoded` event → re-enqueue to Kafka. Safety net for lost messages, dead workers, etc. Idempotent on `(video_id)` so duplicate enqueues no-op.

#### 8.5.4 The Live Plane

##### Live Ingest Gateway

Encoders push **RTMP** (legacy, but OBS / ffmpeg / mobile widely supported) or **SRT** (preferred — re-transmits dropped UDP packets, much more reliable over public internet). Each stream has a stream key (long-lived, rotated per stream session) that the gateway validates. **Active/standby**: two ingest pods per stream, etcd lease (3 s TTL); on primary failure, standby promotes itself.

##### Live Transcoder

Same FFmpeg / NVENC binaries as VOD but configured for **chunked encoding** with `keyframe_interval = 1 s`, output **CMAF chunked fragments of 200 ms**. End-to-end latency from encoder input to packager output: ~500 ms.

##### Live Packager (LL-HLS / LL-DASH)

LL-HLS (Apple's spec, since 2020) splits a 6-second segment into ~30 partial segments of 200 ms each. The manifest is updated every 200 ms with new partial segments. The player asks for the *latest* partial via `EXT-X-PRELOAD-HINT` — the server holds the request open until the partial is ready (HTTP/2 server-push / chunked transfer encoding). End-to-end glass-to-glass: 500 ms encode + 500 ms package + 1 s CDN propagation + 1 s player buffer = **~3 s**, well within our 5 s SLO.

##### DVR Recorder

The live transcoder also writes to GCS in 6-second segments for the entire stream duration. Viewers who tune in late can scrub back up to 4 hours (the DVR window). After the stream ends, the same recordings are archived into a permanent VOD video (with proper ABR ladders), and the live event row becomes a permanent VOD entry in the catalogue.

#### 8.5.5 The Analytics & Recommendation Plane

##### Kafka backbone

All asynchronous data flow goes through Kafka. Per-region Kafka clusters with cross-region MirrorMaker for analytics aggregation. Topics keyed by `user_id` for per-user ordering, by `video_id` for per-video aggregation. Idempotent producers, RF=3, `acks=all`, transactional consumer + outbox pattern for exactly-once-into-store.

##### Flink (real-time stream processing)

- Consumes `watch.events` to compute per-(video × day) view counts (1-min tumbling windows, HLL for unique).
- Consumes `cdn.qoe` to compute per-PoP rebuffering ratios in 10-second windows; output drives multi-CDN routing.
- Consumes `engagement.events` to refresh per-video `like_count` / `comment_count` / `subscriber_count` denormalisations.

##### Spark (offline batch)

Nightly:
- Re-train recommendation embeddings (matrix factorisation + deep two-tower).
- Re-train DNN ranker (~24 GPU-hours).
- Compute creator royalty / payout per channel per day.
- Update `Top-100` per (region × category) for trending.
- Rebuild user feature vectors (genre affinity, language, time-of-day pattern, completion rate, sub-network-effect).
- Compute video-quality scores (ASR confidence, thumbnail click-worthy, audio loudness LUFS).

##### Druid / Pinot (real-time OLAP)

OLAP store for the QoE dashboards (rebuffer ratios, startup time, bitrate distribution per CDN per region per device per time-bucket). Also powers Creator Studio dashboards (per-video real-time view rate, geographic split, source-of-traffic).

##### BigQuery / Snowflake (data warehouse)

Long-term aggregates. Watch.events aggregated daily, retained 5 y. Used for: royalty calc, recommendation training, retention metrics, marketing analytics.

---

## 9. Component Deep-Dives

### 9.1 Watch / Manifest Service — the gatekeeper

We described the role above; here's the *implementation*.

**Stack:** Go (low GC pause, great gRPC). Stateless, horizontally scaled, ~500 pods at peak. Co-located with Redis (metadata cache + manifest signing key cache).

**Hot path** (`GET /watch?v={video_id}`):

```
1. Parse JWT  → user_id, country_home, plan, is_kids
2. Redis: GET video:{video_id}     -- ~95% hit
   if miss → Spanner SELECT FROM videos WHERE video_id=...
             SET video:{video_id} TTL 30s
3. Privacy check:
   if videos.privacy='PRIVATE' AND user_id NOT IN allowed_users → 404
   if videos.privacy='UNLISTED' AND request didn't come with direct link → 404
4. Geo check:
   country = country_from_jwt OR maxmind_lookup(client_ip)
   if country IN videos.country_block_list → 451
   if videos.country_allow_list NOT EMPTY AND country NOT IN allow_list → 451
5. Age check:
   if videos.age_restricted AND (user_id is NULL OR user.age_verified=false) → 451
6. DRM check (rare):
   if videos.drm_required AND plan != 'PREMIUM' → 402
7. Build manifest URL:
   path = "/v/{video_id}/master.m3u8"
   sig  = HMAC-SHA256(edge_signing_key, path + expiry + user_id)
   url  = "https://" + cdn_pop_for_region(country) + path + "?expires=...&sig=..."
8. Build DRM license URL (rare):
   if drm_required → "https://drm.yt.example/widevine?session={sid}&video={vid}"
9. Build Up Next list:
   call Recommendation Svc /up_next?user_id=...&from_video=video_id  (~5ms)
10. Emit Kafka watch.events.PLAY_REQUESTED (key=user_id)
11. Return { manifest_url, drm_license_url, captions_urls, up_next_video_ids[10] }
```

**Failure modes:**

- Redis unavailable → Spanner direct (slow, ~30 ms); rate-limit aggressively to protect Spanner.
- Spanner replica lag → use cached `video` row (30 s TTL); accept up to 30 s stale privacy enforcement.
- Kafka produce failure → still return 200 to the user; emit to a small **local-disk dead-letter queue** that a sidecar drains; analytics is eventual.

**Why a single service?** Because the privacy / geo / age / DRM checks must be **atomic with manifest issuance**. Splitting them introduces window conditions where a manifest is issued for a now-private video.

### 9.2 Transcoding Pool — the GOP-distributed encoder

Already described in §8.5.3, but reiterate the math:

**Per-1080p-hour cost on an A100:**
- H.264 encode at ~12× real-time → ~5 min wall-clock per 1 h source per rung.
- VP9 encode at ~3× real-time → ~20 min per 1 h source per rung.
- AV1 encode at ~0.3× real-time → ~3 hr per 1 h source per rung. (Slowest; we encode AV1 only for the head of the long-tail.)

**Strategy:**
- All videos: H.264 + VP9 ladder (for universal compatibility + Android).
- Top 1% of videos (by predicted views): also AV1 ladder (saves ~30% bandwidth on supporting devices; cost-justified by the egress savings).
- 4K HDR titles: HEVC ladder for Apple TV.

**Aggregate fleet sizing (~30 K hrs / hr ingest):**
- 30 K hrs / hr × 7 rungs = 210 K rendition-hours / hr
- At 12× real-time per rendition-hour: 210 K / 12 = ~17.5 K GPU-hours-per-hour = **17.5 K GPUs busy 24/7 just for H.264**
- VP9 + AV1 + HEVC double or triple this → **~30–50 K GPU fleet**

This is the single biggest infra cost in the system. Mitigations:
- Spot instances for P2 (catch-up / re-encode); 70% cost saving.
- Lazy ladder generation — encode only the rungs the audience is watching. New unknown videos → encode 360p + 720p + 1080p initially; if views > threshold within 24 h, kick off the higher rungs.
- Co-locate with cheap-power regions (Iowa, Oregon, Frankfurt).

### 9.3 View Counter — full deep dive in §14

Goes here as a forward reference. The famous "1 B view counter" interview question gets its own section because the design choices are subtle and have cascading effects.

### 9.4 Recommendation Service — full deep dive in §13

Forward reference; recommendation gets its own dedicated section.

### 9.5 Engagement Service — comments, likes, subscribes

Pattern: every write goes through the **outbox pattern** to ensure exactly-once-into-Kafka.

```
POST /comment { video_id, parent_comment_id, text }
  ↓
Engagement Svc:
  1. Validate text (length, regex, HTML strip)
  2. Run toxicity classifier (~10ms p95) → if score>0.9 hold for review
  3. Bigtable atomic mutation:
       INSERT into comments (video_id, comment_id, user_id, text, created_at, ...)
       INSERT into comments_outbox (event_id, video_id, ...)
  4. Return 201 { comment_id }  (no Kafka in the request path)
  ↓ (asynchronously, via Outbox Publisher)
  5. Outbox Publisher tails comments_outbox, produces engagement.events.COMMENT
  6. Notification Worker derives notify.events for parent commenter + creator
```

Reads (`GET /comments?video_id=X&page=Y`):
- Bigtable scan on `(video_id, ts_desc)` → top-K with cursor-based pagination
- Per-comment: read denormalised `like_count`, `reply_count` from Bigtable cells (refreshed every 5 s by Flink rollup over `engagement.events.LIKE`)
- Sort by "Top" (= like_count + reply_count×2 + creator_hearted_bonus) or "Newest"

### 9.6 Notification Service — fan-out at Mr Beast scale

Big channel uploads notify millions of subscribers. Naive per-subscriber-loop would take hours; we shard:

```
upload event for channel C with N subscribers:
  ↓
Notification Service splits N subscribers into K shards
  ↓
For each shard, produce notify.events (key=recipient_user_id)
  ↓
Notification Worker (one per Kafka partition, ~128 workers) consumes
  ↓
Per-recipient: emit FCM/APNS push (10 ms each) + email (50 ms)
  ↓
Fan-out completes in ~30 min for N=300M (300M / 128 partitions / 10 events/sec/worker = ~30 min)
```

Idempotency table in Bigtable: `(event_id, channel, user_id) → sent_at` so reprocessing on retry doesn't double-fire.

Backpressure: APNS / FCM have rate limits (~100K/sec aggregate per app). Token bucket in Redis throttles per-target.

### 9.7 Live Transcoder + Packager

Already covered in §8.5.4. The ops nuance: **live ingest must never lose a single second**. We log every received RTMP packet's sequence number; if standby fails over, it picks up at the last seen sequence, requests a re-send from the encoder for any gaps. From the viewer POV, the segment cycle just continues.

### 9.8 ContentID Engine — the contractual gate

The numbers:
- Reference DB: ~50 M reference fingerprints (audio + video).
- Each upload: ~30 GPU-seconds for fingerprint + LSH + alignment.
- 30 K hours / hour upload × 30 GPU-sec / video × ~4 videos / hour-of-content (assuming 15-min avg) = ~7 K GPU-hours per ingest hour = **~7 K GPUs busy 24/7** dedicated to ContentID.

Performance optimisations:
- LSH-banding to prune candidate matches to ~100 per upload.
- Audio-only fingerprint first (cheap); video-fingerprint only on audio-match-positive.
- Cache fingerprints in Redis for re-uploads (same SHA-256 → reuse).

### 9.9 Outbox Publisher — exactly-once-into-Kafka

Standard pattern; see [`08-CommonProblems/06-IdempotencyAndDeduplication.md`](../08-CommonProblems/06-IdempotencyAndDeduplication.md). Per-shard publisher tails outbox table:

```sql
SELECT event_id, payload FROM comments_outbox 
WHERE published_at IS NULL 
ORDER BY created_at LIMIT 1000 
FOR UPDATE SKIP LOCKED;
```

Produce to Kafka with `idempotency_key = event_id`; on success, UPDATE `published_at = now()`. Two replicas per shard for HA; SKIP LOCKED ensures no double-publish.

If Kafka is down, outbox accumulates; users still see their comments / likes / subs. When Kafka recovers, drains within minutes. The pattern converts **"Kafka outage = data loss"** into **"Kafka outage = downstream-features-delay"**. Worth it.

---

## 10. Video Upload & Transcoding Pipeline

The transcoding pipeline is its **own system** with its **own scaling axis** — independent of watch. It is the most CPU-intensive part of the entire YouTube.

### 10.1 The DAG, top to bottom

```
              ┌─────────────────────────────┐
              │  Creator Studio uploader     │
              │  (resumable chunked PUT to   │
              │   GCS via signed URL)        │
              └────────────┬─────────────────┘
                           │ GCS PUT complete
                           │ Pub/Sub event
                           ▼
              ┌─────────────────────────────┐
              │  Upload Service              │
              │  (verify SHA, INSERT video  │
              │   ingest_status='UPLOADING') │
              └────────────┬─────────────────┘
                           │ produce video.uploaded
                           ▼
              ┌─────────────────────────────┐
              │  Workflow Orchestrator       │
              │  (Argo / Cloud Workflows /   │
              │   Temporal)                  │
              └─────┬──────┬────────┬────────┘
        demux+split│      │        │
                   ▼      ▼        ▼
       ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
       │ Transcoding      │  │ ASR / Captions   │  │ Thumbnail Pick   │
       │ Workers          │  │ (per-language)   │  │ (CV CTR model)   │
       │ (per-segment     │  │                  │  │                  │
       │  parallel encode)│  │                  │  │                  │
       └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
                │                      │                      │
                │  ┌──────────────────┐  │                      │
                │  │ ContentID Engine │ ◄┘                      │
                │  │ (audio + video   │                          │
                │  │  fingerprint)    │                          │
                │  └────────┬─────────┘                          │
                │           │                                    │
                │  ┌──────────────────┐                          │
                │  │ Safety / NSFW    │ ◄────────────────────────┘
                │  │ (CV + audio)     │
                │  └────────┬─────────┘
                │           │
                ▼           ▼
              ┌─────────────────────────────┐
              │  Packager (Shaka Packager)   │
              │  CMAF source →               │
              │     HLS .m3u8 + DASH .mpd    │
              │  CENC encryption (only if    │
              │  Premium / Movies / DRM req) │
              └────────────┬─────────────────┘
                           │
                           ▼
              ┌─────────────────────────────┐
              │  QC: VMAF + manual sample    │
              │  for Premium uploads only    │
              │  (UGC: skip QC)              │
              └────────────┬─────────────────┘
                           │
                           ▼
              ┌─────────────────────────────┐
              │  Publish:                    │
              │  - GCS finalise to public    │
              │    bucket                    │
              │  - Spanner UPDATE videos     │
              │    ingest_status='PUBLISHED' │
              │  - Kafka video.transcoded    │
              │  - CDN pre-warm (top-1 % only)│
              └─────────────────────────────┘
```

### 10.2 Ladder design

A typical 7-rung ABR ladder for a 1080p-source video (most common):

| Rung   | Resolution | Codec      | Bitrate     | Audience                    |
|--------|------------|-----------|-------------|-----------------------------|
| 144p   | 256×144    | H.264 base | 100 kbps    | very low bandwidth, data-saver |
| 240p   | 426×240    | H.264 base | 300 kbps    | 2G                          |
| 360p   | 640×360    | H.264 main | 800 kbps    | 3G                          |
| 480p   | 854×480    | H.264 main | 1.5 Mbps    | LTE / good Wi-Fi mid-tier   |
| 720p   | 1280×720   | H.264 high + VP9 | 3 Mbps  | Default for most users      |
| 1080p  | 1920×1080  | H.264 high + VP9 + AV1 | 5 Mbps | Fibre / quality tier   |
| 1080p60 | 1920×1080@60fps | H.264 high + VP9 + AV1 | 7.5 Mbps | Gaming, sports |

For 4K HDR uploads, add:

| Rung   | Resolution | Codec       | Bitrate    | Notes |
|--------|------------|-------------|------------|-------|
| 1440p  | 2560×1440  | H.264 + VP9 | 9 Mbps    | "QHD" — premium creator content |
| 2160p (4K SDR) | 3840×2160 | VP9 + HEVC + AV1 | 18 Mbps | 4K TVs / Apple TV |
| 2160p (4K HDR) | 3840×2160 | VP9 + HEVC + AV1 with HDR10/Dolby Vision | 25 Mbps | HDR-capable TVs |

Codec strategy:
- **H.264** for everything (universal; even old smart TVs play it).
- **VP9** for 720p+ (Android + Chrome native; no licensing fee; ~30% better than H.264).
- **AV1** for top-1 % videos (saves ~30% over VP9 but expensive to encode at ~0.3× real-time).
- **HEVC** for Apple TV at 4K (Apple's preference; HEVC is patent-encumbered so we keep its use minimal).

### 10.3 Per-video encode time

A 1-h 1080p video, with GOP-level parallelism over 30 workers:

- Demux into 360 × 10-s GOPs: 30 s.
- 360 × 7 rungs = 2520 segment-encodes; on 30 workers at ~1 s / segment / rung → ~84 s total wall-clock.
- Reassemble + package: 20 s.
- ContentID + Safety + ASR (parallel): ~60 s.
- **Total wall-clock: ~3 min for a 1-h 1080p video.**

For a 5-min 1080p (most common):
- Demux: 5 s
- 30 × 7 = 210 segment-encodes on 30 workers → ~7 s
- Reassemble + package: 5 s
- ContentID + Safety + ASR (parallel): ~30 s
- **Total wall-clock: ~50 s for a 5-min 1080p video.** ← p99 ≤ 60 s SLO satisfied.

### 10.4 Why CMAF is non-negotiable

Pre-CMAF, you had to encode and store HLS segments (.ts files) and DASH segments (.m4s files) separately — 2× the storage. CMAF (Common Media Application Format) defines a single fragmented MP4 (.cmfv) that **both** HLS and DASH can reference.

For us, CMAF means:
- Encode once per rung.
- Encrypt once (CENC, when DRM required).
- Package twice (write a `.m3u8` for HLS, a `.mpd` for DASH, both pointing to the same `.cmfv` segments).

Cuts our packaged storage from ~10 EB to ~5 EB. CDN cache footprint halved. Win.

### 10.5 Lazy ladder generation (the long-tail trick)

90 % of uploaded videos get < 100 views in their lifetime. Encoding 7 rungs for all of them is wasteful.

Strategy:
1. **Initial encode:** 360p + 720p + 1080p (the 3 most-watched rungs). Fast — completes in our SLO.
2. **Lazy backfill:** if `views_24h > 1000`, kick off 144p + 240p + 480p + 1440p (low-priority queue). Done within ~6 hours.
3. **Premium backfill:** if `views_total > 100000`, kick off AV1 + HEVC ladder (top 1%). Done overnight on spot instances.

Result: ~70% saving on encoding GPU-hours for the long tail; the head (which serves the bulk of traffic) gets the full ladder.

### 10.6 Subtitle + audio tracks

- **Subtitles (captions):** Creator can upload SRT / SubRip / WebVTT per language. We also auto-generate via ASR (Whisper-style multilingual) for any language we detect; auto-translate to top-10 languages. Stored as `.vtt` for HLS, `.ttml` for DASH. Closed-caption formats are mandated by FCC (US accessibility).
- **Audio tracks:** Creator-uploaded multi-language audio (rare — only for high-budget content). Each language encoded separately (AAC-LC at 128 kbps; HE-AAC at 64 kbps for low bandwidth). HLS/DASH manifest declares the language tag; player picks based on user pref.

### 10.7 Pre-warming the CDN (only for the head)

Unlike Netflix (where every title is high-value and pre-warmed at release), at YouTube scale we can only pre-warm the very top:

```
On video.transcoded, if predicted_views_24h > 100K:
   → CDN Pre-warmer pushes first 30s of segments to all PoPs in the creator's primary region
   → For top-100 videos (e.g., a Mr Beast premiere), push to all PoPs globally
   → Cold-cache miss penalty avoided for ~80% of expected viewers
```

For the long tail, we don't pre-warm; the first viewer pays the ~150 ms shield-pull latency (acceptable).

### 10.8 QC (Quality Control) — only for premium content

UGC at our scale: skip QC entirely. The creator owns the quality of their own upload. We only ASR-check audio loudness (LUFS normalise) and run perceptual-hash on thumbnails (block obvious clickbait).

For YouTube Premium / Movies / Originals: standard VMAF QC pass against the source (VMAF >= 90 on every rung); manual sample frame review by editorial.

---

## 11. End-to-End Flows

The flows below match the **numbered flows on the HLD diagram** (panel "8 Numbered Flows"). Each step references the diagram arrow id (e.g., `T3`, `W1`).

### 11.1 Flow U — Upload (creator → published)

```
  [Creator clicks Upload in Studio]
         │  1. POST /upload/init { filename, size, sha256 }     --- U1
         ▼
  [API GW] → [Upload Svc]
         │  2. Returns signed GCS resumable URL + chunk_size 8MB --- U2
         ▼
  [Creator client] PUT chunks directly to GCS                    --- U3
         │  3. Each chunk: Content-Range: bytes A-B/Total
         │     GCS reports progress via Pub/Sub → updates Redis
         │     (last_completed_offset)
         ▼
  [On final chunk] POST /upload/complete                         --- U4
         │  4. Upload Svc verifies assembled SHA, INSERTs videos
         │     row (ingest_status='UPLOADING'), produces video.uploaded
         │     to Kafka
         ▼
  Returns: { video_id, status: 'PROCESSING' }
         │  ~5 s typical (upload time dominates)
         ▼
  ================================================================
  [Asynchronous transcoding pipeline (see Flow T)]
```

**Latency**: dominated by upload bandwidth. A 5-min 1080p upload at typical 10 Mbps consumer upload = ~2 min upload + ~50 s transcoding = ~3 min from "click upload" to "viewers can watch".

### 11.2 Flow T — Transcoding (video.uploaded → PUBLISHED)

```
  [Kafka video.uploaded]                                          --- T0
         │
         ▼
  [Workflow Orchestrator] forks DAG:
         │
   ┌─────┼──────┬─────────┬────────────┐
   ▼     ▼      ▼         ▼            ▼
  T1    T2     T3        T4           T5
  Demux  Encode H.264 Encode VP9   ContentID    ASR / Captions
  +split (per-rung,  (per-rung,    Engine        (per-language)
  10s    GOP-distribu GOP-distrib  (audio +      (Whisper-style)
  GOPs)  ted across N) ted)         video FP)
        workers       workers       LSH match
         │              │            │            │
         ▼              ▼            ▼            ▼
        Per-segment .ts/.m4s    moderation.events  .vtt files
        files in GCS staging    (block / monetise) in GCS
         │              │            │            │
         └──────┬───────┘            │            │
                ▼                     │            │
   ┌─────────────────────────┐        │            │
   │ Packager (Shaka)         │       │            │
   │ - Merge segments         │       │            │
   │ - Build .m3u8 + .mpd     │       │            │
   │ - CENC encrypt (if DRM)  │       │            │
   │ - Move to public bucket  │       │            │
   └────────────┬────────────┘        │            │
                │                      │            │
                ▼                      │            │
  [Spanner UPDATE videos                            │
   ingest_status='PUBLISHED'                        │
   contentid_state=:contentid_outcome    ◄──────────┤
   safety_score=:safety_outcome           ◄─────────┤
   captions_languages=:vtt_langs         ◄──────────┘
   monetisation_state=:from_contentid]
         │
         ▼
  [Kafka video.transcoded]                                  --- T6
         │
   ┌─────┼──────────┬──────────────────┐
   ▼     ▼          ▼                  ▼
  Watch  Search     Recommendation     Notification
  Svc    Indexer    Pipeline           (notify subscribers)
  reads  builds     trains features    via notify.events
  cache  inverted   for new video
         + ANN
         indexes
```

**Latency**: ~50 s p99 for 5-min 1080p; ~3 min for 1-h 1080p; ~10 min for 1-h 4K HDR.

### 11.3 Flow W — Watch (user → first frame)

```
  [User taps a video thumbnail in feed]
         │  1. GET /watch?v=<video_id>                          --- W4
         ▼
  [API GW] → [Watch / Manifest Svc]
         │  2. JWT validate, geo + privacy + age + DRM checks
         │  3. Read video metadata (Redis hit ~95%)             --- W5,W6
         │  4. (Rare) Issue DRM license URL                     --- W7
         │  5. Sign manifest URL (HMAC, 6-h TTL)
         │  6. Pick CDN PoP (ASN-routed)
         │  7. Emit Kafka watch.events.PLAY_REQUESTED
         ▼
  Returns: { manifest_url, drm_license_url, captions_urls, up_next }
         │  ~80 ms p99
         ▼
  [Player] fetches manifest_url → CDN edge                     --- W1
         │  8. CDN cache hit ~95% → ~80 ms
         ▼
  [Player] parses .m3u8, picks initial bitrate (ABR)
         │  9. Conservative start: 720p
         ▼
  [Player] fetches init.mp4 + first media segment
         │  10. ~200 ms each segment
         ▼
  [CDN] serves segments from cache                              --- W1
         │  11. on PoP miss: cdn_pop → origin_shield (W2) →
         │      origin (W3); ~150 ms penalty
         ▼
  [Player] decodes + renders first frame                        --- "first frame on screen"
         │  12. happens at p99 ≤ 1 s after the tap (cache hit)
         ▼
  [Player] every ~10 s heartbeats /beacon                       --- V1
         │  13. cumulative play_time → if ≥30s and not yet
         │      counted today, emit watch.events.VIEW
         ▼
  [Player] continues until pause / stop / navigate-away
```

### 11.4 Flow R — Recommendation (home page render)

```
  [User opens app / web]
         │  1. GET /home                                        --- R1
         ▼
  [API GW] → [Recommendation Svc]
         │  2. Read pre-computed rows from Redis home:{user_id} --- R2
         │     (HSET of row_id → [video_ids])
         │     ~95% Redis hit, < 1 ms
         │  3. Read user features (last 5 plays, time-of-day)
         │     from feature store                               --- R3
         │  4. Online re-rank top of each row using session
         │     context (small DNN, TF-Serving, ~5 ms p95)        --- R4
         ▼
  Returns: { rows: [{ row_id, video_ids: [...] }, ...] }       
         │  ~30 ms p99
         ▼
  [Player] renders home page; per-video metadata fetched in
           parallel (or batched via /videos/batch?ids=...)
         │
         ▼
  [Catalog Svc] (or Watch Svc batch endpoint) returns metadata
         │  thumbnails / titles / channel / view_count / duration
         ▼
  [CDN] serves thumbnails from PoP cache                        --- W1 (cdn for images)

  ===========================================================
  Offline / streaming pipeline (background):
  
  [Spark nightly batch]
   - ingests watch.events + engagement.events from BigQuery
   - retrains user feature vectors
   - retrains DNN ranker
   - publishes to feature_store + model_store               --- R5,R6
  
  [Flink online streaming]
   - tails features.stream from Kafka                       --- (R via kf_features → reco_pipe)
   - updates per-user features in Bigtable within seconds
```

### 11.5 Flow E — Engagement (comment / like / subscribe)

```
  [User types a comment, clicks Send]
         │  1. POST /comment { video_id, parent_comment_id, text } --- E1
         ▼
  [API GW] → [Engagement Svc]
         │  2. Validate + toxicity classifier (~10 ms)
         │  3. Bigtable atomic mutation:
         │       INSERT into comments
         │       INSERT into comments_outbox
         │     (single-row atomic)                                --- E2
         │  4. Return 201 { comment_id } in ~30 ms
         ▼
  [User sees their comment immediately in UI]
         │
  ===========================================================
  Asynchronously:
         │
         ▼
  [Outbox Publisher] tails comments_outbox
         │  5. produces engagement.events.COMMENT (key=user_id) --- E3
         ▼
  [Kafka engagement.events]
         │
   ┌─────┼─────────────────────────────────────────┐
   ▼     ▼                                          ▼
  Notification     Recommendation                  Audit
  Worker           feature pipeline                Sink
  (notifies        (CTR / engagement signals)
  parent
  commenter,
  creator)
         │
         ▼
  [Kafka notify.events] (key=recipient_user_id)              --- E4
         │
         ▼
  [Notification Worker]
         │  6. fan-out: APNS / FCM / SES / WebSocket frame    --- N1,N2,N3
         ▼
  [Recipient device] sees push notification within ~5 s
```

### 11.6 Flow V — View Count (the famous one)

```
  [Player has been playing for 30+ s]
         │  1. POST /beacon { user_id, video_id, position_ms,  --- V1
         │                    cumulative_play_ms, ... }
         ▼
  [API GW] → [View Beacon Collector]
         │  2. Dedup: SADD seen_views:{day}:{user_id} {video_id}
         │     if not added (already counted today) → no-op,
         │     return 204 immediately
         │  3. produce watch.events.VIEW (key=user_id)         --- V2
         │  4. Return 204
         ▼
  ===========================================================
  Asynchronously:
         │
         ▼
  [Kafka watch.events]                                          --- V3
         │
         ▼
  [View Aggregator (Flink)]
         │  5. tumbling 1-min windows per (video_id, day)
         │  6. for each window:
         │     - INCR Bigtable view_counters[video_id][day]    --- V4
         │     - INCR Redis view_counters_hot:{video_id}
         │     - PFADD viewers:{video_id}:{day} {user_id} (HLL)
         │     - emit features.stream (CTR, watch-time delta)  --- V5
         │     - feed Trending Engine (Count-Min Sketch)        --- V6
         ▼
  [Bigtable] has the canonical count
  [Redis] has a hot read for the watch page
         │  7. Watch page render: GET video metadata → returns
         │     view_count from Redis (TTL 30 s); fallback Bigtable
         ▼
  [User sees view count on watch page]
         │  total freshness: beacon → visible ≤ 60 s p99
```

### 11.7 Flow L — Live Streaming (creator → viewer)

```
  [Creator clicks "Go Live" in Studio]
         │  1. POST /live/start → returns
         │     { stream_id, ingest_url: rtmp://..., stream_key }
         ▼
  [Creator's encoder (OBS / mobile)]
         │  2. RTMP / SRT push                                  --- L1
         ▼
  [Live Ingest Gateway] (regional anycast)
         │  3. Validates stream_key, forwards to active Live
         │     Transcoder pod (active/standby via etcd lease)
         │  4. forward to Live Transcoder                       --- L2
         ▼
  [Live Transcoder] (NVENC GPU)
         │  5. Per-rung CMAF chunked encoding (200 ms partials)
         │  6. Write 1-s LL-HLS / LL-DASH segments to GCS       --- L3
         │  7. Produce live.segments to Kafka                   --- L4
         ▼
  [Live Packager] (often co-located with Transcoder)
         │  8. Update .m3u8 manifest every 200 ms
         │     (EXT-X-PRELOAD-HINT for next partial)
         ▼
  ===========================================================
  Viewer side (parallel):
         │
  [Viewer taps Live event]
         │  9. GET /watch?v=<stream_id>                         --- W4
         ▼
  [Watch Svc] returns LL-HLS manifest URL (live mode)
         │  10. manifest TTL ~1 s (refreshed every 200 ms)
         ▼
  [Player] fetches LL-HLS manifest → CDN
         │  11. CDN cache TTL = 1 s for live manifests
         │      partial segments cached ~5 s
         ▼
  [CDN] serves manifests + segments from cache
         │  on miss → origin shield → live origin GCS
         ▼
  [Player] decodes + renders
         │  glass-to-glass: ~3-5 s
         │
         ▼
  Heartbeats + chat (via WS Push GW) work like VOD
  
  [DVR Recorder] writes to GCS for the entire stream duration;
  viewer can scrub back up to 4 hours
  [On stream end] DVR recordings → permanent VOD video
```

### 11.8 Flow S — Search (typeahead → results)

```
  [User types in search box]
         │  1. As they type: GET /search/suggest?q=<partial>
         │     (debounced ~150 ms client-side)
         ▼
  [Search Svc /suggest endpoint]
         │  2. In-memory Trie + Top-K → returns suggestions
         │     ~p99 50 ms
         ▼
  [User hits Enter]
         │  3. GET /search?q=<query>                            --- S3
         ▼
  [Search Svc] 
         │  4. Parse query, language-detect, expand synonyms
         │  5. Query Elasticsearch (BM25 over title/desc/ASR)   --- S4
         │     + Query Vespa (ANN over title-desc embedding)
         │  6. Score-fusion → top-50
         │  7. Personalise re-rank with user features (small
         │     DNN, ~5 ms)
         │  8. Hydrate metadata (Redis hit) for top-20
         │  9. Return { results: [...], page_token }
         ▼
  Returns ~30-50 ms p99
         ▼
  [User sees search results page; thumbnails from CDN]
```

---

## 12. CDN, Multi-CDN & ISP-Cache Strategy

### 12.1 Why multi-CDN + ISP-cache

A single CDN dependency is a single point of failure in:
- **PoP outages** — a single CDN's PoP goes down, ~5–30% of regional traffic hangs.
- **Capacity ceilings** — every CDN has contractual Tbps per region; a marquee live event saturates *every* major CDN.
- **Regional pricing arbitrage** — CDN A is cheaper in India, CDN B is cheaper in EU; routing by cost saves real money.
- **Geopolitical** — some CDNs are blocked in some countries; redundancy = reach.
- **The unique YouTube knob: ISP-cache** — Netflix's Open Connect appliances live inside ISP data-centres. We do the same. Per-appliance cost (~$50K) is a fraction of the bandwidth saving from not paying transit on the same content for every user in that ISP. Globally ~15K appliances serve ~50% of all YouTube bytes.

### 12.2 Cache hierarchy

```
[Player] ──► [ISP-Cache appliance] ──► [CDN Edge PoP] ──► [CDN Regional Layer] ──► [Origin Shield] ──► [GCS]
              (~5 ms, in ISP DC)         (~10 ms)            (~30 ms)                  (~50 ms)            (~200 ms)
```

- **ISP-cache** (where applicable, ~50% of users in major ISPs): the fastest hit. Each appliance has 256 TB SSD, holds ~10% of catalogue (the head + per-region popular).
- **CDN Edge PoP**: ~250 GB working set per PoP, ~95% hit on hot content, ~70% on long tail.
- **CDN Regional layer**: the CDN's own regional cache. Catches PoP misses.
- **Origin Shield**: *our* regional cache (one per region) that all CDNs hit. Without this, 5 CDNs each hitting GCS = 5× origin egress; with it, 5 → 1.
- **Origin (GCS)**: only hit when shield misses; rare for popular content.

Cache hit ratios in production:
- ISP-cache (where applicable): 80–90% for hot, 50% for long-tail.
- PoP edge: 90–95% for hot, 70% for long-tail.
- Regional: 98% aggregate.
- Origin shield: 99%+ aggregate.

### 12.3 Cache key design

Keys are URL paths. Segments are immutable — a given segment URL serves identical bytes forever — so we use:

```
Cache-Control: public, max-age=31536000, immutable
```

The signed query parameters (`?expires=…&sig=…`) are *not* part of the cache key (CDN config strips them before computing the cache key).

For manifests:
- VOD master playlist: `Cache-Control: max-age=300` (5 min; rarely changes once published).
- VOD media playlist (per rung): `max-age=86400` (1 day).
- Live manifest: `max-age=1` (refreshed every second; LL-HLS ~200 ms TTL via chunked transfer).
- Thumbnails: `max-age=86400` (immutable since thumbnail URLs include a content-hash suffix).

### 12.4 ISP-cache placement strategy (the Open Connect model)

Two flavours:
- **Embedded** (inside the ISP's data centre / IX): the ISP gives us rack-space + power + 100 Gbps uplink, gets ~50% reduction in egress costs.
- **Peered** (at IX): we colocate at internet exchanges; ISP picks up bytes via private peering instead of transit.

Replenishment: every appliance is a passive cache that pulls on miss from origin shield. *But* we **proactively push** the per-region "what will be popular tomorrow" working set during off-peak hours (Spark batch over `watch.events` predicts).

Distribution: ~15 K appliances globally; the largest ISPs (Reliance Jio in India, Comcast in US) have ~1000 appliances each.

### 12.5 Multi-CDN routing decisions

Computed every 30 s by Flink consuming `cdn.qoe`, output to etcd as JSON config:

```json
{
  "region:IN:device:android:asn:Reliance_Jio": {
    "cdn_weights": {
      "isp_cache_jio": 0.6, "akamai": 0.2, "cloudflare": 0.15, "cloudfront": 0.05
    },
    "cap_pct": { "akamai": 0.85 }
  },
  "region:US:device:smart_tv:asn:Comcast": {
    "cdn_weights": {
      "isp_cache_comcast": 0.5, "google_gfe": 0.3, "cloudfront": 0.2
    }
  }
}
```

Watch Svc reads this on every play, picks the CDN by weighted random, signs the manifest URL accordingly. **Capacity caps** ("akamai at 85%") trigger spillover to the next CDN.

### 12.6 CMCD (Common Media Client Data) — pre-fetch hints

Common Media Client Data (CMCD) is a 2020 spec where the player annotates segment requests with metadata:

```
GET /v/abc123/720p/seg42.ts?CMCD=br%3D5000%2Cbl%3D30000%2Cnor%3D%22..%2Fseg43.ts%22
```

Decoded: `bitrate=5000, buffer_length=30000ms, next_object_request="../seg43.ts"`

The CDN reads `nor` (next-object-request) and pre-fetches segment 43 from origin shield while serving segment 42 to the player. Net effect: cold-cache segments 43+ are pre-loaded by the time the player asks. Eliminates the "first scrub-forward" stall.

### 12.7 Live-specific CDN strategy (chunked transfer + short TTLs)

For live, the cache strategy flips:
- **Manifests** are dynamic — TTL 1 s (LL-HLS at 200 ms via HTTP/2 server-push).
- **Partial segments** cached only ~5 s (no point keeping after they're "old").
- **Origin shield** is mandatory — without it, 5 CDNs × 200 PoPs each pulling every 200ms = 200K req/s on origin per stream. With shield: 5 req/s.
- **DVR segments** (full 6-s segments for scrub-back) cached normally with `max-age=14400` (4-h DVR window).

---

## 13. Recommendation, Search, Trending, Personalisation

YouTube's recommender is the ultimate "watch-time maximisation" engine. The cited stats: ~70% of all watch-time on YouTube is driven by recommendations. The system has three legs.

### 13.1 The two-tower DNN ranker

The classic YouTube paper (Covington, Adams, Sargin 2016) describes the architecture; modern variants are basically refinements.

**Stage 1 — Candidate generation (~hundreds of millions → ~thousands).**

Three candidate sources combined:

1. **Collaborative filtering**: matrix factorisation over a sparse `user × video` watch matrix. Trained nightly on Spark. Output: a 256-d user embedding + a 256-d video embedding per user/video. Approximate nearest-neighbour over video embeddings → top-1000 candidates per user.

2. **Content-based**: per-video features (title embedding via BERT, description embedding, thumbnail embedding via ResNet, audio fingerprint, ASR keywords, channel embedding). Per-user features (history embedding = aggregated watched-video embeddings, demographics, time-of-day, geo). Cosine similarity → top-1000.

3. **Subscription-based**: recent uploads from subscribed channels (very high-recall — these are videos the user explicitly opted into).

Pool the three; deduplicate; pass top-3000 candidates to the ranker.

**Stage 2 — Ranking (~3000 → top-50 ranked).**

A DNN with ~10 hidden layers takes:
- User features: ~100 dims (history embedding, demographics, geo, time-of-day, device, recently-watched topics)
- Video features: ~200 dims (video embedding, channel features, title features, view count, age of video, region popularity, predicted CTR, predicted watch-time, monetisation state)
- Context features: ~50 dims (current row position, surface = home/search/up_next, query if applicable, session length so far)

Output heads (multi-task):
- `p(click)` — the probability the user clicks this thumbnail
- `p(watch ≥ 30 s)` — probability of a counted view
- `p(watch ≥ 50% duration)` — meaningful engagement
- `p(like)`, `p(dislike)`, `p(comment)`, `p(share)`
- `expected_watch_time_seconds`

Final score: a weighted combination of the heads, tuned via A/B (currently the dominant signal is `expected_watch_time × p(satisfaction)` where satisfaction is a meta-classifier over completion + likes + skip rate).

### 13.2 Diversity, freshness, exploration

A pure ranker would over-recommend the global maximum (e.g., one channel monopolises the home page). Real systems add:

- **Diversity** — Maximal Marginal Relevance: down-weight the score of a candidate if it's similar to candidates already chosen above it. Keeps the home page heterogeneous.
- **Freshness boost** — videos < 24 h old get a multiplicative score boost; decays exponentially.
- **Exploration** — ε-greedy: 5% of slots filled with a "random-ish" candidate (high uncertainty, low confidence prediction); helps the model learn unsigned signal in long-tail content.
- **Filter bubble breaking** — explicit logic to inject "outside your usual" content periodically (not heavily; users complained when it was too aggressive).

### 13.3 Online streaming features

The offline pipeline retrains nightly. But within a session, we need to react to "the user just watched Video X" within seconds. **Online streaming features**:

```
watch.events.PLAY_COMPLETE → Flink
   → update Bigtable feature_store: user:{user_id}:recent_videos = [v1, v2, ...] (last 50)
   → update user:{user_id}:topic_affinity (rolling window, exponentially decayed)
   → update user:{user_id}:watch_session_topic (current session theme)
   → publish features.stream
```

The Recommendation Service reads the latest `user:{user_id}:recent_videos` on every `/home` call and uses it to **re-rank** the top of pre-computed rows. Net effect: within ~5 seconds of finishing Video X, the user's home page reflects "you just watched X-style content".

### 13.4 Cold start

New user with no history:

1. Fall back to **per-country popularity row** + **trending row** + **per-language top-channels**.
2. After ~3 watched videos, the user-history-embedding starts converging; collaborative filtering kicks in after ~10 plays.
3. Onboarding survey ("pick 3 favourite topics") accelerates this.

### 13.5 Trending Engine — Top-K with Count-Min Sketch

For each (region × category) we maintain a Count-Min Sketch + Heap:

```
On watch.events.VIEW:
   for each window [10-min sliding]:
     CMS.add(video_id, 1)
   for each window [1-h sliding]:
     CMS.add(video_id, 1)

Every 10 min:
  for each (region, category):
    extract Top-100 from CMS+Heap → Trending row in Redis
```

Why CMS instead of exact counts? With 10 B videos, exact frequency count needs 10 B counters per window per (region × category). CMS approximates with 50 KB per window, 1% error rate — perfectly fine for "trending" (nobody knows the difference between #45 and #46).

### 13.6 Search — hybrid retrieval

Two indexes, one query path:

1. **Elasticsearch** — inverted index over `title + description + ASR_captions + tags + comments_summary`. BM25 scoring with field weights (title ×3, description ×1, ASR ×0.5, tags ×2). Multi-language tokenisers.
2. **Vespa** — ANN vector index over a 512-d embedding combining title-embedding (BERT) + thumbnail-embedding (ResNet). Supports semantic queries ("a video like X").

Query flow:
```
1. Parse query, detect language
2. Expand synonyms (e.g., "soccer" → "football")
3. Query both ES (BM25) and Vespa (ANN) in parallel
4. Score-fusion: final = 0.6 × BM25 + 0.4 × ANN_cosine
5. Take top-100
6. Personalise re-rank with user features (small DNN, ~5 ms)
7. Return top-20 with cursor for pagination
```

Re-indexing: every video's `video.indexed` event triggers a delta update (~5 min latency from publish to searchable). Full nightly rebuild absorbs deletions / privacy changes / newly-trained embeddings.

### 13.7 Personalisation features used everywhere

The same feature store powers:
- Recommendation
- Search re-rank
- Notifications targeting (which subscribers really want this push?)
- Ad targeting (with explicit consent boundaries — we don't share between ads-features and reco-features without contract)

Features are tier-1 stored in Bigtable (online, ~ms reads), tier-2 in BigQuery (offline, training).

---

## 14. The View Counter at Scale (the famous interview question)

This is *the* YouTube system-design canonical. Get it right.

### 14.1 The naive solution and why it fails

```python
# DON'T DO THIS
def on_play(video_id):
    db.execute("UPDATE videos SET view_count = view_count + 1 WHERE id = %s", video_id)
```

Three failures at scale:
1. **Hot row contention** — a video at 1M views/min would sustain 1M write locks per minute on one Spanner / Postgres row → throughput ceiling ~5K writes/sec → 95% of writes time out.
2. **No deduplication** — the same user reloading the page = 100 fake "views". Bots can drive view counts arbitrarily.
3. **No watch-quality signal** — a 0.5-second tap-and-leave shouldn't count as a "view". YouTube defines view = "≥ 30 s of monotonic playback OR completion". Naive UPDATE doesn't capture this.

### 14.2 The right solution — three layers

**Layer 1: 30-s watch threshold + per-day per-user dedupe.**

Beacon path:

```
POST /beacon { user_id, video_id, cumulative_play_ms, ... }
  ↓
View Beacon Collector:
  if cumulative_play_ms < 30_000:
    return 204  (not a view yet)
  
  # Dedup per user per video per day
  added = redis.SADD("seen_views:" + day + ":" + user_id, video_id)
  if added == 0:  # already counted
    return 204
  redis.EXPIRE("seen_views:" + day + ":" + user_id, 90_000)  # 25h TTL
  
  produce(kafka_topic="watch.events", key=user_id, value={
    type: "VIEW", video_id, user_id, day, timestamp
  })
  return 204
```

Anonymous viewers: dedupe by `anonymous_id` cookie. Lossy if cookies clear.

**Layer 2: Stream-aggregate via Flink (1-min windows).**

```
Flink job:
  source: kafka("watch.events").filter(type=="VIEW")
  keyBy(video_id, day)
  window: tumbling(1 min)
  aggregate: COUNT(*)  + HLL_AGG(user_id)
  sink:
    Bigtable: INCR view_counters[video_id][day] += count
    Redis:    INCRBY view_counters_hot:{video_id} += count
    Redis:    PFADD viewers:{video_id}:{day} + user_ids... (HLL for unique-viewer)
    Kafka:    features.stream {video_id, count_delta_1min, ...}
```

The window collapses many writes into one INCR. A video with 1M views in a minute → one INCR of +1M, not 1M individual INCRs.

Watermarking: late beacons (mobile offline → reconnect) accepted up to 5 min late.

**Layer 3: Read-through Redis hot cache (TTL 30 s).**

```
GET /watch?v=X → Watch Svc returns video metadata which includes view_count
  ↓
Watch Svc:
  vc = redis.GET("view_counters_hot:X")
  if vc is None:  # cache miss
    vc = bigtable.read("view_counters", row=X, sum_columns="day_*")
    redis.SETEX("view_counters_hot:X", ttl=30, value=vc)
  return vc
```

Total freshness: beacon → Flink window (1 min) → Bigtable + Redis → next read (TTL 30 s) → ≤ 60 s p99 shown to user.

### 14.3 The famous "301 → 302" view freeze

In 2014, YouTube's view counter would get *stuck at 301* for hours after a video went viral. Why? Anti-fraud: above 301 views, every increment had to pass an extra anti-fraud check (bot detection: traffic source, IP entropy, watch-pattern). The check ran in batch hourly; the displayed count "froze" until the batch caught up.

We replicate the spirit: a video that's been counted in the last 5 min flows through normal aggregation; one that's spiking abnormally (e.g., view rate > 100× baseline) gets routed to a slower fraud-check path. The display shows the last verified count + "verifying…" for the lag period. Cosmetic but contractually required for ad-revenue accuracy.

### 14.4 Hot video — sharding the counter

For viral videos at 100K views/sec, even our 1-min Flink window churns through ~6M increments per window per video — fine for Flink to process but the **sink** (Bigtable INCR on one row) becomes the bottleneck.

Sharded counter:
```
Bigtable: view_counters[video_id][day]_shard_0 .. _shard_15
Flink writes to a random shard per increment.
Read sums all 16 shards.
```

For ultra-hot (top-10 globally), we keep counts in Redis HINCRBY across 32 shards and flush to Bigtable every 5 s.

### 14.5 Unique-viewer count via HyperLogLog

The "unique viewers today" stat (shown in Creator Studio) requires deduplication across hundreds of millions of viewers. Storing a `Set<user_id>` is infeasible (a video at 100M unique viewers × 16-byte UUID = 1.6 GB per video per day).

HyperLogLog:
- Per (video_id, day) store a single HLL sketch (~14 KB at 1% error).
- `PFADD viewers:{video_id}:{day} {user_id}`
- `PFCOUNT viewers:{video_id}:{day}` → estimate within 1% error.

Aggregating across days: `PFMERGE viewers:{video_id}:lifetime viewers:{video_id}:day1 viewers:{video_id}:day2 ...` — HLL has the lovely property that `PFMERGE` is associative, so we can roll up cheaply.

### 14.6 Edge cases

- **User watches the same video twice in a day** → counted once (per-day dedupe). YouTube creator tools clarify "unique viewer" vs "raw playback" stats.
- **User watches via embed (e.g., on a third-party site)** → counts the same way; the embedded player sends the same beacon.
- **User watches in a private window / signed out** → anonymous_id (cookie) used for dedupe; lossy.
- **Re-watching part of the video (seek back)** → cumulative_play_ms tracks monotonic *unique* play time; a seek-back doesn't double-count. The player tracks "highest position reached so far" and reports the unique-watched-time.
- **Bots** → multi-layer fraud detection: IP entropy (single IP at 1M views/min for one video → suspicious), behaviour pattern (no mouse movement, no scrolling), proxy / VPN heuristics. Flagged views are dropped from the count but persisted in audit logs.

---

## 15. Live Streaming (LL-HLS / LL-DASH / RTMP / SRT Ingest)

### 15.1 The latency budget for live (5 s glass-to-glass)

| Stage                                              | Target  | Notes                                                                 |
|----------------------------------------------------|---------|-----------------------------------------------------------------------|
| Camera → encoder (creator's machine)               | 100 ms  | OBS / mobile encoder                                                   |
| Encoder → ingest gateway (RTMP / SRT)              | 100 ms  | Public internet; SRT adds ~100 ms for jitter buffer                   |
| Ingest → Live Transcoder (in-network)              | 50 ms   | Co-located GPU pool                                                   |
| Live Transcoder (CMAF chunked encode, 200ms partials) | 500 ms  | Per-rung NVENC encode                                                  |
| Live Packager → CDN origin                         | 200 ms  | HTTP/2 push from packager to CDN origin                                |
| CDN propagation to PoP                             | 1 s     | Origin shield → CDN regional → PoP (depends on PoP geography)         |
| PoP → player                                       | 500 ms  | LL-HLS partial pulled as soon as available                             |
| Player buffer + decode + render                    | 1 s     | One full segment buffered before play                                  |
| **TOTAL p99**                                      | **~3.5 s** | leaves headroom under the 5 s SLO                                  |

### 15.2 LL-HLS vs WebRTC

We use LL-HLS, not WebRTC. Trade-off:

| Aspect | LL-HLS | WebRTC |
|--------|--------|--------|
| Latency | ~3-5 s | ~200 ms |
| Scale per stream | Millions of viewers | Limited (~hundreds, then SFU needed) |
| CDN-friendly | Yes (HTTP) | No (UDP, peer-to-peer) |
| Cost per viewer | Cheap (CDN cache) | Expensive (compute per peer) |
| Use case | Live streaming to mass audience | Real-time interactive (Zoom-style) |

YouTube Live serves up to millions of concurrent viewers per stream — WebRTC can't scale there. The 5-s latency is an acceptable trade-off for the cost.

### 15.3 RTMP vs SRT for ingest

RTMP (Real-Time Messaging Protocol):
- Decades-old (Adobe Flash era), TCP-based.
- Universally supported by encoders (OBS, ffmpeg, every webcam software).
- Suffers on lossy networks (TCP retransmits stall the stream).

SRT (Secure Reliable Transport):
- Modern (Haivision, 2017+), UDP-based with optional retransmission.
- Better on lossy networks (re-transmits dropped packets without head-of-line blocking).
- Less universal — but OBS supports it; modern encoders prefer it.

We accept both. SRT recommended for long-form / broadcast-grade; RTMP for casual mobile-go-live where TCP is fine.

### 15.4 Active/standby ingest

For each stream key, two ingest pods receive the same RTMP / SRT feed (the encoder publishes to a load balancer that fans out). Only the **active** pod's output flows downstream. etcd lease (3 s TTL) for active election.

On primary failure, etcd lease expires in 3 s; standby acquires lease and promotes itself; from the encoder's POV nothing happened (it's still pushing to the same LB). On the viewer side, a 1-s glitch may occur (one segment skipped); LL-HLS player tolerates this.

### 15.5 DVR window + post-event archive

The Live Transcoder writes 6-s standard segments to GCS for the entire stream duration. Viewers tuning in late can scrub back up to 4 hours.

Post-stream:
1. Stream ends (encoder stops pushing, or creator clicks "end stream").
2. DVR segments are kept as-is for 30 days as the "post-event replay".
3. A background job re-encodes the DVR archive into proper VOD ABR ladders + HLS / DASH manifests + ASR captions.
4. The live event row → permanent VOD video in catalogue.

### 15.6 Live chat (different system, briefly)

Live chat is a separate WebSocket channel (not on the video pipeline at all):
- Per-stream chat room, ~10K msg/s peak for a marquee event.
- Pub/Sub fan-out via WebSocket Gateway (sticky sessions, ~5K connections per pod).
- Slow-mode / subscriber-only mode / moderator mute.
- Super Chat / Super Sticker (paid messages) routed through Payment Service first, then echoed back on success.

---

## 16. Comments, Likes, Subscriptions, Notifications (Engagement)

### 16.1 Comments — Bigtable wide-row

`comments` table partitioned by `video_id`:
```
Row key: video_id + '#' + reverse_ts_ms + '#' + comment_id
Columns: c:user_id, c:text, c:parent_comment_id, c:like_count, c:reply_count, c:moderation_state, c:is_pinned
```

Why reverse-ts? So `Newest` ordering is a contiguous range scan from row-start. `Top` ordering needs a secondary score column that we periodically refresh.

Reads:
- `GET /comments?video_id=X&page=1` → range scan `video_id#*#*` LIMIT 50, cursor based.
- For "Top" ordering: maintain a per-video Top-50 sorted set in Redis (`ZSET comments_top:X`); refreshed every 30 s by a Flink job rolling over `engagement.events.COMMENT_LIKED`.

Writes:
- `POST /comment` → toxicity classifier → Bigtable atomic mutation (insert + outbox row in same atomic operation).

### 16.2 Likes — eventually consistent counters

Per-(target, user) write:
```
POST /like { target_id, target_kind: 'VIDEO' | 'COMMENT' }
  ↓
Bigtable atomic mutation:
  Insert into likes (target_id + '#' + user_id) → liked_at
  (atomic: also upserts likes_outbox row)
  ↓ async via Outbox Publisher
engagement.events.LIKE → Flink → INCR target.like_count denormalisation
```

The denormalised `like_count` on the `videos` and `comments` rows is refreshed every 5 s by a Flink rollup job. UI shows "5.2K likes" eventually consistent.

Toggling a like: `DELETE /like` → soft-delete in `likes`; INCR target.like_count -= 1 via Flink.

Public dislike count: hidden since 2021 by YouTube for trolling-mitigation; we still maintain the count internally for the creator's view and for ranking signals.

### 16.3 Subscriptions

Per-(user, channel) write to the `subscriptions` table. Two read patterns:

1. **"What channels does user X subscribe to?"** — primary key `(user_id, channel_id)`; range-scan on `user_id` returns all subscriptions in one tablet.
2. **"Who subscribes to channel X?"** — secondary index on `channel_id`; needed for fan-out on upload notifications. Denormalised; updated async.

Subscriber count: cached per-channel in Redis ZSET; refreshed every minute via Flink rollup of `engagement.events.SUBSCRIBE` / `UNSUBSCRIBE`.

### 16.4 Notifications fan-out — the Mr Beast problem

A channel with 300M subscribers uploads. We need to notify all 300M.

Naive: loop over `subscriptions WHERE channel_id = X` → emit one notify event per row → ~300M events. At 100K notifs/s sustained throughput → 50 min wall-clock.

Sharded:
```
On upload event for channel C with N subscribers:
  Notification Service issues N/K events (K = ~1000 per shard) to a "fanout job" topic.
  N/K = 300K shard-jobs.
  
  Per-shard worker:
    Reads K subscribers from `subscriptions` (paginated)
    Produces K notify.events to per-recipient topic (key = recipient_user_id)
  
  Fan-out completes in ~5 min wall-clock.
  
  Notification Worker (256 partitions of notify.events) consumes:
    Per-event: emit FCM/APNS push (~10 ms) + email (~50 ms)
  
  Final delivery: 5 min push, 30 min email.
```

For non-Mr-Beast channels (median ~1000 subscribers), the whole fan-out completes in seconds.

Idempotency table in Bigtable:
```
notifications_sent (PK = (event_id, recipient_user_id, channel))
```
Reprocess on retry → idempotent.

### 16.5 Rate-cap and dedupe per user

Even with Mr Beast: a user subscribed to 50 channels may get 50 push notifs / day → spam. We apply per-user rate-cap:
- Max 5 push notifs / day from any one channel (digest the rest).
- Max 10 push notifs / day total (digest the rest).
- Respect "do-not-disturb" window (10 PM – 7 AM local time).
- "Bell" preference: ALL (every upload), PERSONALISED (the recommender picks which uploads to notify), NONE.

---

## 17. Content-ID, NSFW, DMCA, Moderation Pipeline

### 17.1 The pipeline (every upload)

```
video.uploaded
  ↓
Parallel (don't serialise these — total wall-clock = max, not sum):
  ├─ Transcoding (~50 s for 5-min 1080p; §10)
  ├─ ContentID (§17.2; ~30 s)
  ├─ NSFW / Safety (§17.3; ~30 s)
  └─ ASR captions (§10.6; ~60 s)
  ↓
All complete → video.transcoded → mark videos.ingest_status='PUBLISHED'
  IF contentid_state = 'BLOCKED' OR safety_score > 0.95:
    → mark ingest_status='BLOCKED' instead
    → notify creator with reason
```

### 17.2 ContentID — the deep dive

The pipeline (already sketched in §9.8 / §8.5.3, expanded here):

1. **Audio fingerprinting** — `chromaprint`-style (12-d chroma vectors per 100 ms); produces ~7 KB / minute fingerprint.
2. **Video fingerprinting** — pHash (perceptual hash) per keyframe + DCT-based features; ~2 KB / keyframe.
3. **Reference DB** — ~50M reference assets (every commercial album, every TV show, every movie that rights-holders have submitted). Reference DB is a partitioned LSH index in our own datastore (sharded by hash bucket).
4. **Match algorithm**:
   - Coarse retrieval: LSH-banding to find ~100 candidate references per query.
   - Fine alignment: dynamic-time-warp align query fingerprint to candidate; compute correlation score.
   - Threshold: > 0.85 audio correlation OR > 0.7 video correlation OR coincidence of both → match.
5. **Match policy** (per matched reference's rights config):
   - **BLOCK** — entire upload is hidden from the public; creator notified.
   - **MUTE_AUDIO** — audio replaced with silence; video plays.
   - **MONETISE** — ads run; revenue routes to claimant per their share %.
   - **TRACK** — analytics only; claimant notified, no monetisation change.

Stored to `videos.contentid_state`; `moderation.events.contentid_match` emitted.

**Counter-claim flow:**
- Creator disputes via Studio.
- Routes to Moderation Console.
- Claimant has 30 days to respond.
- If claimant doesn't respond → claim auto-released, monetisation goes back to creator.
- If claimant escalates → manual human review at YouTube; in extreme cases, court.

**False positives:**
- Common background music (e.g., Brahms played in a piano lesson video) → matches commercial recording of Brahms → unfair monetisation to claimant.
- Mitigation: rights-holders can opt out of small-snippet matching (we only block on full-track match for low-tier rights).

### 17.3 NSFW / Safety — multi-modal

PhotoDNA: instant CSAM detection (perceptual hash match against NCMEC's reference DB). Match → instant block + auto-report to NCMEC + freeze creator's account.

NSFW image classifier: ResNet-style fine-tuned on labelled NSFW data. Score ≥ 0.9 → age-restrict (show "must be 18 to view"); score ≥ 0.99 → block + human review.

Hate speech: text classifier over title + description + comments + ASR captions; multi-language; score ≥ 0.8 → flag for human review.

Misinformation: per-topic classifiers (election, health, war); high-impact topics get extra attention; demotion in ranker; info-panel attached on watch page ("for context, see WHO's article").

### 17.4 DMCA take-down workflow

Rights-holder files a take-down notice (often automated via the "Copyright Match Tool" or DMCA partner API):

```
DMCA notice → Moderation Console → human review (~1 h SLA for reputable claimants):
  → If valid: hide video (videos.ingest_status='BLOCKED'); creator notified, given 14 days to counter-claim
  → If invalid: rejected; claimant can escalate
  
On counter-claim: video stays hidden; if rights-holder doesn't sue within 14 days, video restored
```

### 17.5 Moderation Console

Internal app for human reviewers. Surfaces:
- Flagged content (NSFW, hate, misinformation)
- DMCA + counter-claim queue
- Appeals (creators contesting block decisions)
- Legal hold workflow (subpoenas, government requests)

Reviewers categorise + decide; decisions feed back into the ML training data (active learning).

---

## 18. Edge Cases & Gotchas

### 18.1 Hot video / viral spike

Symptom: a video goes from 100 views/min to 100K views/min within hours.

Failure modes:
- Spanner row contention on `videos[video_id]` view_count → mitigated by sharded counter (§14).
- One CDN PoP saturating its egress → mitigated by multi-CDN spillover.
- Reco pipeline: the video isn't in any user's pre-computed row → mitigated by the trending row + by online ranker re-ranking with fresh features.
- Comments exploding → 10K comments/s on one video; one Bigtable tablet hot-spots. Mitigation: salt the row key with `(video_id, hash(user_id) % 16)` for high-write-rate videos; reads union the 16 sub-rows.

### 18.2 Creator deletes a popular video

Symptom: a video with 100M views suddenly disappears.

Cascade:
- Watch URL → 404 (Watch Svc reads videos.ingest_status='DELETED' → returns 404).
- Recommendations linking to it → broken link in user's home rows; Reco Svc filters out deleted videos at hydration time (the reco rows store IDs only).
- Comments DB unchanged; orphaned comments are cleaned up by a background job.
- View counter / monetisation history retained for audit.
- ContentID claims retained.

### 18.3 Channel takes-down their own channel

Cascade: all owned videos → DELETED; subscriptions to this channel auto-broken; subscriber receives a "this channel is no longer available" notification.

### 18.4 Mass account deletion (GDPR right-to-be-forgotten)

User requests data deletion under GDPR Article 17:
1. Schedule deletion (24-h grace period in case of mistake).
2. After grace: delete user record from Spanner; null out user_id on watch.events / engagement.events; PFDEL from HLLs (best-effort, HLL doesn't truly support deletion); delete personal feature vectors from feature store.
3. Audit log retains the *fact* of deletion + admin who actioned it (anonymised user references).
4. Inform user via email when complete (~30 days max per GDPR).

The catch: data already in BigQuery exports may persist in DR backups; we maintain a "tombstone" table that GDPR-blocks any future analytical access to that user's data, even from old snapshots.

### 18.5 Live stream encoder crashes mid-stream

Encoder dies; stream key still associated with the dead session. Two scenarios:
- **Encoder restarts within 10 s** → reconnects with same stream key; ingest pod accepts (sequence numbers continue); ~1-s blip on viewer side (one segment skipped).
- **Encoder doesn't recover within 30 s** → ingest gateway marks stream "interrupted"; viewer player switches to "stream interrupted, will resume" UI; if not back in 5 min → stream ended.

### 18.6 Region-wide CDN outage

Akamai's entire EU region goes down. Per-region CDN routing config detects (via cdn.qoe metrics) and within ~30 s shifts all weight to other CDNs; PoP capacity caps trigger spillover.

User-visible: ~30 s of degraded experience for users on Akamai-routed paths; rebuffer rate spikes briefly; ABR drops bitrate to compensate; recovers as new manifest URLs route through other CDNs.

### 18.7 The "infinite Shorts feed" anti-pattern

YouTube Shorts is a vertical-scrolling feed (TikTok-style). Each scroll = a new video play. If we count every Shorts swipe as a "view" (no 30-s threshold), the view counts inflate by 10×.

Mitigation: Shorts views require ≥ 5-s play (Shorts are typically 15–60 s, so 5 s = 30% of duration — analogous to the 30-s threshold for long-form).

### 18.8 Embeds on third-party sites

A blog embeds our video via `<iframe src="https://yt.example/embed/X">`. The embedded player:
- Sends watch beacons exactly like the native player.
- View counts the same way (with cookie-based anonymous ID).
- Ads run on the embed (revenue share with the embedding site if they're a partner).

Geo-restriction edge case: a US-blocked video is embedded on a US blog → embed iframe shows "this video isn't available in your region".

### 18.9 Monetisation "yellow icon" (limited ads)

YouTube's ad-policy classifier scans every monetised video. Borderline content (mild violence, sensitive topics) gets a "yellow icon" — limited ads (some advertisers opt out, monetisation reduced ~30%).

We model this in `videos.monetisation_state ∈ {'GREEN', 'YELLOW', 'RED'}`. Determined automatically + creator can request manual review.

### 18.10 Premium subscription downgrade

A user downgrades from Premium to Free. Their downloads (offline-played videos with DRM-cached licenses) need to be invalidated.

Subscription Svc → strong-consistency write to Spanner → CDC into Redis (subscription_state cache) within seconds → next /watch call returns "ad-supported" version → existing playback continues until next manifest re-fetch (5 min TTL on signed URL) → next session starts ad-supported.

For downloads: licenses are device-bound, 30-d TTL; we revoke by issuing a "license revocation" push notif → device wipes the cached license + offline media.

### 18.11 The "chatGPT-spam comment" problem

Bots flooding popular videos with templated comments. Mitigation:
- Toxicity + LLM-detector classifier on every comment (~10 ms p95).
- Per-IP / per-account rate limit (token bucket in Redis).
- Velocity-anomaly detection (a brand-new account commenting 100 times in an hour → suspended).
- Captcha challenge on suspicious patterns.

### 18.12 Region-blocked content via VPN

A user in Germany uses a US VPN to watch a US-only video. Our IP geo lookup says "US"; we serve. The user sees the video but the legal-licensing accounting routes wrong (we report watch-time to the wrong region's rights-holder).

Mitigation: secondary signals — the user's account country (locked at signup), historical IP patterns, payment country. Discrepancies trigger a "are you really in country X?" prompt for monetised content. Imperfect; some leakage tolerated.

---

## 19. Security, Monetisation, Privacy, Geo-Compliance

### 19.1 Authentication & session

- OAuth2 + OIDC (Google Sign-In primary).
- Refresh token: 30-d TTL, stored in Redis as `refresh:{user_id}:{device_id}`.
- Access token (JWT): 1-h TTL, contains `user_id`, `country_home`, `subscription_plan`, `is_kids`, signed by HSM-backed Ed25519 key.
- Passkey / WebAuthn for sensitive actions (channel deletion, payout).
- 2FA via TOTP for monetised accounts (mandatory).

### 19.2 Per-region content compliance

| Country | Special rule |
|---------|--------------|
| **EU (DSA)** | Transparency reports, takedown SLAs (24 h for illegal content), user appeal mechanism. We expose `/transparency` API. |
| **Germany (NetzDG)** | Hate-speech takedown within 24 h or fines up to €50M. Special hate-speech classifier with high recall in German. |
| **India (IT Rules 2021)** | Grievance officer in India, appoint compliance contact, monthly compliance reports. Geo-block on government order within 36 h. |
| **China** | YouTube blocked entirely; we don't serve. |
| **Brazil (Marco Civil)** | Data localisation for some content categories. |
| **California (CCPA)** | "Do not sell my data" toggle in user settings; honoured across the ad-targeting pipeline. |
| **USA (DMCA + COPPA)** | DMCA takedown response within 14 d; COPPA-compliant kids handling (no targeted ads, no comments, no recommendations from adult content). |

### 19.3 Payments & monetisation

- **Premium** subscription: Stripe / Razorpay / Apple-IAP / Google-Play-Billing / Amazon-IAP. Same patterns as the OTT doc — webhooks treated as events, reconciliation pulls truth from provider, payment_provider locked at signup.
- **Super Chat** (live paid-message): Stripe / Google Pay / UPI; routed through the Payment Service first, on-success echoed back to live chat.
- **Channel memberships**: monthly recurring on Stripe; per-channel pricing; tier-based perks (badges, exclusive videos).
- **AdSense revenue share**: per-creator monthly payout; revenue computed by Spark batch from `watch.events` × ad-impression-events.

### 19.4 Privacy

- All PII encrypted at rest (KMS).
- Watch history available to user via "Your data" download (GDPR right-to-portability).
- "Incognito mode" — watch.events not personalised, not used for recommendations.
- "Pause history" — opt out of watch-history-driven personalisation.
- Cookie consent banner per region (EU, California, Brazil).

### 19.5 DRM (when used)

- Widevine + PlayReady + FairPlay on a shared CMAF ciphertext (CENC).
- Master keys in Cloud HSM; per-region delegate certs.
- License TTL 30 min; bound to device-id for offline downloads.
- Output protection (HDCP for 4K HDR; HDCP 2.2 mandatory for premium).
- Keys never leave the TEE / Secure Enclave on the device.

### 19.6 Audit & forensics

- Every login, upload, comment moderation, DMCA action, admin action → `audit.events` Kafka topic → S3-WORM bucket with 7-year retention.
- Searchable via ClickHouse for hot 30 days, BigQuery for older.
- Used for: compliance audits, fraud investigations, legal subpoenas.

---

## 20. Observability & Quality-of-Experience Telemetry

### 20.1 The QoE metrics that matter

| Metric | Target | Source |
|--------|--------|--------|
| TTFF p99 | ≤ 1 s (cache hit) | Player beacon |
| Rebuffer ratio | < 0.5% | Player beacon |
| Avg bitrate / session | > 720p for fibre, > 480p for LTE | Player beacon |
| First-segment-fetch p99 | < 400 ms | Player beacon → cdn.qoe |
| Manifest-fetch p99 | < 200 ms | Player beacon |
| CDN cache-hit ratio | ≥ 95% | CDN provider logs |
| ContentID-positive rate | < 30% | ContentID Engine |
| Upload-to-publish p99 | ≤ 60 s (1080p, 5min) | Pipeline DAG metrics |
| Recommendation serve p99 | ≤ 100 ms | Reco Svc |
| Search p99 | ≤ 200 ms | Search Svc |
| View count freshness p99 | ≤ 60 s | Beacon → display delta |
| Live glass-to-glass p99 | ≤ 5 s | Live encoder timestamp → player render timestamp (out-of-band measurement) |

### 20.2 Stack

- **Prometheus + Grafana** for service-level metrics (latency, error rate, saturation).
- **Jaeger** for distributed traces (sampling 1% of requests).
- **ELK / Loki** for log aggregation.
- **Druid / Pinot** for QoE OLAP (per-CDN per-region per-device per-time-bucket aggregations).
- **BigQuery** for offline analytics (creator dashboards, exec reporting).

### 20.3 Alerting tiers

- **SEV-1** — site-wide outage (paging on-call within 60 s):
  - Watch Svc 5xx rate > 1%
  - CDN cache-hit ratio < 80% sustained
  - Spanner unavailability > 30 s
- **SEV-2** — degraded experience (Slack ping):
  - TTFF p99 > 2 s sustained
  - Recommendation latency > 200 ms
  - Kafka consumer lag > 5 min
- **SEV-3** — investigative (daily digest):
  - Increased ContentID match rate (potential rights-holder DB issue)
  - New region's QoE degraded

### 20.4 Creator analytics dashboard (Studio)

- Real-time view rate (per-minute over last 60 min) → Druid query.
- Geographic split (last 24 h, last 7 d, last 28 d) → BigQuery aggregate.
- Source-of-traffic (search / suggested / external / direct) → BigQuery + on-the-fly Druid for recent.
- Watch-time distribution (which segments of the video have the most rewatch / dropout) → Player beacon → Bigtable per-(video_id, second_bucket) histogram.
- Revenue breakdown — daily, monthly. Spark output → BigQuery.
- Subscriber growth + demographics — BigQuery + Druid for trending.

---

## 21. Technology Choices — Final Verdict

| Layer | Choice | Why |
|-------|--------|-----|
| Object store (raw + transcoded) | **GCS / S3 multi-region** | 11-9s durability; cheap; cross-region replicated; cold-archive tier |
| Catalogue DB | **Spanner / Vitess** | Multi-region strong consistency; sharded by video_id; SQL-like for creators / admins |
| Comments / Likes | **Bigtable / Cassandra** | Wide-row partitioning; high-write append-mostly; horizontal scaling |
| View counter | **Bigtable + Redis HLL + Flink** | Atomic INCR + HLL for unique-viewer; Flink rollups for tumbling-window aggregation |
| Hot cache | **Redis (cluster)** | Sub-ms reads; HSET for reco rows, HLL for unique-viewer, ZSET for ranked lists |
| Search index | **Elasticsearch + Vespa** | BM25 (ES) + ANN vector (Vespa) hybrid retrieval |
| Feature store | **Bigtable (online) + BigQuery (offline)** | Online sub-ms reads; offline cheap training; dual-write contract |
| Model serving | **TF-Serving / Vertex AI** | Versioned model store; GPU inference; A/B framework |
| Async backbone | **Kafka (RF=3, idempotent producer, transactional consumer)** | Durable + outbox pattern; exactly-once-into-store |
| Stream processing | **Flink / Beam** | Tumbling/sliding windows; watermarking; state backed by RocksDB |
| Offline batch | **Spark on Dataproc** | Nightly recommendation training + royalty compute |
| Real-time OLAP | **Druid / Pinot** | Sub-second QoE aggregations |
| Data warehouse | **BigQuery / Snowflake** | Long-term aggregates; SQL for analysts |
| Coordination | **etcd / Consul** | Live ingest leader election; CDN router config; feature flags |
| Multi-CDN | **Akamai + CloudFront + Cloudflare + Limelight + ISP-cache** | No single-CDN dependency; ISP-cache serves ~50% of bytes |
| DRM | **Widevine + PlayReady + FairPlay (on shared CENC ciphertext)** | All three platforms with one ciphertext; only when DRM required |
| Live ingest | **RTMP + SRT** | Universal (RTMP) + reliable on lossy networks (SRT) |
| Live packaging | **LL-HLS + LL-DASH (CMAF chunked, 200 ms partials)** | ~3-5 s glass-to-glass; CDN-friendly; mass-scale |
| Auth | **OAuth2 / OIDC (Google Sign-In primary)** | Universal; passkey / WebAuthn for sensitive |
| Edge + API GW | **Envoy + Google GFE-equivalent** | TLS, JWT, rate-limit, geo-IP, idempotency |
| WebSocket gw | **Sticky LB (sha1(user_id) hash) + Go-based pod fleet** | 5-10K conns/pod; ring-buffer 30 s for resume |
| Encoder pool | **NVENC GPU (A100) + AWS MediaConvert spillover** | GPU-accelerated FFmpeg; spillover for catch-up backlog |
| Container orchestration | **Kubernetes** | The standard; per-shard publisher pods, transcoding workers, all stateless services |
| Observability | **Prometheus + Grafana + Jaeger + ELK / Loki** | Standard cloud-native stack |
| Compliance / WORM audit | **S3 Object Lock / GCS Bucket Lock** | 7-y immutable; FCC / SEC / GDPR audit trail |

---

## 22. Q&A Defense — Top 25 Tough Interview Questions

### Q1. How do you handle a viral video that goes from 100 views/min to 100K views/min in one hour?

The system has to absorb a 1000× burst on three independent axes:

**Egress**: the CDN absorbs almost all of it. Cache-hit ratio on this video shoots from "cold" to "hot" within minutes as ISP-caches and PoP-caches populate. Origin shield sees ~5 K req/s peak (1000 PoPs each missing once until populated); GCS sees ~200 req/s. Multi-CDN routing balances across 5 CDNs + ~15 K ISP-cache appliances → no single PoP saturates. Within 10 minutes, ~95% of bytes for this video come from cached edge.

**View counter**: the existing pipeline handles it natively. 1-min Flink tumbling window aggregates millions of beacons into one INCR. We have **sharded counters** in Bigtable (16 shards per video) so even a 100K-views/min video distributes across 16 row-keys → 6 K/min/shard → no contention. Redis HLL absorbs the unique-viewer count.

**Recommendations**: this video is by definition not in any user's pre-computed home row (it's brand new). Two safety nets: the **trending row** (refreshed every 10 min via Count-Min Sketch + Heap) catches it within 10 min; the **online ranker** picks it up within minutes via streaming features (`features.stream` from `watch.events`).

**Failure mode I'd watch for**: comments. 10K comments/sec on one video = one Bigtable tablet hot-spots. Mitigation: salt the row key with `(video_id, hash(user_id) % 16)` for high-write videos; reads union the 16 sub-rows. Detected automatically when `comments_write_rate > 1K/s/video` triggers the salt path.

### Q2. View count freezes at 301 — what's actually happening?

YouTube's classic 301 freeze (2014) was an anti-fraud check kicking in above 301 views. We replicate the spirit: a video whose view rate exceeds 100× baseline gets routed to a slower fraud-check path before its count is incremented. The display shows the last verified count + "verifying..." for the lag window (~5 min).

In our system: the View Aggregator flags these videos via a "fraud-check pending" flag. Display layer reads `view_counters_hot:X` from Redis; if the flag is set, it serves the last *verified* count (which is `view_counters_verified:X`, updated only after fraud check passes). User sees the count tick up in chunks every ~5 min instead of smoothly.

This is contractually required for ad-revenue accuracy — advertisers will sue if we charge them for fake views.

### Q3. How do recommendations stay fresh within minutes of a new behaviour?

Two-layer:
- **Offline pipeline** (nightly Spark): retrains the DNN ranker, refreshes user feature vectors. Latency: ~24 h.
- **Online streaming** (Flink → feature store): user's last 5 plays, recent topics, current session theme. Latency: ~5 s end-to-end from the play event to the feature store.

The Recommendation Service reads pre-computed Redis rows (TTL 5 min) but **re-ranks the top of each row in real-time** using the online streaming features. So if the user just watched a JavaScript tutorial, within 5 s their next home refresh has more JavaScript content at the top, even though the offline-baked rows haven't changed.

### Q4. Why Bigtable for comments and not Spanner?

Three reasons:

1. **Write throughput**: 3 K comments/sec peak (50 K/sec on viral videos) needs a wide-row write path. Bigtable's tablet-server architecture handles this natively; Spanner's Paxos-replicated writes would queue and tail-latency-degrade.

2. **Read pattern**: every comment-list query is `(video_id, ts_desc, LIMIT 50)` — a single-tablet scan. Bigtable's columnar wide-row layout serves this in one I/O. Spanner would need a secondary index + cross-shard reads.

3. **Cost**: Bigtable storage + read I/O is ~5× cheaper than Spanner at this scale. Spanner pays a tax for global strong consistency we don't need for comments (comments are eventual; "your comment shows up in 3 s" is fine).

Spanner stays for `videos`, `channels`, `users`, `subscriptions` — small (~20 TB), strongly-consistent, low-write-rate.

### Q5. How does the upload-to-publish pipeline guarantee exactly-once processing?

Three layers of dedupe:

1. **Upload Service**: dedupes on `(user_id, sha256)`. Re-uploading the same file → returns the same `video_id` (no double-storage). Idempotency.

2. **video.uploaded Kafka producer**: `idempotency_key = video_id`. Broker dedupes within a session. Cross-session: consumer-side dedupe.

3. **Transcoding Workflow**: each step is keyed by `(video_id, step_name, parameters_hash)`. Re-running a step with the same parameters → no-op (the step checks if the output artifact already exists in GCS).

If a transcoding worker crashes mid-job: Kubernetes reschedules; the new worker picks up; the GOP-segment encodes that already completed have outputs in GCS staging → workflow detects and skips them; only missing segments re-encode. Net effect: at-most-once compute, exactly-once output.

### Q6. Why don't all videos get DRM?

Two reasons:
1. **Cost**: every DRM-protected video adds ~200 ms latency (license issuance + key-wrap) to TTFF. Multiplied by 800 M DAU × multiple plays → significant infrastructure overhead. Worth it for premium content; not worth it for the average creator's vlog.
2. **Compatibility**: not every device's DRM stack is reliable. DRM-related "this device isn't supported" errors are a major user-experience issue. For UGC with no rights-holder concern, we'd rather not break users.

DRM kicks in **only** for: YouTube Premium-only content, Movies & TV (rentals), Kids profile-restricted content. These are pre-licensed and contractually require DRM.

For everything else: signed URLs (HMAC, 6 h TTL) + ContentID for copyright protection + Geo-blocking — sufficient.

### Q7. What if Spanner goes down for one region?

Multi-region active-active deployment. Each region has a full Spanner cluster (with leader-region designation per shard). If one region's Spanner cluster fails:

- **Reads**: served from another region's read-replica (~50 ms cross-region latency). Most reads tolerate that.
- **Writes**: failover to another region's leader within ~30 s (Spanner has automatic leader election). During the gap: writes fail with 503 RETRY; clients retry; the API GW returns "uploads will retry — your video will publish once we're back".
- **CDC into Redis**: pauses while the leader is unavailable; Redis serves stale cache (5-min TTL); ageing entries serve slightly stale data until CDC resumes.

User-visible impact: a brief (~30 s) write outage; reads degraded to slightly higher latency; no data loss. This is the contract Spanner gives us.

### Q8. How do you protect against bot views?

Multi-layer:
1. **30-s threshold + per-day per-user dedupe** — already in the design; eliminates simple "open and close in a loop" bots.
2. **IP entropy** — a single IP at 10K plays/min for one video → suspicious; views from this IP excluded from count.
3. **Behaviour pattern** — no mouse movement (web), no scroll (mobile feed), all-the-same-position-in-video → flagged.
4. **Proxy / VPN heuristics** — known data-centre IP ranges → views still counted (for general reach) but excluded from monetisation calculation.
5. **Device fingerprint + cookie age** — brand-new cookies + brand-new browser fingerprint + immediately watching one video → discounted.
6. **ML scoring** — a "real human probability" score per beacon; views below threshold dropped from monetised count but kept in raw count.

The flagged views are persisted to `audit.events`; if a creator's revenue is later disputed, we can replay and recompute.

### Q9. What's your recommendation cold-start strategy?

Three layers:
1. **Onboarding survey** at sign-up: "pick 3 favourite topics" + "rate 5 videos". Gives us an initial user-feature vector (~50 dims).
2. **Popularity fallback**: per-(country, language, time-of-day) trending row. Decent default for everyone.
3. **Exploration via ε-greedy**: 5% of slots filled with "random-ish" candidates, weighted by the user's coarse demographic features. Helps the model learn signal for new users.

After ~10 watched videos, the user-history-embedding starts converging; collaborative filtering kicks in; personalisation rapidly improves.

### Q10. Why not just use one big Postgres for everything?

At 700K writes/sec for view beacons, ~50K writes/sec for likes, 3K writes/sec for comments — Postgres's WAL would be saturated with replication / disk-flush; checkpoints would lag; WAL archive would balloon. We'd need painful manual sharding. The query patterns also differ — wide-row scans (Bigtable shines), columnar OLAP (Druid shines), full-text search (Elasticsearch) — Postgres is excellent at none of those at this scale.

The architectural rule: **pick the right store per workload**. Spanner for OLTP-strong, Bigtable for OLTP-write-heavy, Elasticsearch for search, Druid for OLAP, Redis for hot cache, Kafka for async. Each is doing what it's best at; Postgres would be mediocre at all of them.

### Q11. How does the live stream ingest handle a primary failure?

Active/standby per stream key. etcd lease (3 s TTL) for active election.

Steady state:
- Encoder pushes RTMP / SRT to a regional load balancer.
- LB fans out to both ingest pods (active + standby).
- Both pods receive the bytes, but only the active publishes downstream.
- Standby is "warm" — has the most recent ~2 GOPs in memory.

Primary fails:
- etcd lease expires within 3 s.
- Standby acquires lease, promotes itself, starts publishing downstream.
- Sequence numbers continue without gap (standby has the in-memory tail).
- Viewer sees: one ~1-s segment may be skipped at the cutover; LL-HLS player tolerates this with a brief buffer.

Total user-visible disruption: < 1 s.

### Q12. What's the DRM story for offline downloads (Premium)?

Per-device license:
1. User taps "Download" on a Premium-eligible video.
2. Watch Service (Premium-aware) issues a manifest URL with downloadable variant + a DRM license URL.
3. Client downloads the encrypted segments + requests the license.
4. License is bound to `device_id`, valid for 30 days from download or 48 h from first play (whichever earlier).
5. Encrypted segments stored on device; the platform's DRM (Widevine / FairPlay) provides the key only via the license.

Revoking: when subscription is cancelled, we send a "license revocation" push notification → device deletes the cached license + offline media on next launch. If the device is offline forever: license expires after 30 days regardless.

### Q13. How do you handle GDPR right-to-be-forgotten?

Documented in §18.4. The challenge is **partial deletion across many systems**:
- Spanner: delete user record. Easy.
- Bigtable comments: cannot literally delete (would tombstone-explode); replace user_id with a tombstone sentinel "[deleted user]"; original text preserved (creator can still moderate).
- Watch.events HLLs: can't truly remove from HLL (the algorithm doesn't support deletion). Mitigation: maintain a tombstone table; analytical queries filter on it.
- Feature store: explicit DELETE on all user-feature-vector rows.
- BigQuery historical exports: tombstone table; queries WHERE user_id NOT IN tombstone.
- DR backups: 30-d retention; the tombstone propagates to prevent backup-restoration leaks.

GDPR allows ~30 days for full deletion; we have a deletion workflow that fan-outs to all datastores and confirms within that window.

### Q14. How do you scale comments for a video with 1M+ comments?

Bigtable wide-row pagination handles it natively:
- Row key prefix: `<video_id>` + `<reverse_ts_ms>` + `<comment_id>`.
- "Newest first" page → range scan from row-start, LIMIT 50, cursor-based for next page.
- "Top first" page → maintained as a secondary Redis ZSET (`comments_top:{video_id}`), refreshed every 30 s by Flink rollup over `engagement.events.COMMENT_LIKED`. ZRANGE for pagination.

For a very-popular video (a Mr Beast video with 5M comments), the row scans still work (Bigtable scales to TB-per-row-prefix). The bottleneck is the per-second comment write rate, which we mitigate by row-key salting (§18.1).

### Q15. How do you recommend a brand-new video that has zero watch history?

Cold-start for content (the inverse of cold-start for users):
1. **Content-based features**: new video has title-embedding, thumbnail-embedding, channel-embedding (if the channel has prior videos with watch history). The two-tower model uses these to predict initial CTR.
2. **Channel-affinity boost**: new uploads from subscribed channels get a temporary multiplicative boost in the ranker.
3. **Trending injection**: if the trending engine catches a sudden spike (Count-Min Sketch detects > 10× baseline within first hour), the video gets propagated into the trending row globally.
4. **Exploration slots**: 5% of recommendation slots reserved for "high-uncertainty" candidates; new videos preferentially fill these.

Within ~24 h of upload, enough watch signal has accumulated for the ranker to make confident predictions.

### Q16. What happens if Kafka goes down completely?

Outbox pattern saves us. Every state-changing service (Engagement, Upload, etc.) writes to `*_outbox` rows in the **same atomic mutation** as the user-visible state. If Kafka is unreachable:
- Outbox Publisher backs off and retries; outbox rows accumulate.
- Users continue placing comments, likes, uploads — all are durable in Bigtable / Spanner.
- The user-visible UI continues to work (reads are local).
- Downstream consumers (notifications, analytics, recommendations) **delay** — recommendations may not refresh, push notifs may not fire — but recover within minutes when Kafka returns.

If Kafka outage extends beyond 30 min: outbox tables grow uncomfortably; we have an automatic mitigation that throttles new writes to keep outbox bounded. Beyond 1 h: declare incident, may need to spin up a fresh Kafka cluster and replay.

We've **never lost a user comment** to a Kafka outage in this design. The pattern converts "Kafka outage = data loss" into "Kafka outage = downstream feature delay". Worth the 30-ms additional write latency in steady-state.

### Q17. How does the multi-CDN router decide which CDN to use?

Computed every 30 s by Flink consuming `cdn.qoe`:

```
score(cdn, region, device, asn) = 
  w1 * throughput_p50_kbps
  - w2 * rebuffer_ratio
  - w3 * error_rate
  - w4 * cost_per_GB
  - w5 * (1 - capacity_headroom_fraction)
```

The output (per region × device × asn → cdn weights) is written to etcd. Watch Svc reads it on each play, picks weighted-random.

Capacity caps: if Akamai is at 85% of contractual capacity, the cap forces spillover to other CDNs. Prevents "all eggs in Akamai basket" during a flash-traffic event.

Client-side override: the player itself measures per-CDN throughput on the first few segments; if it sees a streak of failures, it switches to a different CDN URL (Watch Svc returns multiple manifest URLs, one per CDN, in priority order).

### Q18. What's the data flow for the "Up Next" auto-play recommendation?

`GET /watch?v=X` → Watch Svc internally calls `Recommendation Svc /up_next?user_id=Y&from_video=X` (~5 ms p99).

Up Next is a specialised ranker — same model architecture as home, but trained with a different objective: **expected_session_continuation** (will the user keep watching after this video?). Inputs include: the just-watched video's features (so we can pick "like this but different"), the user's session theme (last 3 videos), current session length (less aggressive auto-play after 5+ videos to avoid burnout).

Returned in the same `/watch` response (saves a round-trip). Player auto-plays the top result if the user hasn't disabled auto-play in settings.

### Q19. How do you handle a thundering-herd at the start of a marquee live event?

A premiere starts at 7 PM IST; 50M users tap "Watch live" within 30 s.

Mitigations:
1. **Pre-stage manifest** at all PoPs 30 min before start (push the initial `.m3u8` to PoP edge cache).
2. **Pre-stage first 5 s of segments** at all PoPs (so cold cache misses are minimised in the first window).
3. **Push notification with jitter** (notify viewers between T-15 min and T-0, with random ±5-min jitter) to spread the actual taps across a wider window.
4. **Live ingest active/standby** already in place, so encoder failover doesn't compound.
5. **CDN capacity caps** — automatically spread load across all 5 CDNs from the start (no single CDN gets > 60% of the load).
6. **Rate-limit at API GW** — token bucket per-IP at 10 req/s; if exceeded, 429 with Retry-After hint. Most users only hit `/watch` once per stream; 429s are bots / aggressive retries.
7. **DRM license server pre-warm** — for Premium live, pre-mint license templates so the first burst can be served from cache.

### Q20. How does the sharded view counter handle reads consistently?

Reads sum all 16 shards:

```python
def get_view_count(video_id):
    keys = [f"view_counters:{video_id}:shard{i}" for i in range(16)]
    counts = redis.mget(keys)  # or Bigtable column-family read
    return sum(c or 0 for c in counts)
```

Round-trip: 1 Redis MGET = 1 ms. Bigtable: 1 multi-column read on a single row = 5 ms.

Race condition: a write to shard 7 may not yet be visible when we read shards 0..15. Net effect: a transient under-count of < 1-min worth of views; eventual consistency catches up within seconds. Acceptable per our SLO.

### Q21. What's the failure mode if the View Aggregator (Flink) crashes?

Flink jobs are checkpointed every 30 s to GCS / S3 (state backed by RocksDB on local SSD; periodically snapshotted).

Crash:
1. Flink's job manager detects, restarts the job from the last checkpoint within ~30 s.
2. State is restored from GCS snapshot.
3. Kafka offsets are restored to the checkpoint offsets → re-process all events since the checkpoint.
4. Idempotent sinks (Bigtable INCR is idempotent? — *actually no*, INCR is not idempotent on retry, so we use a different approach):
   - Per-window aggregation produces an **upsert** keyed by `(video_id, day, window_id)` instead of an INCR. The window_id is a Flink-generated unique. Duplicate writes are a no-op because the same window_id is upserted with the same count.
5. View counter row in Bigtable is computed as `SUM(window_counts)` where `window_counts` are stored as columns within a row. Idempotent.

User-visible: ~1-min delay in view-count freshness during the recovery window; no data loss; no double-counting.

### Q22. How do you handle video deletion when it has 1M+ comments / 100M views?

`DELETE /video/{id}` is the entry point:
1. Spanner UPDATE `videos.ingest_status='DELETED'`. This is the single source of truth.
2. Async cleanup workflow:
   - Tombstone all comments in Bigtable (set `moderation_state='ORPHAN'` on the wide-row prefix; lazy GC).
   - Mark all watch.events as orphan in BigQuery (`WHERE video_id = X` filter applied to future queries).
   - Remove from search index (Elasticsearch + Vespa).
   - Remove from recommendation feature store.
   - Schedule GCS object deletion in 30 days (so the creator can request restoration within that window).
3. CDN cache invalidation: the `videos.ingest_status` is read on every `/watch` call; deleted videos return 404 immediately. CDN segments themselves are still cached but unreachable (no manifest URL signed → no path to fetch them).
4. View counter: retained for audit / monetisation history.

The deletion is "instant" from the user's perspective (Watch Svc reads strong-consistency Spanner row); cleanup is async.

### Q23. How do you ensure the recommendation model isn't biased / exploitative?

A mix of:
- **Diversity injection** in the ranker (Maximal Marginal Relevance to avoid mono-topic feeds).
- **Watch-time vs satisfaction**: explicit secondary objective to penalise clickbait (low completion rate after click).
- **Disinformation demotion**: a separate classifier flags health/election misinformation; demoted in ranker; surfaced with info-panel.
- **Filter-bubble mitigation**: explicit "outside your usual" injection.
- **Manual policy overrides**: certain content categories (e.g., self-harm) are demoted globally regardless of engagement signal.
- **Transparency**: creator dashboard shows how recommendations contributed to each video's traffic.
- **Ongoing audit**: independent ML-fairness team reviews ranker outputs across demographic slices.

This isn't a "solved problem"; it's an ongoing balancing act with public scrutiny.

### Q24. Walk me through what happens when a user uploads a 1-hour 4K HDR video.

Timeline:

```
t = 0       Creator clicks "Upload" → POST /upload/init
t = 0..5    Returns signed GCS URL; Studio app starts chunked upload
t = 5..900  Upload streams to GCS (~15 GB at typical 30 Mbps consumer upload = ~15 min)
            On each chunk, Pub/Sub event updates Redis last_completed_offset
t = 900     Final chunk → POST /upload/complete → Upload Svc INSERTs videos row,
            produces video.uploaded
t = 900..950 Workflow Orchestrator forks DAG:
              - Demux into 360 GOPs (50 s)
              - Encode 7 rungs × 360 GOPs = 2520 segment-encodes distributed
                across ~50 transcoding workers
                * H.264 1080p: 5 min on 30 workers
                * VP9 1080p: 10 min
                * H.264 4K: 15 min on 30 workers
                * VP9 4K + HEVC 4K + AV1 4K: 30 min on 30 workers (parallelised)
              - Total wall-clock for transcoding: ~30 min
              - In parallel: ASR captions (~5 min for 1-h audio), ContentID (~30 s),
                Safety scan (~30 s), Thumbnail picks (~10 s)
t = 950..2700 Transcoding completes; Packager builds .m3u8 + .mpd
t = 2700    UPDATE videos.ingest_status='PUBLISHED'; produce video.transcoded
t = 2700..2705 Search Indexer ingests; CDN Pre-warmer (only for predicted-popular) starts
t = 2705..2710 Notification Worker fans out "new upload" push to subscribers
              (capped at 100K/sec aggregate; full fan-out for Mr Beast: ~30 min)
t = 2710    Creator gets email "Your video is live"; first views start trickling in
```

Total: ~45 min for upload + ~30 min for transcoding + ~5 min for downstream ≈ ~80 min from "click upload" to "first viewers see it on home page". Most of that is upload bandwidth (consumer connection-bound).

For a typical 5-min 1080p video: ~2 min upload + ~50 s transcoding + ~5 s downstream ≈ ~3 min total.

### Q25. What's the most architecturally interesting trade-off in this design?

**The view counter + recommendation freshness trade-off.**

We have two competing demands:
- Watch beacons want strong consistency and low latency to power recommendations (the ranker needs fresh signal).
- View counts want eventual consistency and high throughput (we can't afford a synchronous write per view).

The unified solution is the streaming-feature pipeline:
- One Kafka topic (`watch.events`) drives both.
- View Aggregator (Flink) consumes it for view counts (1-min windows).
- Same Flink job emits `features.stream` for recommendations (real-time deltas).
- Both downstreams (Bigtable view counters + Bigtable feature store) are updated within seconds of the beacon.

Net effect: **one event source, two purposes, eventually consistent within ~5–60 s for both**. The user sees fresh recommendations within seconds of their last view; the creator sees the view count tick up within a minute. Neither requires synchronous writes; neither breaks under viral load.

The architectural elegance is that we avoided the temptation to **separate** the "fast" path (recommendation features) from the "slow" path (view counter). Sharing the Kafka backbone + Flink topology means one failure recovery, one capacity-planning unit, one observability dashboard. Compare to the alternative — a separate feature-collection pipeline + a separate view-counter pipeline — where any one breaking causes a cross-cutting outage. Single source, single fault domain, two consumers.

---

## 23. Acronyms & Abbreviations — Cheat-Sheet

> Every acronym used anywhere in this document, expanded once.

### 23.1 Video / streaming

| Acronym | Full form | One-line meaning |
|---|---|---|
| **UGC** | User-Generated Content | Content uploaded by users (the YouTube model), as opposed to professionally-licensed catalogue (the Netflix model) |
| **VOD** | Video On Demand | Pre-recorded video the user can play at any time |
| **AVOD** | Advertising-supported VOD | Free with ads (YouTube default) |
| **SVOD** | Subscription-supported VOD | Paid subscription (Netflix, YouTube Premium) |
| **TVOD** | Transactional VOD | Pay per rental / purchase (YouTube Movies) |
| **OTT** | Over-the-Top | Internet-delivered video bypassing traditional broadcast |
| **HLS** | HTTP Live Streaming | Apple's adaptive streaming protocol; uses .m3u8 manifests + .ts (or .m4s CMAF) segments |
| **DASH** | Dynamic Adaptive Streaming over HTTP | MPEG's adaptive streaming standard; uses .mpd manifests |
| **LL-HLS** | Low-Latency HLS | HLS extension for ~3-5 s glass-to-glass latency |
| **LL-DASH** | Low-Latency DASH | DASH equivalent for low-latency live |
| **CMAF** | Common Media Application Format | Single fragmented MP4 container that both HLS and DASH can reference; halves storage |
| **CENC** | Common Encryption | Encryption format that all three major DRMs (Widevine, PlayReady, FairPlay) can decrypt |
| **PSSH** | Protection System Specific Header | DRM metadata embedded in a media segment |
| **KID** | Key ID | 16-byte identifier for a content encryption key |
| **DRM** | Digital Rights Management | Encryption + license-bound content protection |
| **EME** | Encrypted Media Extensions | Browser API for DRM (used by hls.js, Shaka) |
| **MSE** | Media Source Extensions | Browser API for adaptive streaming (used by hls.js, Shaka) |
| **ABR** | Adaptive Bitrate | Player switches between rendition rungs based on bandwidth |
| **TTFF** | Time To First Frame | Latency from "tap Play" to "first decoded frame on screen" |
| **GOP** | Group Of Pictures | A keyframe + N delta frames; ~10 s for VOD, ~1 s for live |
| **CMCD** | Common Media Client Data | Spec for player→CDN telemetry annotations |
| **ASR** | Automatic Speech Recognition | Speech-to-text (for auto-captions) |
| **VTT / WebVTT** | Web Video Text Tracks | Subtitle format for HLS |
| **TTML** | Timed Text Markup Language | Subtitle format for DASH |
| **SRT** | (1) SubRip Text — subtitle format. (2) Secure Reliable Transport — UDP-based live ingest protocol. | Two unrelated meanings |
| **RTMP** | Real-Time Messaging Protocol | TCP-based live ingest (Adobe legacy, universally supported) |
| **DVR** | Digital Video Recorder | Server-side rolling recording of live stream for scrub-back |
| **NVENC** | NVIDIA Video Encoder | GPU-accelerated H.264 / H.265 / AV1 encoding |
| **VP9 / AV1 / HEVC / H.264** | Video codecs | H.264 universal; VP9 Android/web; AV1 next-gen 30% bandwidth saving; HEVC Apple |
| **AAC / E-AC3 / Opus** | Audio codecs | AAC universal; E-AC3 5.1; Opus newer |
| **SSAI** | Server-Side Ad Insertion | Ads stitched into the manifest at origin (immune to ad-blockers) |
| **CSAI** | Client-Side Ad Insertion | Ads played by the player as separate assets (blockable, but supports skippable / overlay creatives) |
| **PoP** | Point of Presence | A CDN edge location |
| **ISP** | Internet Service Provider | Reliance Jio, Comcast, etc. |
| **HDR** | High Dynamic Range | Wider colour gamut (HDR10, Dolby Vision) |
| **HDCP** | High-bandwidth Digital Content Protection | Output protection between device and TV (mandated for 4K HDR) |
| **VMAF** | Video Multi-method Assessment Fusion | Netflix's perceptual quality metric (0-100; >85 = good) |
| **QoE** | Quality of Experience | End-user-observed metrics (rebuffer, TTFF, bitrate) |

### 23.2 Storage / data

| Acronym | Full form | Meaning |
|---|---|---|
| **GCS** | Google Cloud Storage | Google's S3-equivalent |
| **S3** | Simple Storage Service | AWS object store |
| **WORM** | Write-Once-Read-Many | Immutable storage retention model |
| **TTL** | Time To Live | Auto-expiry duration for a key |
| **HLL** | HyperLogLog | Probabilistic set-cardinality estimator (~1% error, ~14 KB per set) |
| **CMS** | Count-Min Sketch | Probabilistic frequency estimator (used for trending / Top-K) |
| **LSH** | Locality-Sensitive Hashing | Hash function family that maps similar inputs to same bucket (for ContentID matching) |
| **ANN** | Approximate Nearest Neighbour | Sub-linear vector similarity search (for semantic search, recommendations) |
| **BM25** | Best Match 25 | Classic information-retrieval scoring (used by Elasticsearch) |
| **CDC** | Change Data Capture | Reading DB changes (Postgres WAL via Debezium, Spanner change streams) |
| **WAL** | Write-Ahead Log | Append-only commit log (Postgres, Bigtable) |
| **ACID** | Atomicity, Consistency, Isolation, Durability | Strong-consistency guarantees |
| **BASE** | Basically Available, Soft-state, Eventual consistency | The eventual-consistency cousin of ACID |
| **CAP** | Consistency, Availability, Partition-tolerance | Brewer's distributed-systems trilemma |
| **DLQ** | Dead Letter Queue | Topic for messages a consumer can't process |
| **RF** | Replication Factor | Number of copies of each data partition |
| **CAS** | Compare-and-Swap | Conditional update primitive |
| **UUID** | Universally Unique Identifier | 128-bit id (UUIDv7 = time-sortable) |

### 23.3 Engineering / networking

| Acronym | Full form | Meaning |
|---|---|---|
| **HLD** | High-Level Design | This document |
| **API / REST / gRPC / RPC** | Standard API styles | REST = HTTP+JSON; gRPC = HTTP/2+protobuf |
| **HTTP / HTTPS** | HyperText Transfer Protocol / Secure | Web request protocol |
| **TLS** | Transport Layer Security | Encryption-in-transit |
| **JWT** | JSON Web Token | Signed token carrying user identity |
| **OAuth2 / OIDC** | Open Authorization 2.0 / OpenID Connect | Auth-token framework |
| **CDN** | Content Delivery Network | Geographically-distributed cache for static content |
| **LB** | Load Balancer | Traffic distributor across pods |
| **WAF** | Web Application Firewall | Application-layer attack filter |
| **DDoS** | Distributed Denial of Service | Mass-traffic attack pattern |
| **GFE** | Google Front End | Google's edge load-balancer / TLS terminator |
| **TUS** | Tus.io resumable upload protocol | Open-source resumable-chunked-upload spec |
| **KMS** | Key Management Service | Cloud-managed encryption-key store |
| **HSM** | Hardware Security Module | Tamper-resistant on-prem key store |
| **ASN** | Autonomous System Number | Identifies an ISP for routing purposes |
| **BGP** | Border Gateway Protocol | Internet routing protocol |
| **MaxMind** | (commercial product) | IP → geolocation database |

### 23.4 ML / recommendation / search

| Acronym | Full form | Meaning |
|---|---|---|
| **DNN** | Deep Neural Network | Multi-layer neural network |
| **CTR** | Click-Through Rate | Fraction of impressions that result in clicks |
| **MAR** | Maximal Marginal Relevance | Diversity-promoting re-ranking algorithm |
| **A/B** | A / B test | Split-traffic experiment |
| **RAG** | Retrieval-Augmented Generation | LLM with retrieval (not used here but commonly confused) |
| **LSH** | Locality-Sensitive Hashing | (also in storage) |
| **TF / TF-Serving / Vertex AI** | TensorFlow / serving framework / Google's ML platform | ML runtime |
| **HNSW / FLAT** | Vector index algorithms | HNSW is hierarchical, FLAT is brute-force |

### 23.5 Reliability / operations

| Acronym | Full form | Meaning |
|---|---|---|
| **DR** | Disaster Recovery | Plan to recover from regional-scale disasters |
| **HA** | High Availability | Resilience to small in-region failures |
| **RTO** | Recovery Time Objective | "How long can we be down?" |
| **RPO** | Recovery Point Objective | "How much data loss can we accept?" |
| **MTTR** | Mean Time To Recover | Measured average recovery time |
| **SLO / SLA / SLI** | Service Level Objective / Agreement / Indicator | Target / external commitment / measurement |
| **p50 / p95 / p99** | Latency percentiles | 50th / 95th / 99th percentile of latency distribution |
| **k8s** | Kubernetes | Container orchestrator |
| **CI / CD** | Continuous Integration / Deployment | Build & deploy automation |
| **SRE** | Site Reliability Engineering | The Google-coined ops discipline |

### 23.6 Compliance / regulation

| Acronym | Full form | Meaning |
|---|---|---|
| **DMCA** | Digital Millennium Copyright Act | US copyright takedown framework |
| **DSA** | Digital Services Act | EU online-platform regulation |
| **GDPR** | General Data Protection Regulation | EU privacy law (right-to-be-forgotten, right-to-portability) |
| **CCPA** | California Consumer Privacy Act | California privacy law |
| **COPPA** | Children's Online Privacy Protection Act | US kids-privacy law (under 13) |
| **NetzDG** | Netzwerkdurchsetzungsgesetz | German hate-speech law |
| **PII** | Personally Identifiable Information | Data identifying a real person |
| **CSAM** | Child Sexual Abuse Material | Illegal content; PhotoDNA + NCMEC mandatory reporting |
| **NCMEC** | National Center for Missing & Exploited Children | US org receiving CSAM reports |
| **PhotoDNA** | (Microsoft tool) | Perceptual hash for CSAM detection |

### 23.7 Push / notification / mobile

| Acronym | Full form | Meaning |
|---|---|---|
| **FCM** | Firebase Cloud Messaging | Google's push-notif service for Android |
| **APNS** | Apple Push Notification Service | Apple's push-notif service for iOS |
| **SES** | (AWS) Simple Email Service | Bulk transactional email |

### 23.8 Internal-to-this-doc

| Acronym | Full form | Where defined |
|---|---|---|
| **TTFF** | Time To First Frame | §5 — the watch-start latency budget |
| **ContentID** | YouTube's copyright fingerprint engine | §17.2 — audio + video fingerprint matching |
| **HLD diagram** | The companion drawio | [`assets/06-design-youtube-hld.drawio`](./assets/06-design-youtube-hld.drawio) |
| **Outbox pattern** | Atomic DB-write + Kafka-publish via outbox table | §9.9 |
| **GOP-distributed encoding** | Splitting a video into N GOPs and encoding each in parallel | §10.3 |

---

## Related Material in This Repo

- **[05-DesignOTTPlatform.md](./05-DesignOTTPlatform.md)** — the Netflix shape (curated, DRM-mandatory, licensed catalogue). Read both — together they cover the two extremes of the "design a video platform" problem space.
- **[04-StockBrokingPlatform.md](./04-StockBrokingPlatform.md)** — the WebSocket fan-out + outbox + exchange-side-replay patterns generalise to live-chat + comment events.
- **[03-HighPrecisionDistributedJobScheduler.md](./03-HighPrecisionDistributedJobScheduler.md)** — premieres / scheduled-publish / live-go-live timer flows are job-scheduler-flavoured.
- **[07-SystemDesignAlgorithms/01-RateLimitingAlgorithms.md](../07-SystemDesignAlgorithms/01-RateLimitingAlgorithms.md)** — token-bucket per-user / per-IP / per-API-key.
- **[07-SystemDesignAlgorithms/02-HashingAndPartitioning.md](../07-SystemDesignAlgorithms/02-HashingAndPartitioning.md)** — sharding by `video_id` / `user_id`.
- **[07-SystemDesignAlgorithms/05-CountMinSketchAndHLL.md](../07-SystemDesignAlgorithms/05-CountMinSketchAndHLL.md)** — HLL for unique-viewer; CMS for trending Top-K.
- **[07-SystemDesignAlgorithms/10-ConsensusAndLeadership.md](../07-SystemDesignAlgorithms/10-ConsensusAndLeadership.md)** — etcd-based active-standby for live ingest.
- **[08-CommonProblems/03-HotKeysAndHotPartitions.md](../08-CommonProblems/03-HotKeysAndHotPartitions.md)** — viral video hot-row mitigation.
- **[08-CommonProblems/05-RetryStormsAndCircuitBreakers.md](../08-CommonProblems/05-RetryStormsAndCircuitBreakers.md)** — back-pressure on Recommendation under burst.
- **[08-CommonProblems/06-IdempotencyAndDeduplication.md](../08-CommonProblems/06-IdempotencyAndDeduplication.md)** — the `idempotency_key` contract on uploads + comments.
- **[08-CommonProblems/08-DistributedLocksAndLeases.md](../08-CommonProblems/08-DistributedLocksAndLeases.md)** — live-ingest active/standby lease.
- **[08-CommonProblems/21-CellBasedAndShuffleSharding.md](../08-CommonProblems/21-CellBasedAndShuffleSharding.md)** — Spanner shard isolation; one shard's tantrum doesn't break the others.
- **[Components/](../Components/)** — Kafka, Spanner, Bigtable, Redis, ClickHouse deep-dives.
