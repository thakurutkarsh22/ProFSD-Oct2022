# Design an OTT Platform (Netflix / Amazon Prime Video / Disney+ Hotstar)

> **Difficulty:** Hard &nbsp;|&nbsp; **Frequency:** ★★★★★ &nbsp;|&nbsp; **Companies:** Netflix, Amazon Prime Video, Disney+ Hotstar, JioCinema, SonyLIV, Zee5, HBO Max, Hulu, Apple TV+, Peacock, YouTube Premium, ALTBalaji, Voot, MX Player, ESPN+, Paramount+.
>
> **Real-world analogues:** subscription video-on-demand (SVOD: Netflix, Prime), advertising-supported VOD (AVOD: JioCinema free tier, MX Player, Tubi), live-streaming (Hotstar IPL, JioCinema IPL, ESPN+ live sports), short-form vertical video (Instagram Reels, YouTube Shorts shape), TVOD/EST (rent / buy on Apple TV / Amazon), and the "watch-party" / co-streaming use-case overlaid on top.

> **A note on scope.** This document designs the **OTT platform** end-to-end: ingestion (studio master → encoded ladder → packaged segments), delivery (CDN + DRM + adaptive bitrate streaming), playback (manifest, license, segments, watch-progress, concurrent-stream limits), live (low-latency HLS / DASH for sports), recommendation (offline embeddings + online ranker), and the user / subscription / billing surface on top. It does **not** design the encoder appliances themselves (FFmpeg / x264 / x265 are off-the-shelf), the CDN PoP infrastructure (we *use* Akamai / CloudFront / Cloudflare / Open Connect — we don't build them), or the DRM cryptographic primitives (Widevine / PlayReady / FairPlay are licensed standards). The line is: **we own the pipeline that feeds them and the metadata about them.**

> **Acronym reference.** OTT, ABR, HLS, DASH, LL-HLS, CMAF, DRM, PSSH, KID, EME, MSE, CDN, QoE, CMCD — every acronym is expanded in [§22 — Acronyms & Abbreviations Cheat-Sheet](#22-acronyms--abbreviations--cheat-sheet) with a one-line meaning and a forward-link to the deep-dive.

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Clarifying Questions](#2-clarifying-questions-always-ask-these-first)
3. [Requirements (FR + NFR)](#3-requirements)
4. [Capacity Estimation](#4-capacity-estimation-back-of-the-envelope)
5. [The Latency Budget — Play-Start (TTFP)](#5-the-latency-budget--play-start-ttfp)
6. [Why *Not* Just MP4 + S3 / Skip CDN / Single-Bitrate](#6-why-not-just-mp4--s3--skip-cdn--single-bitrate)
7. [Data Model](#7-data-model)
8. [High-Level Architecture (HLD)](#8-high-level-architecture-hld)
9. [Component Deep-Dives](#9-component-deep-dives)
10. [Video Ingest & Transcoding Pipeline](#10-video-ingest--transcoding-pipeline)
11. [End-to-End Flows](#11-end-to-end-flows)
12. [CDN, Multi-CDN & Edge Caching Strategy](#12-cdn-multi-cdn--edge-caching-strategy)
13. [DRM, License Issuance & Content Protection](#13-drm-license-issuance--content-protection)
14. [Recommendation, Personalization & Search](#14-recommendation-personalization--search)
15. [Live Streaming (LL-HLS / LL-DASH / WebRTC)](#15-live-streaming-ll-hls--ll-dash--webrtc)
16. [Subscription, Billing & Concurrent-Stream Limits](#16-subscription-billing--concurrent-stream-limits)
17. [Edge Cases & Gotchas](#17-edge-cases--gotchas)
18. [Security, DRM Compliance, Geo-Restrictions](#18-security-drm-compliance-geo-restrictions)
19. [Observability & QoE Telemetry](#19-observability--qoe-telemetry)
20. [Technology Choices — Final Verdict](#20-technology-choices--final-verdict)
21. [Q&A Defense — Top 25 Tough Interview Questions](#21-qa-defense--top-25-tough-interview-questions)
22. [Acronyms & Abbreviations — Cheat-Sheet](#22-acronyms--abbreviations--cheat-sheet)

---

## 1. Problem Statement

Design an **over-the-top video streaming platform** that lets hundreds of millions of users browse a catalog of movies, TV shows, originals, and live events, and play them on **any device** (phone, tablet, web, smart TV, game console, set-top box) with **broadcast-grade quality of experience** — start within 2 seconds, never rebuffer, automatically adapt to the user's bandwidth, work offline (downloads), respect content licensing windows, enforce the user's subscription plan, and pay rights-holders accurately based on what was watched.

**Concrete SLOs:**

- **Concurrency (peak):** **~50 M concurrent streams** during a marquee live event (Hotstar IPL final, Netflix global premiere); **~5–10 M concurrent VOD** at typical evening peak.
- **Catalog size:** ~50 K titles × ~5 ABR ladder rungs × ~10-second segments × ~2 h average duration ≈ **~200 M segment files** at rest, ~5 PB packaged storage (before regional replication).
- **Throughput (egress):** sustained **~50–100 Tbps** during peak; **~250 Tbps** during a global live final. (Reference: 2023 IPL final on JioCinema peaked at ~32 M concurrent → ~25 Tbps.)
- **Latency targets:**
  - **Time-to-first-frame (TTFF) p95 ≤ 2 s** from "user taps Play" to "first decoded frame on screen".
  - **Glass-to-glass live latency p95 ≤ 5 s** for sports (LL-HLS / LL-DASH); ≤ 10 s acceptable for non-time-sensitive live.
  - **Rebuffering ratio < 0.4 %** (time spent rebuffering / total play time).
  - **Catalog browse p95 ≤ 200 ms** for the home page.
- **Durability:** master mezzanine files (the studio's source) → 11 nines on S3 + cold archive; packaged ABR segments → recoverable by re-running the pipeline (so they live on cheaper standard storage with replication).
- **Availability:** **99.99%** for browse + playback (a 4-min annual budget — viewers tolerate almost zero downtime); **99.999%** for the "play next episode" / "resume watching" path during peak.
- **Consistency contract:**
  - **Watch-progress, watchlist** → eventually consistent across devices (~ a few seconds is fine; we can't afford a synchronous round-trip on every 10-second heartbeat).
  - **Subscription state, concurrent-stream count, DRM license** → strongly consistent (a downgraded user must be cut off; a sub on the maximum allowed devices must not get a 5th license).
  - **Recommendation rows** → eventually consistent (refreshed hourly / daily; no SLO).
  - **Catalog metadata** → strongly consistent on writes (editorial team), eventually replicated to read replicas/edge caches with a TTL.

> The interview-defining tension: **a 50 M concurrent live audience cannot be served from origin** (would need ~250 Tbps from one DC); the design has to fan out through multi-CDN with origin-shielding while still enforcing **per-session DRM**, **per-user concurrent-stream limits**, and **regional content licensing** — all on the critical path of "play". Every design decision below trades against one of: latency tail, CDN egress cost, DRM correctness, licensing compliance.

---

## 2. Clarifying Questions (always ask these first)

Score points by asking these *before* drawing boxes:

1. **VOD only, or VOD + Live + Downloads?** → *All three. VOD is the bulk of traffic; Live is the burst (IPL, Oscars, GoT premiere); downloads are the "offline on a flight" use-case with TTL'd DRM.*
2. **Geographic scope?** → *Global. ~190 countries. Different content licensing windows per country (a Marvel film may be on Netflix US but on Disney+ India). Geo-restriction at manifest + license layer.*
3. **What ABR formats?** → *HLS for Apple/iOS/Safari; DASH for Android/web/CTV; both packaged from a **single CMAF source** to halve storage. (See §10.)*
4. **DRM?** → *Yes, mandatory. Widevine for Android/Chrome, PlayReady for Edge/Xbox/CTV, FairPlay for Apple. Studios contractually require it for premium content.*
5. **What devices?** → *iOS, Android, Web (HTML5 + EME/MSE), Smart TVs (Tizen/WebOS/Android TV/Roku/Fire TV), Apple TV, game consoles. ~30 platform combinations; we own the player on each.*
6. **What does "concurrent users" mean?** → *Concurrent **active player sessions**, not just logged-in. A user paused on the home screen is not concurrent. The DRM license issuance count is the source of truth.*
7. **Subscription tiers?** → *Basic (1 device, 720p, ads), Standard (2 devices, 1080p), Premium (4 devices, 4K HDR, no ads). Plan dictates max bitrate the manifest exposes + concurrent stream count.*
8. **Ads (AVOD) or pure SVOD?** → *Hybrid. Premium tier is ad-free; basic tier has SSAI (server-side ad insertion) — ads stitched into the manifest at the origin packager so client can't block them.*
9. **Do we own the encoder farm or use AWS MediaConvert?** → *Hybrid. MediaConvert / managed for catch-up VOD (cost-optimised, slower); on-prem GPU farm for high-priority titles + live transcoding (latency-optimised).*
10. **How much master content arrives per day?** → *Studios deliver ~50–100 new title-hours/day; 4K HDR masters at ~100 Mbps = 45 GB/h. Ingestion DAG is its own scaling axis (independent of playback).*
11. **Live latency target?** → *5 s glass-to-glass for sports (LL-HLS); 30 s acceptable for cricket commentary delay tolerated. We are NOT designing a 200 ms WebRTC interactive system here.*
12. **Recommendation freshness?** → *Two layers: hourly batch refresh of "Top 10", "Trending Now", and per-user rows; real-time on-the-fly re-ranking based on the last few clicks within a session.*
13. **Multi-profile per account?** → *Yes. One subscription = up to 5 profiles (kids, adults). Each profile has its own watchlist, history, recommendations, parental controls.*
14. **Offline downloads — how is licensing handled?** → *Per-device DRM license with a TTL (typically 30 days from download, 48 h from first play). Encrypted segments stored on device; license bound to device-id.*
15. **Geo-fencing — IP-based or account-based?** → *Both. Account home-region locks subscription pricing; IP-based geo on every play decides which catalog window is allowed (handle the "VPN traveller" case by setting `licensing_country = max(ip_country, account_country, locked_country)` per title).*
16. **What about content delivery in low-bandwidth markets (India tier-3)?** → *240p / 360p ABR rungs; aggressive caching at telco edge (Jio / Airtel point-of-presence); pre-positioning of popular content; data-saver mode in app.*
17. **CDN strategy?** → *Multi-CDN (Akamai + CloudFront + Cloudflare + Limelight + own Open-Connect-style appliances at ISP), with real-time switching based on QoE. No single-CDN dependency for marquee events.*
18. **Recommendation cold-start (new user)?** → *Onboarding survey (3 favourite genres + ratings) + popularity-based fallback row + collaborative-filtering kicks in after ~10 plays.*

---

## 3. Requirements

### 3.1 Functional

- **F1.** **Catalog browse** — `GET /home`, `GET /title/{id}`, `GET /search?q=`. Browse-by-genre, search, "More like this", continue watching, watchlist, top 10.
- **F2.** **Authentication & profiles** — email / phone / SSO (Google, Apple, Facebook). 5 profiles per account; kid profiles enforce parental gates.
- **F3.** **Subscription** — pick a plan (Basic / Standard / Premium), pay (Stripe / Razorpay / iOS IAP / Google Play Billing / Amazon IAP), cancel anytime, upgrade/downgrade, free trial, family plan.
- **F4.** **Playback** — `POST /play/{title_id}` returns a signed manifest URL + DRM license server URL + license challenge. Player adapts bitrate on the fly. Resume from last watched position. Skip intro / next episode / recap.
- **F5.** **Live** — schedule of upcoming live events; `POST /live/{event_id}/play` for live entry; **DVR rewind window** of 4 h (so a viewer joining late can scrub back to the start of the match).
- **F6.** **Watch-progress** — every 10 s the player heartbeats `(profile_id, title_id, position_ms, duration_ms)`. Stored in Cassandra; consumed by "continue watching" + recommendations.
- **F7.** **Watchlist & history** — add/remove from watchlist, viewing history (last 90 days hot, lifetime archived).
- **F8.** **Downloads** — pre-cache a title's encrypted segments on the device + a per-device DRM license valid 30 d / 48 h after first play.
- **F9.** **Recommendations** — at least 12 personalized rows on home (Top 10, Continue Watching, Trending in your country, Because you watched X, Top Picks, New Releases, Genre rows).
- **F10.** **Search** — full-text + tolerant (typo, partial), multi-language, voice (mobile / TV remote).
- **F11.** **Multi-language audio + subtitles** — pick from N audio tracks + M subtitle tracks at any time during playback (no replay).
- **F12.** **Notifications** — new-release alerts, "you left off at episode 3", subscription expiry, billing failure. FCM / APNS / email / in-app.
- **F13.** **Concurrent stream enforcement** — Premium = 4, Standard = 2, Basic = 1. The 5th request is rejected with "max devices reached".
- **F14.** **Parental controls / kids profile** — locked PIN, restricted catalog (G-rated only), no recommendations from adult titles.
- **F15.** **Studio content management** — internal portal for editorial: upload masters, edit metadata, scheduling, regional rights windows, release calendar.
- **F16.** **Live event ingest** — production truck pushes RTMP / SRT to ingest gateway; transcoded into 5-rung live ABR ladder; packaged as LL-HLS + LL-DASH; pushed to multi-CDN.
- **F17.** **Ads (AVOD)** — server-side ad stitching for the basic tier (SSAI) + opportunity to upsell to ad-free at every ad break.
- **F18.** **Reports** — viewership reports (per-title, per-region, per-day) for studio rights-holders; royalty calculation; revenue-share statements.

### 3.2 Non-Functional

| Attribute             | Target                                                                                                |
|-----------------------|-------------------------------------------------------------------------------------------------------|
| Concurrent streams (live peak)  | **~50 M** during marquee live (IPL final, global premiere)                                  |
| Concurrent streams (VOD avg)    | **~5–10 M** evening peak                                                                    |
| Throughput (egress)             | ~50–100 Tbps avg peak; **~250 Tbps live peak**                                              |
| Catalog size                    | ~50 K titles · ~200 M segment files at rest · ~5 PB hot                                     |
| Master library size             | ~5 PB (mezzanine) + ~30 PB (packaged ABR variants across all rungs and DRM bundles)          |
| **TTFF (play-start)**           | **p95 ≤ 2 s** mobile; ≤ 1 s TV (already on home screen)                                     |
| Glass-to-glass live latency     | **p95 ≤ 5 s** (LL-HLS, sports); ≤ 10 s acceptable for non-time-critical                     |
| Rebuffering ratio               | **< 0.4 %**                                                                                 |
| Search latency                  | p95 ≤ 200 ms                                                                                |
| Recommendation refresh          | hourly batch + per-session real-time re-rank                                                 |
| Watch-progress persistence      | RPO ≤ 10 s (one heartbeat); RTO 0 (stateless reads from Cassandra LOCAL_ONE)                |
| Availability (browse + play)    | **99.99%** ; **99.999%** during peak / live                                                  |
| Consistency                     | Subscription / DRM / concurrent-stream counter → strong; watch-progress / recommendations → eventual |
| Geo / licensing                 | Per-title regional licensing windows; geo-IP at manifest + license; VPN-aware                |
| DRM                             | Widevine + PlayReady + FairPlay; per-session license; HDCP enforcement for 4K               |
| Multi-CDN                       | At least 3 CDNs active; real-time QoE-driven switching; origin-shielded                     |
| Encoding ladder                 | 5–7 rungs (240p, 360p, 480p, 720p, 1080p, 4K, 4K-HDR); CMAF source, packaged twice (HLS / DASH) |
| DR                              | Multi-region active-active for control plane; multi-CDN for data plane; mezzanine in 3 regions |
| Compliance                      | DMCA / CCPA / GDPR / India IT Rules 2021; PII encrypted at rest (KMS); studio audit (Sigma OTT) |

---

## 4. Capacity Estimation (back of the envelope)

Let's pin down the working numbers we'll quote in the rest of the doc. **Assumption: ~250 M registered users globally, ~50 M MAU, ~10 M DAU (peak hour 5–7 M concurrent).**

| Metric                                          | Calculation                                       | Value                              |
|-------------------------------------------------|---------------------------------------------------|------------------------------------|
| Registered users                                | stated                                            | **250 M**                          |
| MAU                                             | ~20% of registered                                | **50 M**                           |
| DAU                                             | ~20% of MAU                                       | **10 M**                           |
| Avg minutes/day per DAU                         | ~90 min                                           | 90 min                             |
| Avg concurrent streams (typical peak)           | 10M × 90 min ÷ (24h × 60) × peak-factor (3×)      | **~5–7 M concurrent**              |
| **Peak concurrent (live final, e.g. IPL)**      | empirical                                         | **~50 M concurrent**               |
| Avg bitrate (mix of 480p–4K)                    | weighted: 60% 720p (3 Mbps), 30% 1080p (5 Mbps), 10% 4K (15 Mbps) | **~5 Mbps**           |
| **Egress (steady)**                             | 7 M × 5 Mbps                                      | **~35 Tbps**                       |
| **Egress (live peak)**                          | 50 M × 5 Mbps                                     | **~250 Tbps**                      |
| Catalog titles                                  | ~50 K                                             | 50 K                               |
| Avg duration / title                            | ~2 h (movies + episodes)                          | 2 h                                |
| Segments per title (HLS, 6-s segments)          | 2 h × 3600 s ÷ 6 s × 5 rungs × 2 packagers (HLS + DASH) | **~12 K segs/title**         |
| **Total segments at rest**                      | 50 K × 12 K                                       | **~600 M segments**                |
| Avg segment size                                | 6 s × 5 Mbps ÷ 8                                  | ~4 MB                              |
| **Packaged storage**                            | 600 M × 4 MB                                      | **~2.4 PB** packaged               |
| Mezzanine masters (4K HDR)                      | 50 K × 2 h × 100 Mbps × 3600 ÷ 8                  | ~4.5 PB                            |
| Watch-progress events                           | 7 M × 1 hb/10s = 700 K/s avg, 5 M/s live peak     | ~700 K/s avg                       |
| Watch-progress storage                          | 700 K/s × 100 B × 86400 × 30d                     | ~180 TB / 30 d                     |
| Cassandra cluster (watch progress)              | RF=3, 2 PB hot                                    | ~80 nodes                          |
| Catalog DB (Postgres)                           | ~50 K titles × ~10 KB metadata + 200 M items in catalog row state | ~20 GB           |
| Search index (Elasticsearch)                    | 50 K titles × ~50 KB enriched doc                  | ~5 GB                              |
| Recommendation feature store                    | 50 M users × ~1 KB feature vector                  | ~50 GB                             |
| DRM license issuance rate                       | 10 M plays/day × peak factor 3                    | ~350 / s avg, **3 K/s peak**        |
| Concurrent stream Redis cluster                 | 10 M users × ~32 B counter                         | ~300 MB (in-memory)                |
| Encoding farm (per-title throughput)            | 4K HDR 2-h master ≈ 8 GPU-hours (with x265)        | ~50 K GPU-hrs/yr                   |
| Kafka partitions (`viewing.events`)             | peak 5 M/s ÷ ~20 K/s/partition                     | **~256 partitions**                |
| Kafka partitions (`playback.heartbeat`)         | peak 1 M/s ÷ ~20 K/s/partition                     | **~64 partitions**                 |
| CDN cache footprint per PoP                     | working set ~5 % of catalog ≈ 250 GB hot           | ~250 GB / PoP                      |
| Multi-CDN regional caches                       | ~100 PoPs × 5 CDNs                                 | ~500 cache locations               |
| Open-Connect-style ISP appliances               | empirical (Netflix-style)                          | ~15 K appliances globally          |

The numbers are not exotic until you reach the **live peak** — *250 Tbps* is six times the entire global Netflix steady-state. That's the design pressure: you need a CDN strategy that can absorb **2-orders-of-magnitude bursts** without over-provisioning the rest of the year. Multi-CDN + origin-shielding + ISP appliances is how it's actually done.

---

## 5. The Latency Budget — Play-Start (TTFP)

**Total budget: 2 s p95 from "tap Play" to "first frame on screen".** Spend it explicitly:

| Stage                                                     | Target  | p95    | Notes                                                                 |
|-----------------------------------------------------------|---------|--------|-----------------------------------------------------------------------|
| Tap → API GW (TLS, mobile RTT)                            | 50 ms   | 200 ms | Edge close to user — POPs in every metro                              |
| API GW: TLS, JWT validate, geo-check                      | 5 ms    | 20 ms  | Token cached 1 m; geo-IP looked up from MaxMind in-process            |
| Playback Svc: subscription + concurrent-stream check       | 10 ms   | 30 ms  | Redis HINCRBY `streams:{user}` (atomic CAS to enforce N-stream limit) |
| Manifest signing (HLS / DASH)                             | 5 ms    | 15 ms  | Pre-built manifest in S3; we just sign URL with short TTL (5 min)     |
| Player parses manifest, picks initial bitrate              | 100 ms  | 300 ms | Player-side (network round-trip not in our budget if cached locally)  |
| DRM license challenge → license server                    | 200 ms  | 400 ms | License generation + signing; license cached on device for session    |
| First segment fetch (init.mp4 + first media segment)       | 300 ms  | 600 ms | CDN cache hit ~95% at PoP; cold path adds 200 ms origin-shield        |
| Decoder warm-up + first decoded frame                     | 100 ms  | 200 ms | Hardware decoder init                                                 |
| **TOTAL p50 / p95**                                       | **~770 ms / ~1.8 s** |  | leaves ~200 ms headroom before the 2 s ceiling             |

> **Key insight — the CDN cache hit ratio is the single biggest knob.** A 90 % hit ratio means 10 % of users go to origin which is ~5–10× slower; this dominates the p95. **Pre-warming popular titles before a release** (push to all PoPs the night before) is non-negotiable. Likewise, the DRM license is the second-biggest spike — ~95 % of license requests can be served from a regional license cache (HMAC-signed time-bound JWTs); only ~5 % need the master license server.

### 5.1 What blows this budget in practice

- **CDN cold cache (rare title)** — adds 200–500 ms origin-shield round-trip; mitigation: pre-warm + intelligent pre-fetch based on "next episode" hint sent in metadata.
- **DRM license server cold path** — issuing a Widevine license involves an HSM signature; under bursty load (10 M users at IPL kickoff), the HSM becomes the bottleneck. Mitigation: **regional license replicas** (each region has its own signing identity certified by master), **license caching on device** for the session.
- **Manifest mis-routing** — if the user's home country isn't matched correctly, we serve wrong-region manifest and player retries. Mitigation: bake `country=` into the JWT at login + verify at manifest request.
- **TLS handshake on cold connection** — first ever connect is ~3 round-trips for TLS 1.2. Mitigation: TLS 1.3 + 0-RTT for return visitors + connection pre-warm during app launch.
- **Live event "thundering herd"** — 50 M users tap Play at 7:30 PM IST when the IPL match starts. Mitigation: **stagger** (jittered backoff), pre-stage the manifest at all PoPs 30 minutes before kickoff, and push a "warm-up" prefetch via push notification ("the match is about to begin").

---

## 6. Why *Not* Just MP4 + S3 / Skip CDN / Single-Bitrate

Interviewers will press on every short-cut. Honest comparison:

| Candidate shortcut                                          | What's tempting                       | Why it breaks at this scale                                                         |
|-------------------------------------------------------------|---------------------------------------|-------------------------------------------------------------------------------------|
| **One progressive MP4 per title (no ABR)**                   | simple, just an `<video src=…>` tag  | No bitrate adaptation: the user on 3G stalls; the user on 1 Gbps fibre wastes bandwidth on 480p. **One single bitrate** is what TV broadcast did; the internet wins by adapting per-segment. |
| **Stream directly from S3 (no CDN)**                         | one storage tier, no cache to invalidate | S3 does not have edge presence; egress from us-east-1 to a user in Mumbai is ~250 ms RTT and saturates at ~100 Gbps regional. **CDN is mandatory.** |
| **Single CDN (Akamai only)**                                 | one contract, one set of rules         | Akamai's PoP in Hyderabad goes down at 8 PM during IPL → 30 % of viewers stall. **Multi-CDN is the difference between a 4-min-incident and a Twitter trending day.** |
| **Encode once at 4K and let player downsample**             | save CPU on encoding                   | Bandwidth doesn't downsample — the user on 3G still pulls 50 Mbps. ABR is **mandatory** for any non-trivial scale. |
| **No DRM (rely on signed URLs)**                             | simpler stack                          | Studios contractually require **L1 Widevine** for premium content. Without DRM, you can't license Marvel / HBO / Disney. The license isn't optional. |
| **Compute recommendations on every page-load**               | always fresh                           | 10 M home-page loads × ~50 ms ranker call × ~50 KB feature vector = 25 GB/s of memory bandwidth. Pre-compute hourly into Redis → 5 ms read. |
| **One Postgres for catalog + watch-progress + analytics**    | one source of truth                    | Watch-progress is 700 K/s writes — Postgres WAL chokes. Use Cassandra/DynamoDB for high-write streams; Postgres for the low-write catalog. |
| **No regional licensing — global catalog**                   | simpler                                | Studios sell rights region-by-region for $$$. A title may be on Netflix US but not Netflix India. **Per-region catalog** is the only legal way to operate. |
| **Live = same pipeline as VOD**                              | one codepath                           | VOD is *latency-tolerant; cache-friendly* (a movie is the same for everyone). Live is *latency-sensitive; cache-hostile* (segments are minted in real time, must propagate in seconds). They share components but the pipelines are distinct. |
| **Build our own CDN**                                        | save licensing fees                    | A global CDN is 10 K nodes in 100 countries, $1B+ infra. Buy from Akamai / Cloudflare / CloudFront; *augment* with Open-Connect-style ISP appliances for the long-tail of cheap egress. |
| **No SSAI — let client play ad files**                       | simpler ad pipeline                    | Ad-blockers strip them out. SSAI stitches the ad **into the same manifest** so the client cannot tell content from ad — ad views are recoverable. |
| **No concurrent-stream limit, just trust the user**          | nice user experience                   | Account sharing destroys revenue. Netflix lost ~$3 B/yr to it. **Hard limit + soft eviction** (kick the oldest stream, with prompt) is the industry norm. |

### Final architectural decisions (consequence of the above)

- **Source of truth:** **CMAF mezzanine** (single CMAF source per ABR rung) on **S3** with multi-region replication; packaged twice (HLS playlist + DASH manifest) over the same media segments via [CMAF compatibility].
- **Catalog DB:** **Postgres** (multi-region, read replicas at edge, write-through cache).
- **Watch progress / events:** **Cassandra** (per-region, RF=3, eventual consistency across regions); **Kafka** for change feed into recommendation pipeline.
- **Hot cache:** **Redis** for sessions, manifest URL signatures, concurrent-stream counters, license cache, search-suggestion cache, "Top 10" rows.
- **Async backbone:** **Kafka** with topics `viewing.events`, `playback.heartbeat`, `play.start`, `play.complete`, `cdn.qoe`, `recommendations.feedback`, `subscription.events`. RF=3, idempotent producer.
- **Real-time analytics:** **Apache Flink** consuming Kafka → **ClickHouse** / **Druid** for QoE dashboards; **Apache Spark** for nightly batch (recommendation training, royalty calc, retention metrics).
- **Multi-CDN:** **Akamai + CloudFront + Cloudflare + Limelight + Open-Connect-style ISP appliances**. Real-time switching via QoE telemetry. Origin-shielded with regional shield clusters.
- **DRM:** **Widevine** (Android, Chrome) + **PlayReady** (Edge, Xbox, CTV) + **FairPlay** (Apple). Common encryption (CENC) at packaging so a single ciphertext can be played by all three; **per-DRM license server**; **regional license replicas** with master HSM in primary region.
- **Recommendation:** offline pipeline (Spark, weekly model retrain) → online ranker (TensorFlow Serving / TorchServe) + per-session real-time re-rank.
- **Live:** RTMP/SRT ingest → live transcode (NVIDIA NVENC GPU farm) → packager → multi-CDN with **origin-shielded LL-HLS / LL-DASH** at ~5 s glass-to-glass.

---

## 7. Data Model

### 7.1 `Title` (the catalog row — the editorial team's truth)

```
Title {
  string  title_id              // UUIDv7
  string  type                  // 'MOVIE' | 'SERIES' | 'EPISODE' | 'LIVE_EVENT'
  string  parent_title_id       // for episodes -> series_id; null for top-level
  int     season_number         // for episodes
  int     episode_number
  string  name
  string  synopsis
  string  language_primary      // 'en', 'hi', 'es', ...
  string[] languages_audio       // available audio tracks
  string[] languages_subtitle
  int     duration_sec
  string  rating                // 'G', 'PG', 'PG-13', 'R', 'NR'
  string[] genres                // 'drama', 'thriller', 'comedy', 'sport'
  string[] cast
  string[] directors
  date    release_date
  string  poster_url            // CDN-hosted
  string  backdrop_url
  string  trailer_video_id      // points to a separate (smaller) Title row
  jsonb   metadata              // flex blob: imdb_id, awards, content_advisory, etc.
  // Provenance / pipeline
  string  ingest_status         // 'PENDING' | 'TRANSCODING' | 'PACKAGED' | 'PUBLISHED' | 'WITHDRAWN'
  string  master_s3_uri         // mezzanine
  // Compliance / licensing
  string[] available_countries   // ISO-3166 codes; if empty, global
  long    license_window_start_ms
  long    license_window_end_ms
  bool    requires_premium      // 4K HDR / latest title only on Premium
  // Audit
  long    created_at_ms
  long    updated_at_ms
  long    published_at_ms
}
```

### 7.2 `VideoAsset` (the encoded variants — populated by the pipeline)

```
VideoAsset {
  uuid    asset_id
  string  title_id
  string  variant               // 'HLS' | 'DASH'
  string  manifest_s3_uri       // master playlist / MPD
  string  manifest_url          // CDN-distributable URL (signed at play-time)
  jsonb   ladder                // [{ rung: '1080p', bitrate_kbps: 5000, codec: 'h264', segments: 1200 }, ...]
  string  cmaf_init_uri         // shared init segment (CMAF)
  string  drm_widevine_pssh
  string  drm_playready_pssh
  string  drm_fairplay_pssh
  string  encryption_kid        // hex key id (16 bytes)
  long    bytes_total
  long    duration_ms
  string  codec_video           // 'h264' | 'h265' | 'av1' | 'vp9'
  string  codec_audio           // 'aac' | 'eac3' | 'opus'
  long    packaged_at_ms
}
```

### 7.3 `User` and `Profile`

```
User {
  uuid    user_id
  string  email
  string  phone
  string  password_hash         // bcrypt
  string  country_home          // billing country, geo-locked at signup
  string  language_pref
  string  signup_source         // 'web' | 'ios' | 'android' | 'tv'
  long    created_at_ms
  long    last_login_at_ms
}

Profile {                        // 1 user → up to 5 profiles
  uuid    profile_id
  string  user_id
  string  name
  string  avatar_url
  bool    is_kid
  string  pin_hash              // optional, for adult profiles in family setup
  string  language_pref
  long    created_at_ms
}
```

### 7.4 `Subscription` and `ConcurrentStream`

```
Subscription {
  uuid    subscription_id
  string  user_id
  string  plan                  // 'BASIC' | 'STANDARD' | 'PREMIUM'
  int     max_devices           // 1, 2, 4
  int     max_resolution_p      // 720, 1080, 2160
  bool    has_ads               // true for Basic
  string  status                // 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'PAST_DUE'
  long    current_period_start_ms
  long    current_period_end_ms
  string  payment_provider      // 'stripe' | 'razorpay' | 'apple_iap' | 'google_play'
  string  payment_provider_subscription_id
  long    created_at_ms
}

// Hot key: stored in Redis only, not Postgres (refreshed from license issuance)
ConcurrentStream {                // Redis: streams:{user_id} = ZSET (session_id, timestamp)
  string  user_id
  string  session_id
  string  device_id
  string  device_kind            // 'ios' | 'android' | 'web' | 'smart_tv' | 'console'
  string  title_id
  long    started_at_ms
  long    last_heartbeat_ms
}
```

### 7.5 `WatchProgress` (high-write, eventually consistent)

```
WatchProgress {                 // Cassandra: PRIMARY KEY ((profile_id), title_id)
  uuid    profile_id
  string  title_id
  long    position_ms
  long    duration_ms
  bool    completed             // true if position_ms >= 0.95 × duration_ms
  string  device_id_last
  long    updated_at_ms
}

ViewingEvent {                  // Cassandra append-only: PRIMARY KEY ((profile_id), event_ts_ms, event_id)
  uuid    profile_id
  uuid    event_id
  long    event_ts_ms
  string  title_id
  string  event_type            // 'PLAY_START' | 'PAUSE' | 'RESUME' | 'COMPLETE' | 'STOP' | 'BITRATE_CHANGE'
  long    position_ms
  string  device_id
  jsonb   payload               // bitrate, dropped_frames, rebuffer_ms, etc.
}
```

### 7.6 `RecommendationRow` (pre-computed)

```
RecommendationRow {             // Redis HSET: home:{profile_id} -> { row_id -> [title_id, ...] }
  uuid    profile_id
  string  row_id                // 'top_picks' | 'continue_watching' | 'because_you_watched_<title>'
  int     row_position          // ordering on home page
  string[] title_ids             // ranked
  long    generated_at_ms
  long    valid_until_ms        // TTL = next batch run + 1h
  string  algorithm_version
}
```

### 7.7 `LicenseSession` (the per-play license bookkeeping)

```
LicenseSession {                // Redis: license:{session_id}, TTL = title_duration + 1h
  uuid    session_id
  string  user_id
  string  profile_id
  string  title_id
  string  device_id
  string  drm_system            // 'widevine' | 'playready' | 'fairplay'
  string  kid                   // key id
  bytes   key_wrapped           // encrypted with device public key
  long    issued_at_ms
  long    expires_at_ms
  string  signing_cert_id
  string  policy_blob           // HDCP level, output protection, max resolution, etc.
}
```

### 7.8 Sample Postgres DDL — `titles` (the editorial table)

```sql
CREATE TABLE titles (
    title_id              UUID            PRIMARY KEY,
    type                  TEXT            NOT NULL CHECK (type IN ('MOVIE','SERIES','EPISODE','LIVE_EVENT','TRAILER')),
    parent_title_id       UUID                NULL REFERENCES titles(title_id) ON DELETE CASCADE,
    season_number         INTEGER             NULL CHECK (season_number IS NULL OR season_number > 0),
    episode_number        INTEGER             NULL CHECK (episode_number IS NULL OR episode_number > 0),
    name                  TEXT            NOT NULL,
    synopsis              TEXT                NULL,
    language_primary      TEXT            NOT NULL,                     -- ISO-639-1
    duration_sec          INTEGER         NOT NULL CHECK (duration_sec > 0),
    rating                TEXT                NULL,
    genres                TEXT[]          NOT NULL DEFAULT ARRAY[]::TEXT[],
    cast_                 TEXT[]          NOT NULL DEFAULT ARRAY[]::TEXT[],
    directors             TEXT[]          NOT NULL DEFAULT ARRAY[]::TEXT[],
    release_date          DATE                NULL,
    poster_url            TEXT                NULL,
    backdrop_url          TEXT                NULL,
    trailer_video_id      UUID                NULL,
    metadata              JSONB           NOT NULL DEFAULT '{}'::jsonb,

    -- Pipeline state
    ingest_status         TEXT            NOT NULL DEFAULT 'PENDING'
                                          CHECK (ingest_status IN ('PENDING','TRANSCODING','PACKAGED','PUBLISHED','WITHDRAWN')),
    master_s3_uri         TEXT                NULL,

    -- Licensing
    available_countries   TEXT[]          NOT NULL DEFAULT ARRAY[]::TEXT[],   -- empty = global
    license_window_start_ms BIGINT            NULL,
    license_window_end_ms   BIGINT            NULL,
    requires_premium      BOOLEAN         NOT NULL DEFAULT FALSE,

    created_at_ms         BIGINT          NOT NULL,
    updated_at_ms         BIGINT          NOT NULL,
    published_at_ms       BIGINT              NULL
);

CREATE INDEX titles_published_idx     ON titles (published_at_ms DESC) WHERE ingest_status='PUBLISHED';
CREATE INDEX titles_genre_gin         ON titles USING GIN (genres);
CREATE INDEX titles_country_gin       ON titles USING GIN (available_countries);
CREATE INDEX titles_parent_idx        ON titles (parent_title_id, season_number, episode_number);
```

### 7.9 Sample Cassandra schema — `watch_progress`

```cql
CREATE TABLE watch_progress (
    profile_id     uuid,
    title_id       text,
    position_ms    bigint,
    duration_ms    bigint,
    completed      boolean,
    device_id_last text,
    updated_at_ms  bigint,
    PRIMARY KEY ((profile_id), title_id)
) WITH default_time_to_live = 7776000      -- 90 d hot
  AND compaction = { 'class' : 'LeveledCompactionStrategy' };

-- Append-only events table (used for analytics + recommendation training)
CREATE TABLE viewing_events (
    profile_id    uuid,
    event_ts_ms   bigint,
    event_id      timeuuid,
    title_id      text,
    event_type    text,
    position_ms   bigint,
    device_id     text,
    payload       text,
    PRIMARY KEY ((profile_id), event_ts_ms, event_id)
) WITH CLUSTERING ORDER BY (event_ts_ms DESC, event_id DESC)
  AND default_time_to_live = 2592000;       -- 30 d hot; tiered to S3 for warm/cold
```

> **Why Cassandra and not Postgres?** Watch-progress is **700 K/s** sustained writes (10 s × 7 M concurrent), append-mostly, partitioned cleanly by `profile_id`. Cassandra absorbs that on a 30-node ring without breaking a sweat; Postgres would need painful sharding and the WAL would still bottleneck. The reads are also clean: "give me this profile's progress on this title" — single-partition.

---

## 8. High-Level Architecture (HLD)

**Editable source:** [`assets/05-ott-platform-hld.drawio`](./assets/05-ott-platform-hld.drawio). Open with the *Draw.io Integration* extension in Cursor / VS Code, or in [diagrams.net](https://app.diagrams.net/) (`File → Open from device`). Save-as-SVG to render inline on GitHub if needed (`File → Export As → SVG (with editable XML embedded)`).

> The diagram is laid out top-to-bottom along the **request flow**: clients at the top, multi-CDN edge + API GW + WS-push + Live-Ingest + Auth, then the core service row (Catalog / Search / **Playback ★ Gatekeeper** / Recommendation / Watchlist / Subscription / DRM-License / Notification / Live-Transcoder / Concurrent-Stream / Heartbeat). Kafka backbone in the middle. Pipeline-workers row (Workflow Orchestrator / Encoder Pool / Packager / QC / CDN-Pre-warmer / Multi-CDN-Router / Watch-Progress-Writer / Recommendation-Pipeline / Search-Indexer / Outbox). Storage plane below (S3 mezz · S3 packaged · Postgres · Cassandra · Elasticsearch · Redis · HSM · ClickHouse · etcd · Audit · Cold). Analytics + Observability + External (payment / studios / DRM CAs) at the bottom. The right-hand panels carry the **TTFF latency budget**, the **9 Key Invariants**, the **8 numbered colour-coded flows** (Browse · Play · Live · Heartbeat · Concurrent-stream · Ingest · Recommendation · Multi-CDN/Audit), the **DRM matrix**, and the **capacity & SLO summary**. Each step in §11 is annotated with the matching arrow id (e.g. `P3`, `I7`).
>
> The diagram is laid out top-to-bottom along the **request flow**: clients at the top, edge / API GW, core services row, async backbone (Kafka), the encoding pipeline on the left, the live ingest on the right, the analytics + recommendation pipeline at the bottom-right. The right-hand panels are the storage plane, the 9 invariants, the 8 numbered/colour-coded flows (Browse, VOD Play, Live Play, Watch Heartbeat, Concurrent-Stream Check, License Issuance, Recommendation, Ingest), and a latency-budget callout. Each step in §11 is annotated with the matching arrow id (e.g. `B3`).

### 8.1 Component roster (every box on the canvas)

#### Edge plane

| Component | Role |
|-----------|------|
| **Mobile App / Web SPA / Smart TV / Console / Set-top** | end-user surfaces; each ships an embedded player (ExoPlayer on Android, AVPlayer on iOS, Shaka Player / hls.js on web) plus an EME bridge to the device DRM |
| **CDN Edge (Akamai / CloudFront / Cloudflare / Limelight + Open-Connect-style appliances)** | terminates TLS for *segment* requests; caches HLS / DASH segments + manifests; serves ~95 %+ of egress |
| **API Gateway** *(N pods, multi-region active-active)* | TLS, JWT validate, **per-user + per-IP rate-limit** (token bucket in Redis), **geo-IP** (MaxMind), routes to REST services; **never** in the segment path |
| **Auth Svc** | login, refresh, SSO (Google/Apple/Facebook), 2FA optional, sessions in Redis with TTL 30 d (long-lived for "smart TV stays signed in") |

#### Core service row (user-facing, REST)

| Component | Role |
|-----------|------|
| **Catalog Svc**           | reads/writes titles, seasons, episodes; serves `/title/{id}`, `/genre/{g}`, `/home`; multi-region read-replica from Postgres, hot rows cached in Redis |
| **Search Svc**            | thin layer over Elasticsearch / OpenSearch; multi-language tokenisers, fuzzy match, autosuggest, voice-search-friendly |
| **Playback Svc**          | the **only writer** of license-issuance + concurrent-stream state; checks subscription / geo / rights window, signs manifest URL, mints DRM challenge response |
| **User / Profile Svc**    | user, profile, parental-control PIN, language preferences; small Postgres |
| **Watchlist / History Svc** | reads/writes the user's watchlist + viewing history; backed by Cassandra |
| **Subscription / Billing Svc** | plan management, Stripe/Razorpay/Apple-IAP/Google-Play webhooks; writes `subscription.events` to Kafka |
| **Notification Svc**      | push (FCM/APNS), email, SMS, in-app; consumes `subscription.events`, `recommendation.fresh`, `episode.released`; idempotent on `event_id` |
| **Recommendation Svc**    | reads pre-computed rows from Redis; on-the-fly re-rank using session context (last 5 plays) via a small online ranker |

#### Pipeline plane (left side of canvas)

| Component | Role |
|-----------|------|
| **Studio Upload Portal**       | internal app for editorial team; multipart resumable upload of mezzanines (4K HDR ProRes / DNxHR, 50–500 GB) → S3 |
| **Ingest Validator**           | sniffs codec, duration, frame-rate, audio tracks; rejects broken masters before they cost transcoding time |
| **Transcoding Workflow** *(SQS / Kafka job queue)* | DAG of jobs: (1) per-rung video encode (NVENC GPU), (2) per-language audio encode, (3) subtitle ingestion (SRT/SSA → WebVTT + TTML), (4) thumbnail / sprite-sheet, (5) per-DRM packaging, (6) QC, (7) publish |
| **Encoder Pool (GPU farm + AWS MediaConvert)** | the actual FFmpeg workers; horizontally scalable; priority queue (live > new release > catch-up) |
| **Packager (Shaka Packager / Bento4)** | takes encoded fragmented MP4s → emits HLS .m3u8 + DASH .mpd over a **shared CMAF source** (one set of segments serves both); applies CENC encryption with per-title KID |
| **DRM License Server (Widevine / PlayReady / FairPlay)** | mints per-session licenses; HSM-backed signing; regional read replicas; ≤ 200 ms p95 |
| **Origin Storage (S3 multi-region)** | mezzanine + packaged variants; 11-9s durability for masters; standard storage for packaged (recoverable from masters) |
| **CDN Pre-warmer**             | for marquee releases, push first ~5 % of segments to all PoPs ahead of release; hooked into Catalog publish event |

#### Live plane (right side of canvas)

| Component | Role |
|-----------|------|
| **Production Truck / Studio**       | OB van for sports, broadcast control room for news; outputs SDI → encoded RTMP / SRT |
| **Live Ingest Gateway**             | accepts RTMP-push or SRT push; auth via stream key; sequence-checks; primary + standby per event |
| **Live Transcoder (NVENC GPU)**     | per-rung live encoding at the rate of input; **uses CMAF chunked encoding** so the latest 200 ms slice is available before the segment is "complete" |
| **Live Packager (LL-HLS / LL-DASH)** | emits **partial segments** (~200 ms) and **preload hints** for low-latency HLS; CENC encryption for premium live |
| **Origin Shield (regional)**        | a single regional cache layer in front of the N CDNs to **dedupe origin pulls**; without it, 5 CDNs × 10 PoPs each = 50 origin requests per segment |
| **DVR Recorder**                    | the live transcoder also writes to S3 for the DVR rewind window (4 h) and post-event VOD highlights |

#### Async backbone (Kafka)

| Topic                   | Partitions | Producer                | Consumer                         | Notes |
|-------------------------|-----------|-------------------------|----------------------------------|-------|
| `viewing.events`         | 256       | Player (via beacon API) | Cassandra writer, Flink (QoE), Recommendation feature pipeline | per-profile keyed; PLAY_START / PLAY_COMPLETE / PAUSE / RESUME |
| `playback.heartbeat`     | 64        | Player (every 10 s)     | Watch-progress writer, real-time concurrent-stream auditor | ephemeral; key = `session_id` |
| `cdn.qoe`                | 64        | Player                  | Multi-CDN router, Grafana, Flink | rebuffer ratio, throughput, dropped frames |
| `subscription.events`    | 16        | Subscription Svc        | Notification, Audit, Royalty, Concurrent-Stream service | NEW / RENEWED / CANCELLED / DOWNGRADED |
| `catalog.events`         | 8         | Catalog Svc             | Search indexer, Recommendation, CDN pre-warmer | TITLE_PUBLISHED / METADATA_UPDATED / WITHDRAWN |
| `ingest.events`          | 16        | Pipeline workflow       | Encoder pool, Packager, QC, Publish | DAG progress |
| `recommendation.feedback`| 64        | Player + Recommendation | Online learner, A/B analyzer | impressions, clicks, dwell time |
| `audit.events`           | 32        | every service           | Audit ingestor → S3 WORM         | append-only; 7 y cold |
| `*.dlq`                  | 8         | every consumer          | manual triage                    | poison messages |

#### Storage plane

| Store | What | Why |
|-------|------|-----|
| **S3 (mezzanine, 11-nines, 3-region)** | studio masters | the only irreplaceable artifact in the whole system |
| **S3 (packaged, standard)**            | HLS / DASH segments + manifests | re-derivable from mezzanines, so cheaper class is fine; fronted by CDN |
| **Postgres `titles` / `users` / `subscriptions`** | catalog + identity + billing state | OLTP, multi-region read replica |
| **Cassandra (watch-progress, viewing-events)** | high-write, partition-by-profile | append-mostly, eventual cross-region replication |
| **Redis cluster (HOT)** | sessions, manifest URL signatures, license cache, concurrent-stream counters, recommendation rows, search-suggest | sub-ms reads on the hot path |
| **Elasticsearch / OpenSearch**         | search index over titles | tokenised + multi-language + suggest |
| **ClickHouse / Druid**                 | analytics + QoE rollups | columnar, sub-second aggregations over billions of events |
| **HSM (Cloud HSM / Thales)**           | DRM master signing keys + CDN edge cert keys | FIPS-140-2 L3 |
| **etcd (control plane)**               | Live-Ingest leader election; CDN-router config; feature flags | strongly-consistent |

### 8.2 Layered view (ASCII, for the whiteboard)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                  OTT PLATFORM                                          │
│                                                                                        │
│   Mobile · Web · TV · Console                                                          │
│        │ HTTPS                  │ Segment GET (~95% of all traffic)                    │
│        ▼                        ▼                                                      │
│   ┌──────────┐            ┌────────────────────────────────────────────┐               │
│   │ API GW   │            │  Multi-CDN: Akamai · CloudFront · Cloud-   │               │
│   │ (N pods, │            │  flare · Limelight · Open-Connect          │               │
│   │ multi-   │            │  appliances at ISP                         │               │
│   │ region)  │            └─────────────┬──────────────────────────────┘               │
│   └────┬─────┘                          │                                              │
│        │                                │ origin-shield miss                           │
│        ▼                                ▼                                              │
│   ┌──────────────────┐         ┌──────────────────────┐                                │
│   │ Catalog · Play-  │         │ Origin Shield (region) │                              │
│   │ back · Search ·  │         │  →  S3 packaged tier  │                              │
│   │ Profile · Sub /  │         └──────────────────────┘                                │
│   │ Billing · Notif  │                                                                 │
│   └──┬───────────┬───┘                                                                 │
│      │ (REST)    │ produce                                                             │
│      │           ▼                                                                     │
│      │     ┌──────────────────────────────────────────────────────────────┐           │
│      │     │   Kafka backbone (RF=3)                                       │           │
│      │     │   viewing · heartbeat · cdn.qoe · subscription · catalog ·    │           │
│      │     │   ingest · recommendation.feedback · audit                    │           │
│      │     └────────┬─────────────────────────┬──────────────────────────┘           │
│      │              │                         │                                       │
│      ▼              ▼                         ▼                                       │
│  ┌────────────┐  ┌────────────────┐  ┌──────────────────────────┐                     │
│  │ Postgres   │  │ Cassandra      │  │ Recommendation Pipeline  │                     │
│  │ titles /   │  │ watch_progress │  │ Spark batch + online     │                     │
│  │ users /    │  │ viewing_events │  │ ranker (TF-Serving)      │                     │
│  │ subscript. │  └────────────────┘  └──────────┬───────────────┘                     │
│  └────────────┘                                  │                                     │
│                                                  ▼                                     │
│   ┌──────────────────┐    ┌──────────────────────────────────────┐                    │
│   │ Studio Upload    │    │ Live Ingest (RTMP / SRT) → Live      │                    │
│   │ → Transcoding    │    │ Transcode (GPU) → LL-HLS Packager →  │                    │
│   │ DAG → Packager → │    │ Origin Shield → Multi-CDN            │                    │
│   │ S3 mezz + S3 pkg │    │                                       │                    │
│   └──────────────────┘    └──────────────────────────────────────┘                    │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 The control plane (etcd / ZooKeeper)

The control plane is small and intentionally **not** on the playback path:

- **Live ingest leader election** — primary vs standby ingest pod per live event (`/ott/live/{event_id}/leader`).
- **CDN-router config** — per-region multi-CDN weights (refreshed every 30 s based on QoE telemetry).
- **Feature flags & per-region kill-switches** — read-cached in every pod, fail-closed.
- **Encoder pool autoscaler signals** — backlog depth per priority queue.

### 8.4 The 9 invariants (printed on the diagram, panel "9 Key Invariants")

If you can rattle off these nine on a whiteboard you have narrated the entire correctness story:

1. **CDN serves the bytes; origin serves the metadata.** ~99 % of bytes egress are video segments from CDN. Origin / our app servers see ~1 % of traffic. Confusing the two scales us out of business.
2. **One CMAF source, two manifest formats.** HLS and DASH share the *same* encrypted CMAF segments via CENC. We don't double-encode; we double-package. Saves 50 % storage.
3. **Concurrent-stream limit is enforced at license issuance, not at play.** Once the player has a license, we can't claw back a stream — but we never issue a 5th license while 4 are active. The Redis ZSET for `streams:{user}` is the source of truth, with TTLs from heartbeats.
4. **License issuance is the single chokepoint that must scale.** Every play hits it. Regional license replicas with HSM-backed master in primary region; per-license signing JWTs cached on device for the session.
5. **Watch-progress is eventually consistent across regions.** A user who switches from TV to phone may see "Continue Watching at 40:21" instead of 40:31 for a few seconds. Acceptable. Globally synchronous would 10× our latency for zero benefit.
6. **Subscription state is strongly consistent.** A user who downgrades from Premium to Basic must lose 4K access *immediately*. Subscription Svc is the only writer; CDC into Redis with read-your-write.
7. **Manifest URLs are short-lived signed URLs (~5 min TTL).** Re-signed on each play. This forces every play to go through Playback Svc → license check → concurrent-stream check. Without it, a saved manifest URL is a free pass.
8. **Live ingest is active/standby per event, with sequence-number persistence.** A failover during the IPL final must not cause a viewer-visible glitch >1 s.
9. **Every play emits an audit event.** title_id, profile_id, device_id, geo, license_id, started_at_ms — used for studio royalty calculation and DRM compliance audits. Append-only S3-WORM, 7-year retention.

### 8.5 Plain-English Walkthrough — Every Box on the Diagram

#### 8.5.1 The Edge Plane

##### Mobile App / Web SPA / Smart TV / Console / Set-top

These are the five faces of the same platform. Each ships:

- **A native player** (ExoPlayer on Android, AVPlayer on iOS, Shaka or hls.js on web, native on TV/console) that knows how to download an HLS / DASH manifest, choose the right rung of the ABR ladder, fetch segments, push them through the **EME** (Encrypted Media Extensions) pipeline to the platform's **DRM**, and render frames.
- **A small in-app HTTP client** for catalog / search / sign-in / subscription — these go to our API Gateway just like any REST app.
- **A QoE telemetry SDK** that beacons rebuffer events, dropped frames, throughput estimates, and bitrate switches every ~10 s to `cdn.qoe`. This is what powers our real-time multi-CDN routing — we know within seconds when Akamai's Hyderabad PoP is degrading.

The reason every device has its own native player (not a single web-view) is that **DRM is platform-specific**. Widevine on Android wraps a Linux Trustzone OS, FairPlay on iOS wraps the Secure Enclave, PlayReady on Xbox wraps the SoC. We can't paper over them with one player; we ship five, with a shared protocol layer.

##### CDN Edge (Multi-CDN)

This is where ~95 % of our egress lives. A user requesting `https://cdn.ott.example/segments/title_X/720p/seg00042.m4s` is served from a PoP within ~50 km — probably on Akamai's Hyderabad rack or our own Open-Connect-style appliance inside Reliance Jio's data centre. The CDN never goes to S3 unless its cache has missed; cache is keyed by URL path + segment number, with `Cache-Control: public, max-age=31536000` (segments are immutable — same content forever — so cache-forever is safe).

The PoP also terminates TLS, runs WAF rules ("block IPs with > 100 req/s"), and emits its own access logs that we Kafka-ingest for analytics.

The **multi-CDN dimension** is the subtle part: a single CDN's PoP outage during a marquee event would take down 30 % of viewers. We run **5 CDNs in parallel**, with a per-region weighting that updates every 30 seconds based on QoE. Hotstar publicly stated they used Akamai + L3 + Edgecast + their own appliances during IPL 2023 to absorb 32 M concurrent.

##### API Gateway

Front door for REST. Validates the JWT, rate-limits per-user and per-IP, attaches the `country` (from MaxMind GeoIP lookup) and `subscription_plan` to the internal request headers, then routes to the matching service. The single most important property: **the API Gateway is never in the segment path**. If our app servers are in `us-east-1` and the user is in Mumbai, we still serve their *segments* from a Mumbai PoP — the round-trip to the API is only for "play decision" and "watch-progress write", once per play and every 10 s respectively.

##### Auth Service

Stateless OAuth2 server. Issues access tokens (short, 1 h) + refresh tokens (long, 30 d). The refresh token lifetime is 30 d so smart TVs that haven't been turned on for 3 weeks still resume signed-in. Sessions live in Redis as `session:{token_jti}` so we can revoke; the JWT itself carries `user_id`, `country_home`, `subscription_plan`, `max_devices`, `max_resolution`. Every other service trusts the JWT.

#### 8.5.2 The Service Plane

##### Catalog Service

Owns the editorial source of truth — `Title`, `Season`, `Episode` rows in Postgres. Three call patterns dominate:

1. **Home page** — `GET /home?country=IN&profile=…` — returns 12 personalized rows (top 10 IN, continue watching, because-you-watched, new releases, …). The catalog rows themselves are tiny; the personalization is the heavy lift, served by the Recommendation Svc which we describe below.
2. **Title page** — `GET /title/{id}` — name, synopsis, cast, similar titles. This is *very* read-heavy; cached in Redis with 1 h TTL keyed by `title:{id}:{country}` (different countries see different "available_until" dates).
3. **Episode list** — `GET /title/{series_id}/episodes` — paginated by season; one Postgres index hit.

Edits flow from the editorial portal: a producer marks a title as PUBLISHED; Catalog Svc writes to Postgres + emits `catalog.events.title_published`; Search indexer + Recommendation feature pipeline + CDN pre-warmer all consume.

##### Search Service

A thin Elasticsearch / OpenSearch client. The interesting parts are at indexing time, not query time:

- **Multi-language tokenisation** — Hindi, Tamil, Telugu, Bangla, English, Spanish, Korean, Japanese — each language has its own analyzer (ICU + language-specific stemmer + synonym list).
- **Tolerant matching** — fuzzy edit-distance for typos ("avengers" ≈ "avengrs"), n-gram for partial ("breaking ba…" → "Breaking Bad").
- **Voice-friendly** — TVs send transcribed voice queries that are noisy ("the office" comes through as "thee offiss"); we lowercase + remove duplicate vowels + retry.
- **Boost & ranking signals** — popularity, recency, country match, language match, user's genre affinity (passed in the search request). The final score is `BM25 × boost(country, language) × popularity_decay`.

##### Playback Service — the gatekeeper of every play

This is the **only** writer of license-issuance and concurrent-stream counters. When the user taps Play, the player calls `POST /play/{title_id}` with a body containing `device_id`, `device_type`, `drm_system_supported`, `current_session_id`. Playback Svc does six things in sequence (each one early-returns on failure):

1. **Resolve the title** — fetch `available_countries`, `requires_premium`, `license_window_*`. Reject 403 if not licensed in this country, 402 if not on the right subscription tier, 410 if the licensing window has expired.
2. **Concurrent stream check** — atomic Redis operation. Lua script:
   ```
   ZRANGEBYSCORE streams:{user_id} (now-30s) +inf  -- prune dead heartbeats
   ZCARD streams:{user_id}                          -- count live
   IF count >= max_devices → return MAX_DEVICES_REACHED
   ZADD streams:{user_id} {now} {session_id}
   EXPIRE streams:{user_id} 3600
   ```
   The CAS guarantees a 5th simultaneous request fails even if all 5 hit different pods at the same nanosecond.
3. **DRM challenge → license** — the player has sent a DRM "license challenge" (Widevine PSSH for Android, FairPlay key request for iOS). Playback Svc validates the request, mints a license (a ~1 KB blob containing the wrapped content key + policy: max resolution, output protection, expiry), signs it with the regional license server's HSM key, stores `license:{session_id}` in Redis with TTL `min(title_duration + 1h, 24h)`, returns it.
4. **Sign manifest URL** — produce a signed URL like `https://cdn.../title_X/master.m3u8?expiry=…&sig=…`. Signed with our edge-signing key (rotated weekly). TTL ~5 min — long enough for the player to start, short enough that a leaked URL is useless tomorrow.
5. **Emit `play.start` event** — to Kafka `viewing.events`, partition-keyed by `profile_id`. Downstream: watch-progress writer, recommendation feature ingester, royalty pipeline, audit log.
6. **Return** the manifest URL + license to the player.

The whole thing is < 30 ms p95. Every part is optimised for tail latency: subscription / max-devices is pulled from Redis (not Postgres), the license is signed in-memory (HSM is only hit for key derivation, cached per-region), the manifest URL is just a pre-built path + signature.

If we lost this service, no new plays would start, but **already-playing streams would continue uninterrupted** because they have license + segments from CDN. That's a deliberate design property — Playback Svc failure is degrading, not catastrophic.

##### User / Profile Service

Tiny CRUD service. Account hierarchy is `User → 1..5 Profile`. The interesting bit is the **kid profile** flag, which gates the catalog at every level: home page, search, recommendations all filter to G-rated titles.

##### Watchlist / History Service

Reads from Cassandra. The watchlist is small (≤ 200 titles, kept as a list per profile); the history is large but read in pages.

##### Subscription / Billing Service

Wraps Stripe / Razorpay / Apple-IAP / Google-Play-Billing / Amazon-IAP. Every payment provider has its own webhook contract; we normalize them all into our internal `subscription.events` topic with a unified schema.

The trickiest bit: **multiple payment providers per user**. If a user signs up via Apple-IAP on iPhone, then logs in on web and tries to "upgrade", we **must not** show them a Stripe checkout (Apple takes 30 % cut and contractually owns the subscription lifecycle for that user). Each user has a `payment_provider` that's locked at signup; we show them the right upsell flow.

##### Notification Service

Consumes `subscription.events`, `recommendation.fresh`, `episode.released`, `live.starting_soon`, `payment.failed` topics. Routes via FCM (Android), APNS (iOS), email (SES), SMS (Twilio) based on user preferences. Idempotent on `event_id` — at-most-once delivery per event per user per channel, even if Kafka redelivers.

##### Recommendation Service

Reads pre-computed rows from Redis (key: `home:{profile_id}` → HSET of `row_id → [title_ids]`). On the read path it's basically a Redis HGETALL plus an optional **online re-rank**: if the user just played title X, we boost titles similar to X within the next ~30 minutes. The online re-ranker is a small FastAPI service that takes `(user_features, candidate_titles, session_history)` and returns a ranked list — runs in ~5 ms p95.

The expensive offline pipeline (Spark) is described in §14.

#### 8.5.3 The Pipeline Plane (left side)

##### Studio Upload Portal

An internal web app. Uses **S3 multipart resumable upload** so a 200 GB 4K HDR ProRes file can resume after a network drop. Computes SHA-256 client-side; the server side verifies. After upload completes, a row is INSERT'd into `titles` with `ingest_status='PENDING'`, and a job is enqueued.

##### Ingest Validator

The cheapest gate. Runs `ffprobe` on the master, checks codec / duration / frame-rate / audio tracks / colour space / HDR metadata. If anything is off (e.g., interlaced video, unexpected colour space), reject before paying for a 6-hour transcode that would output garbage.

##### Transcoding Workflow (orchestrator)

This is a DAG executor — could be Argo Workflows / AWS Step Functions / Temporal / our own. The DAG looks like:

```
upload → validate → [video_encode_240p, video_encode_360p, video_encode_480p, video_encode_720p, video_encode_1080p, video_encode_4K]
                  → [audio_encode_en, audio_encode_hi, audio_encode_es, …]
                  → subtitle_ingest
                  → thumbnail_extract
                  → package_HLS + package_DASH (uses all video + audio + subtitle)
                  → drm_encrypt (CENC, mints KID, deposits at license server)
                  → qc (automated PSNR / VMAF + manual sample)
                  → publish (S3 mv + manifest signing + catalog.events.title_published)
                  → cdn_prewarm (push first ~10 % of segments to all PoPs)
```

Each step is idempotent (keyed by content hash + parameters) and retryable. Failures route to a triage queue + page editorial.

##### Encoder Pool

A pool of GPU-accelerated FFmpeg workers (NVENC for H.264 / H.265 / AV1) plus AWS MediaConvert for spillover. **Priority queues**:

- **P0 (live)** — sub-second, dedicated capacity
- **P1 (new release / next-day)** — minutes
- **P2 (catch-up backlog)** — hours, runs on spot instances overnight

A 2-hour 4K HDR title at H.265 takes ~8 GPU-hours; the entire ladder takes ~12 GPU-hours all-in. At 50 new title-hours/day across formats, we burn ~500 GPU-hours/day = ~20 GPU-machines busy 24/7.

##### Packager (Shaka Packager / Bento4)

Takes the encoded fragmented MP4s and emits HLS playlists + DASH manifests **over the same CMAF segments**. The trick: a CMAF fragment with `cbcs` encryption can be referenced by both an HLS .m3u8 (with `EXT-X-KEY:METHOD=SAMPLE-AES`) and a DASH .mpd (with `<ContentProtection>`), so we store one set of bytes and ship two manifest files. **Saves ~50% of egress cache footprint** — material at scale.

##### DRM License Server

Mints licenses on demand. Three flavours, one master signing chain:

- **Widevine** — Google's, used on Android, Chrome, ChromeOS, Cast, most Smart TVs. License is signed by our Widevine "content provider certificate" issued by Google.
- **PlayReady** — Microsoft's, used on Edge, Xbox, many Smart TVs and STBs. License is signed by our PlayReady server certificate.
- **FairPlay** — Apple's, used on iOS, macOS, tvOS, Safari. License is signed by an Apple-issued FPS provider certificate, deployed in our HSM.

Master keys live in HSM (Cloud HSM / Thales). Per-region license replicas hold *signing* delegate certs (rotated daily) so a regional outage of HSM doesn't tank license issuance globally — we keep ~24 h of pre-signed delegates.

##### Origin Storage (S3)

- **Mezzanine bucket** (3-region replication, Glacier after 30 d, **immutable** WORM): the studio masters.
- **Packaged bucket** (single-region with cross-region read-replicas via S3 Replication Time Control): the .m4s segments + manifests; CDN's cache origin.

##### CDN Pre-warmer

For marquee releases, when `catalog.events.title_published` lands, the pre-warmer issues HEAD/GET requests to every PoP across every CDN for the **first 10 % of the segments** (covering the first ~12 minutes of the title — long enough for the user to commit). This warms the cache before the announcement, so the very first user doesn't pay the 200–500 ms origin-shield miss penalty.

#### 8.5.4 The Live Plane

##### Live Ingest Gateway

Production trucks push **RTMP** (legacy) or **SRT** (preferred — re-transmits dropped UDP packets, much more reliable over public internet). Each event has a stream key (long-lived, rotated per event) that the gateway validates. **Active/standby**: two ingest pods receive the same RTMP feed (the truck publishes to a load balancer that fans out to both); only the *active* pod's output flows downstream. On primary failure, etcd lease times out in 3 s and standby promotes itself; from the truck's POV nothing happened.

##### Live Transcoder

Same FFmpeg / NVENC binaries as VOD but configured for **chunked encoding** with `keyframe_interval = 1 s`, output **CMAF chunked fragments of 200 ms**. The pipeline runs in a tight loop: as bytes arrive from the ingest gateway, encode, push to packager. End-to-end latency from the encoder to the packager is ~500 ms.

##### Live Packager (LL-HLS / LL-DASH)

LL-HLS (Apple's spec, since 2020) splits a 6-second segment into ~30 partial segments of 200 ms each. The manifest is updated every 200 ms with new partial segments. The player asks for the *latest* partial segment via `EXT-X-PRELOAD-HINT` — the server holds the request open until the partial is ready (HTTP/2 server-push or chunked transfer encoding). End-to-end glass-to-glass: 500 ms encoder + 500 ms packager + 1 s CDN propagation + 1 s player buffer = **~3 s**, well within our 5 s SLO.

##### Origin Shield (regional)

A single regional cache layer in front of the multi-CDN, so every CDN's origin pull goes through *one* regional shield rather than directly hitting our origin packager. Without it, 5 CDNs × 10 PoPs each = 50 origin pulls per segment; with it, 5 → 1. Cuts origin egress by ~50× during a 50 M concurrent live event.

##### DVR Recorder

The live transcoder also writes to S3 in 6-second segments for the entire event duration. Viewers who tune in late can scrub back up to 4 hours (the DVR window). After the event, the same recordings are mastered into a VOD title with proper ABR ladders, and the live event row becomes a permanent VOD entry in the catalog.

#### 8.5.5 The Analytics & Recommendation Plane

##### Kafka backbone

All asynchronous data flow goes through Kafka. Per-region Kafka clusters with cross-region MirrorMaker for analytics aggregation. Topics keyed by `profile_id` for per-user ordering, by `title_id` for per-title aggregation. Idempotent producers, RF=3, `acks=all`.

##### Flink (real-time stream processing)

Consumes `cdn.qoe` to compute per-PoP rebuffering ratios in 10-second windows; output drives multi-CDN routing.

Consumes `viewing.events` to compute **real-time concurrent-stream counts** as a backup to Redis (for audit and over-license detection).

##### Spark (offline batch)

Nightly:
- Re-train recommendation embeddings (matrix factorisation + deep neural ranker).
- Compute royalty / payout per studio per title per region per day.
- Update `Top 10` rows per country and language.
- Feature pipeline: rebuild user feature vectors (genre affinity, language, time-of-day pattern, completion rate).

##### ClickHouse / Druid

OLAP store for the QoE dashboards (rebuffer ratios, startup time, bitrate distribution per CDN per region per device per time-bucket). Backs Grafana for ops + the studio analytics portal.

---

## 9. Component Deep-Dives

### 9.1 Playback Service — the gatekeeper

We described the role above; here's the *implementation*.

**Stack:** Go (low GC pause, great gRPC). Stateless, horizontally scaled, ~200 pods at peak. Co-located with Redis (license cache + concurrent-stream counters).

**Hot path** (`POST /play/{title_id}`):

```
1. Parse JWT  → user_id, country_home, plan, max_devices, max_resolution
2. Postgres (read replica): SELECT title metadata
   - if not in available_countries OR licensing window expired → 403
   - if requires_premium AND plan != PREMIUM → 402
3. Redis Lua atomic block (the concurrent-stream gate):
     local now = redis.call('TIME')[1]*1000 + tonumber(redis.call('TIME')[2]/1000)
     redis.call('ZREMRANGEBYSCORE', 'streams:'..user_id, '-inf', now-30000)
     local n = redis.call('ZCARD', 'streams:'..user_id)
     if n >= max_devices then return {err='MAX_DEVICES'} end
     redis.call('ZADD', 'streams:'..user_id, now, session_id)
     redis.call('EXPIRE', 'streams:'..user_id, 3600)
     return {ok=true}
4. DRM license issuance:
     - compute KID for this title (from Postgres / cache)
     - look up content key (HSM-wrapped, in Redis license-key cache)
     - mint license (signed by region's delegate cert)
     - store license:{session_id} → license_blob, TTL = 24h
5. Manifest URL signing:
     - manifest path: /v1/{title_id}/{variant}/master.m3u8
     - sign with HMAC(edge_signing_key, path + expiry + user_id)
     - URL: https://cdn.../path?expiry=…&sig=…
6. Emit Kafka viewing.events.PLAY_START (key=profile_id)
7. Return { manifest_url, license_blob, session_id }
```

**Failure modes:**

- Redis unavailable → fail-closed (reject the play with 503 RETRY) — better than over-licensing.
- Postgres replica lag → use cached `title` (1 h TTL); accept up to 1-h stale licensing window enforcement.
- Kafka produce failure → still return 200 to the user; emit to a small **local-disk dead-letter queue** that a sidecar drains; analytics is eventual.

**Why a single service?** Because all the checks must be **atomic** with respect to license issuance. Splitting "subscription check" from "concurrent stream check" from "license sign" introduces window conditions where a 5th license is issued between two RPCs.

### 9.2 DRM License Server — per-region replicas

The DRM license server is a separate deployable from Playback Svc because:

1. **Different scaling axis** — license issuance is bursty (50 M concurrent at IPL kickoff = 5 M licenses in 30 s); Playback Svc handles steady catalog browsing too.
2. **HSM-bound** — license signing requires the signing key, and the HSM is FIPS-140-2 L3 with strict access controls. We don't want every Playback Svc pod talking to HSM.
3. **DRM-specific protocol** — Widevine, PlayReady, FairPlay each have their own license request/response binary protocols. Encapsulated behind a thin REST facade.

The **regional delegate signing** pattern: master key in primary region's HSM signs ~24 hours of "delegate certificates" each day. Delegate certs are deployed to each region's license server. Each region signs its own licenses using its delegate. If the master HSM dies for 12 hours, regional license issuance continues uninterrupted; we just can't roll over delegates until master is back.

### 9.3 Manifest Signing & Edge Signing Keys

Manifest URLs are short-lived signed URLs:

```
https://cdn.ott.example/v1/{title_id}/{variant}/master.m3u8
   ?Expires=1714665600
   &User=u_abc123
   &Signature=base64(HMAC-SHA256(secret, "{path}|{expiry}|{user}"))
```

The **edge signing key** is held by:
1. **Playback Svc** (signs URLs).
2. **CDN edge** (verifies them — every CDN supports custom URL signing).

Keys rotate weekly. CDN configurations are updated via API a few hours before rollover; both old and new keys are accepted for the overlap window.

The reason for the `User=` field: it lets us **per-user blacklist a leaked URL**. If a user tries to share their session, we can revoke the key prefix for that user without affecting anyone else.

### 9.4 Watch-Progress Service & Heartbeat Pipeline

The player heartbeats every 10 s:

```
POST /heartbeat
{
  "session_id": "s_abc",
  "profile_id": "p_xyz",
  "title_id":   "t_avengers",
  "position_ms": 124300,
  "duration_ms": 7200000,
  "buffered_ms": 30000,
  "throughput_kbps": 8500,
  "current_bitrate_kbps": 5000,
  "rebuffer_count_session": 1,
  "rebuffer_total_ms_session": 320,
  "device_id": "d_iphone_xyz"
}
```

Two hot paths:

1. **Concurrent-stream refresh.** Server runs `ZADD streams:{user_id} {now} {session_id}` so the entry doesn't time out. (Implicit: no heartbeat for 30 s → entry is pruned, slot is released for another device.)
2. **Watch-progress UPSERT.** UPSERT into Cassandra `watch_progress` with `position_ms` (per partition; LWT not used — last-write-wins is fine, the player on the same session doesn't go backwards).

The heartbeat *also* fans out to Kafka `playback.heartbeat` (key = `session_id`) for the analytics pipeline (Flink consumes this for QoE).

**Why not write every heartbeat to Postgres?** 700 K writes/s would shred Postgres. Cassandra absorbs it on a 30-node ring. The reads are also clean: "where did profile X leave off on title Y" is a single-partition lookup.

### 9.5 Multi-CDN Router (Selector)

Lives between the player and the CDNs. The router's job: for each player session, pick the best CDN edge to serve segments from.

Two implementations, in production at major OTTs:

- **Client-side** (Conviva / Bitmovin / mux model): the player downloads a *manifest of manifests* — for each ABR rung, it has the URL on each of N CDNs. The player measures per-CDN throughput on the first few segments and switches if performance is poor.
- **Server-side** (DNS / signed-URL): the manifest URL contains a CDN-routing token; on each manifest re-fetch, the router decides the active CDN based on per-region QoE scores updated every 30 s.

We use **server-side primary + client-side fallback**. The server-side decision is broad (region × time × device) and uses real-time `cdn.qoe` aggregates; the client can override locally if it sees a streak of failures.

Routing inputs:
- Per-CDN per-region rebuffer ratio (last 30 s).
- Per-CDN per-region throughput (p50, p95).
- Per-CDN error rate.
- Cost weighting (CDN B is cheaper, prefer if QoE is within 10 %).
- Capacity weighting (CDN A is at 90 % of contractual capacity; spill to others).

### 9.6 Subscription / Billing Service

A common engineering trap: **trust the payment provider's webhook**. We don't.

- **Webhooks are events, not the source of truth.** A webhook for `customer.subscription.created` arrives → we do the work, but we *also* schedule a reconciliation job that pulls the provider's API once per day per active sub and compares. Webhooks can be lost; reconciliation catches that.
- **Idempotent webhook handling.** Every webhook has a provider event-id; we Postgres-INSERT into `webhook_events (provider, event_id) UNIQUE`. Duplicate webhooks no-op.
- **Multi-provider routing.** A user's `payment_provider` is locked at signup; web upgrades for an Apple-IAP user redirect them to the iOS app to manage their sub there. Apple contractually owns that subscription's lifecycle.
- **Currency / region.** Plan prices vary by country (Premium is $19.99 in US, ₹649 in India). The `country_home` at signup locks pricing.
- **Retry / dunning.** A failed payment → 3 retries over 7 days → grace period (still streams) → suspend. All driven by a state machine in Subscription Svc, not the payment provider.

### 9.7 Recommendation Service — read path

The read path is intentionally trivial: open Redis, HGETALL `home:{profile_id}`, return the rows. Sub-millisecond.

The Redis values are pre-computed by the offline pipeline; the online path just re-ranks the top of each row based on session-context (last 5 plays, current local time, currently-playing-now) using a small Python service that loads a TF-Lite model.

Caching strategy:
- **Redis miss** (new user, or first home page after long absence) → fall back to a *country / language popularity row* (`fallback:{country}:{language}`) plus a "Top 10" row. Eventually the offline pipeline catches up and personalizes.
- **TTL** is 1 hour. Editorial publishes (`catalog.events.title_published`) trigger an immediate refresh of `Top 10` and `New Releases`.

### 9.8 Search Service

Elasticsearch / OpenSearch. Index has ~50 K docs (titles), so it fits in RAM. Sharding is trivial (single shard would suffice; we use 5 for parallelism + redundancy).

The interesting part is **query-time per-region filtering**: we index `available_countries` as a keyword multi-value, and every query carries a `country` filter. The same index serves all regions; the filter prunes results.

Voice queries from TV remotes are noisy. We feed them through a small "did you mean" pass: phonetic matching (Soundex / Metaphone), then fuzzy match (edit distance ≤ 2), then n-gram. If the corrected query has high confidence, we suggest "Searching for 'breaking bad'…" in the UI.

### 9.9 Notification Service

Stateless consumers of multiple Kafka topics. Each event maps to 0..N notifications based on user preferences:

```
subscription.events.payment_failed →
   if prefs.email && !muted("billing"): send email via SES
   if prefs.push && !muted("billing"): send push via FCM/APNS
   if prefs.sms (rare): send SMS via Twilio
```

Idempotency: each notification carries `(event_id, channel, user_id)` as a hash; we INSERT into a Postgres `notifications_sent` table with that as PRIMARY KEY. A redelivered Kafka message no-ops on the second insert.

Rate-limiting: we *deduplicate within a 24-hour window* so a user doesn't get bombed with "new release" notifications if 10 titles drop the same day; we batch into a daily digest unless the user opted in to immediate.

---

## 10. Video Ingest & Transcoding Pipeline

The transcoding pipeline is its **own system** with its **own scaling axis** — independent of playback. It is the most CPU-intensive part of the entire OTT.

### 10.1 The DAG, top to bottom

```
                  ┌─────────────────────────────┐
                  │   Studio Upload Portal       │
                  │   (multipart resumable S3)   │
                  └────────────┬─────────────────┘
                               │ S3 PUT complete
                               ▼
                  ┌─────────────────────────────┐
                  │   Ingest Validator           │
                  │   (ffprobe; reject early)    │
                  └────────────┬─────────────────┘
                               │ pass
                               ▼
                  ┌─────────────────────────────┐
                  │   Workflow Orchestrator      │
                  │   (Argo / Step Functions /   │
                  │    Temporal)                 │
                  └─────┬──────┬────────┬────────┘
            video_encode│      │audio_  │subtitle_
                  (×N rungs)   │encode  │ingest
                       ▼        ▼        ▼
                  ┌─────────────────────────────┐
                  │   Encoder Pool (NVENC GPU +  │
                  │   AWS MediaConvert spillover)│
                  └────────────┬─────────────────┘
                               │ encoded fragmented mp4
                               ▼
                  ┌─────────────────────────────┐
                  │   Packager (Shaka Packager)  │
                  │   CMAF source →              │
                  │     HLS .m3u8 + DASH .mpd    │
                  │   CENC encryption (per-KID)  │
                  └────────────┬─────────────────┘
                               │
                               ▼
                  ┌─────────────────────────────┐
                  │   QC: VMAF score + manual    │
                  │   sample frames              │
                  └────────────┬─────────────────┘
                               │ pass
                               ▼
                  ┌─────────────────────────────┐
                  │   Publish: S3 mv to public   │
                  │   bucket + catalog mark      │
                  │   PUBLISHED + CDN pre-warm    │
                  └─────────────────────────────┘
```

### 10.2 Ladder design

A typical 5-rung HLS / DASH ABR ladder for a 1080p title:

| Rung   | Resolution | Codec      | Bitrate     | Audience                    |
|--------|------------|-----------|-------------|-----------------------------|
| 240p   | 426×240    | H.264 base | 400 kbps    | 2G / very low bandwidth     |
| 480p   | 854×480    | H.264 main | 1 Mbps      | 3G mobile, India tier-3     |
| 720p   | 1280×720   | H.264 high | 3 Mbps      | LTE / good Wi-Fi            |
| 1080p  | 1920×1080  | H.265 main | 5 Mbps      | Fibre / premium plan        |
| 1080p (H.264 fallback) | 1920×1080 | H.264 high | 8 Mbps | older devices without HEVC |

For 4K HDR titles, we add:

| Rung   | Resolution | Codec       | Bitrate    | Notes |
|--------|------------|-------------|------------|-------|
| 4K SDR | 3840×2160  | H.265 main10 | 15 Mbps   | premium plan, HDR10 not enabled |
| 4K HDR | 3840×2160  | H.265 main10 + Dolby Vision | 25 Mbps | premium plan, HDR-capable display |

We also experiment with **AV1** (~40 % bitrate savings at the same quality) for newer devices — gradually replacing H.265 across the ladder. Old TVs without AV1 get H.265 / H.264 fallback.

### 10.3 Per-title encode time

A 2-hour 4K HDR master:
- 4K HDR H.265 encode: 8 GPU-hours (NVENC, fast preset, 1× real-time on a single A100).
- 1080p H.265 encode: 1.5 GPU-hours.
- 720p / 480p / 240p H.264 encodes: ~3 GPU-hours combined.
- Audio encode (5–6 languages × 256 kbps AAC): 2 CPU-hours.
- Subtitle ingestion + packaging + DRM: 30 minutes.
- **Total wall-clock**: ~3 hours on a 4-GPU machine; ~12 GPU-hours of compute.

For a Marvel-tier release with 60 dub languages and 100 subtitle tracks, audio/subtitle work dominates and we trade time for parallelism.

### 10.4 Why CMAF is non-negotiable

Pre-CMAF, you had to encode and store HLS segments (.ts files) and DASH segments (.m4s files) separately — 2× the storage. CMAF (Common Media Application Format) defines a single fragmented MP4 (.cmfv) that **both** HLS and DASH can reference. The catch: HLS segments traditionally were MPEG-TS, not fMP4. Apple shipped fMP4 support in HLS in 2016, removing the storage doubling.

For us, CMAF means:
- Encode once.
- Encrypt once (CENC).
- Package twice (write a .m3u8 for HLS players, a .mpd for DASH players, both pointing to the same .cmfv segments).

Cuts our packaged storage from ~4.8 PB to ~2.4 PB. CDN cache footprint halved. Win.

### 10.5 DRM packaging

**CENC** (Common Encryption) defines two cryptographic modes:

- **`cenc`** (CTR mode) — what most DASH players support.
- **`cbcs`** (CBC-with-subsample-pattern) — what FairPlay (Apple) requires.

We use `cbcs` exclusively now (Widevine and PlayReady both adopted it; FairPlay only supports it). One encryption pass produces ciphertext that **all three DRMs can play** with their respective licenses. This is the modern norm.

The packager:
1. Generates a **KID** (16-byte key ID) per title (or per asset variant if needed).
2. Generates a **content key** (16 bytes), wraps it with the DRM provisioning key, deposits at each license server keyed by KID.
3. Encrypts each segment with the content key in `cbcs` mode.
4. Writes the **PSSH box** (Protection System Specific Header) for each DRM into the segment headers / manifest.

At play time, the player extracts the PSSH, sends it to the license server, gets back the wrapped content key (decryptable only on the device's TEE / Secure Enclave), and decrypts segments as they're fetched.

### 10.6 Subtitle and Audio Tracks

- **Subtitle:** Studios deliver SRT / SubRip / SCC / TTML / DFXP files per language. We normalize to **WebVTT** (HLS) + **TTML** (DASH) + **CEA-608/708** for some platforms. Closed-captions are mandatory for accessibility (FCC in US, broadcaster-equivalent rules elsewhere).
- **Audio:** Each language is encoded separately (AAC-LC stereo at 128 kbps for everyone, AAC-HE for low bandwidth, E-AC3 5.1 / Dolby Atmos for premium content). The HLS / DASH manifest declares the language tag; the player picks based on user preference.

### 10.7 Pre-warming the CDN

For a marquee release (a Marvel film, a Squid Game new season), we pre-warm the CDN cache:

```
catalog.events.title_published
   → CDN Pre-warmer fans out a list of (PoP × CDN × segment_path) tuples
   → A small workflow makes HEAD requests to each PoP for the first 10 % of each rung's segments
   → CDN pulls from origin shield and populates its cache
   → By the time the title goes live, ~80 % of regions have warm cache for the first 10 minutes of content
```

For a title with 12 K segments, the first 10 % is ~1200 segments × ~4 MB = 4.8 GB per ABR rung × 5 rungs = 24 GB per region per CDN. With ~100 PoPs × 5 CDNs, that's ~12 PB of pre-warm traffic. We do it in the off-hours before the announcement — egress is metered but cheap at midnight, expensive at 8 PM.

### 10.8 QC (Quality Control)

Two passes:
1. **Automated:** compute **VMAF** (Video Multi-method Assessment Fusion, Netflix's perceptual metric) against the master. If VMAF < 85 on any rung, fail the encode and re-run with adjusted parameters.
2. **Manual:** sample frames every 30 s from the 1080p output → assemble into a contact sheet → editorial reviews. Mostly detects black frames, ad-pod misplacement, dropped subtitles.

---

## 11. End-to-End Flows

The flows below match the **numbered flows on the HLD diagram** (panel "8 Numbered Flows"). Each step references the diagram arrow id (e.g., `B3`).

### 11.1 Flow 1: Browse Home Page (cold cache)

```
  [User opens app]
         │  1. App loads → fetches JWT (from secure storage)
         ▼
  [API GW]                           --- A1
         │  2. Validates JWT, attaches country (from MaxMind), routes
         ▼
  [Catalog Svc] ──► [Postgres titles] (read replica)        --- B1
         │  3. catalog rows + image URLs (CDN-hosted)
         ▼
  [Recommendation Svc] ──► [Redis home:{profile_id}]        --- C1
         │  4. 12 personalized rows, list of title_ids per row
         ▼
  [Catalog Svc] ──► hydrate title metadata for each title_id
         │  5. (hits Redis cache for title:{id}; <1ms each)
         ▼
  [API GW] ◄── { rows: [...] }
         │  6. JSON response, ~50 KB
         ▼
  [Player] renders home page; image URLs point to CDN
         │
         ▼
  [CDN] serves poster.jpg, backdrop.jpg from PoP cache       --- A2
```

**Latency**: p95 ~ 200 ms total; ~50 ms of that is CDN-served images downloading in parallel with the JSON.

### 11.2 Flow 2: Play VOD (the hot path)

```
  [User taps Play on title T]
         │  1. POST /play/T  with device_id, drm_supported    --- B3
         ▼
  [API GW] → [Playback Svc]
         │  2. Subscription check (JWT) + geo check (Postgres replica)
         │  3. Concurrent-stream Lua block (Redis ZSET)
         │  4. License mint (HSM-cached delegate, signed in-memory)
         │  5. Manifest URL signing (HMAC, 5-min TTL)
         │  6. Emit Kafka viewing.events.PLAY_START
         ▼
  Returns: { manifest_url, license_blob, session_id }
         │  7. Player receives in ~30 ms p95
         ▼
  [Player] fetches manifest_url → CDN edge                  --- A3
         │  8. CDN cache hit ~95% → ~50 ms
         ▼
  [Player] parses manifest, picks initial bitrate (ABR)
         │  9. fetches init.mp4 + first media segment
         ▼
  [CDN] serves segments from cache                           --- A4
         │  10. ~50ms each segment
         ▼
  [Player] decrypts segments (DRM key from license_blob)
  [Player] decodes + renders first frame                    --- "first frame on screen"
         │  11. happens at p95 ≤ 1.8s after the tap
         ▼
  [Player] every 10 s heartbeats (POST /heartbeat)          --- D1
         │  12. concurrent stream slot refresh + watch progress
         ▼
  [Watch-Progress Svc] → [Cassandra UPSERT]                 --- D2
         │  13. + Kafka playback.heartbeat for QoE
         ▼
  [Player] continues until pause / stop / complete
  [Player] on STOP emits viewing.events.PLAY_STOP / COMPLETE --- E1
```

### 11.3 Flow 3: Play Live Event (e.g., IPL match)

```
  [User taps Play on a LIVE event]
         │  Same Flow 2 up to license issuance, then:        --- B5
         ▼
  [Player] fetches LL-HLS / LL-DASH manifest
         │  manifest is dynamic — refreshed every 200 ms
         ▼
  [CDN] serves manifest (cache TTL = 1 s) + partial segments
         │  CDN cache for partial segments is short-lived (~5 s)
         │  but origin-shield in front of CDN dedupes pulls    --- F1
         ▼
  [Origin Shield (regional)] → [Live Packager]
         │  Live Packager has the segments in memory (recent-only)
         ▼
  [Live Transcoder] streams CMAF chunks to packager
         │  ~500 ms encode + 500 ms package = 1 s ingest-to-packager
         ▼
  [Live Ingest] receives RTMP from production truck         --- F2
         │
         │  ── upstream: production truck SDI feed ──
         ▼
  [Player] glass-to-glass latency ~3-5 s
         │  Heartbeats + DVR scrubbing same as VOD flow
```

### 11.4 Flow 4: Concurrent-Stream Eviction (5th device)

```
  User has 4 streams active on Premium plan, opens a 5th device:
  [Device 5] POST /play
         │
         ▼
  [Playback Svc] runs Lua atomic block:
       ZREMRANGEBYSCORE streams:{u} -inf, now-30s
       count = ZCARD streams:{u}     → 4 (all live)
       count >= max_devices          → return MAX_DEVICES_REACHED
  [Device 5] receives 429 / MAX_DEVICES error
  [App] shows dialog:
        "You're streaming on 4 devices. Stop one?"
        [Stop one and play here] [Cancel]
  [User taps "Stop one and play here"]
         │
         ▼
  [Device 5] POST /play?evict=true
         │
         ▼
  [Playback Svc]:
       evict the OLDEST entry from ZSET
       publish viewing.events.STREAM_EVICTED for that session_id
       proceed with normal Flow 2
         │
         ▼
  [Old Device] WebSocket / Push notification: "Streaming stopped on this device"
       (the player on the old device polls heartbeat response and finds 410 Gone)
```

### 11.5 Flow 5: Studio Upload → Publish (the ingest pipeline)

```
  [Studio editor]
       │  uploads master MOV (200 GB) via portal
       │  multipart resumable S3
       ▼
  [S3 mezzanine bucket]
       │  triggers S3 event → SQS → Workflow Orchestrator
       ▼
  [Validator]
       │  ffprobe; check codec / duration / colour space
       ▼
  [Workflow Orchestrator] forks DAG:
       ├─ Encode 240p, 360p, 480p, 720p, 1080p, 4K rungs
       ├─ Encode audio per language
       ├─ Ingest subtitles per language → WebVTT/TTML
       └─ Extract sprite-sheet thumbnails
       ▼
  [Packager] (after all encodes complete)
       │  CMAF segments + HLS manifests + DASH manifests
       │  CENC encryption per-KID; deposit content key at each license server
       ▼
  [QC]
       │  VMAF >= 85 on every rung; manual sample
       ▼
  [Publish]
       │  S3 mv to public bucket
       │  Postgres titles UPDATE ingest_status='PUBLISHED'
       │  Catalog Kafka catalog.events.title_published
       ▼
  [Search Indexer] consumes → reindex Elasticsearch
  [Recommendation] consumes → enqueue feature build
  [CDN Pre-warmer] consumes → push first 10% to all PoPs
  [Notification] consumes → push notif to wishlisted users
```

### 11.6 Flow 6: Subscription Sign-up + Billing

```
  [User picks Premium plan in app]
       │
       ▼
  [Subscription Svc] (which payment provider?)
       │  if iOS app → Apple-IAP flow (purchase via StoreKit; we never see the card)
       │  if Android app → Google-Play-Billing flow
       │  if web → Stripe Checkout → user enters card
       ▼
  [Stripe webhook customer.subscription.created]
       │
       ▼
  [Subscription Svc]
       │  INSERT into webhook_events (idempotency)
       │  UPSERT subscriptions row, status='ACTIVE'
       │  EMIT subscription.events.ACTIVATED (Kafka)
       ▼
  [Notification Svc] sends "Welcome to Premium!" email + push
  [User Svc] writes subscription_plan claim into next JWT refresh
```

### 11.7 Flow 7: Recommendation Pipeline (offline → online)

```
                                Nightly Spark batch
  [Cassandra viewing_events]  ─────────────────────┐
  [Postgres titles]           ─►  [Spark]          │
  [Kafka catalog.events]      ─►  Embedding train  │
                                  Feature pipeline │
                                  Top-10 compute   │
                                                   ▼
                                  [Redis home:{profile_id}]
                                  (12 rows × 50 titles each)

  Online path (on home-page load):
  [Recommendation Svc] HGETALL home:{profile_id}
       │  if hit → online re-rank top of each row using session context
       │           (last 5 plays, time of day, currently-playing)
       │  if miss → fallback:{country}:{language} popularity row
       ▼
  Returns 12 rows, ~600 title_ids total
```

### 11.8 Flow 8: Multi-CDN QoE-Driven Routing

```
  Player heartbeats QoE:
   { rebuffer_count, throughput_kbps, errors, current_cdn }
       │
       ▼
  Kafka cdn.qoe (key = cdn × region × device_kind, 30s windows in Flink)
       │
       ▼
  Flink computes:
   - p50/p95 throughput per (CDN × region × device)
   - rebuffer ratio
   - error rate
   - score = w1·throughput - w2·rebuffer_ratio - w3·error_rate - w4·cost
       │
       ▼
  Output → [CDN Router config in etcd / Consul]
       │
       ▼
  Manifest signing (Playback Svc) reads the config and chooses CDN per-session
       per region & user. The manifest URL is rewritten to the chosen CDN.
       Also emits a Conviva-style "client-switch hint" the player can act on
       if the chosen CDN starts degrading mid-session.
```

---

## 12. CDN, Multi-CDN & Edge Caching Strategy

### 12.1 Why multi-CDN

A single CDN dependency is a single point of failure in:
- **PoP outages** — Akamai's Hyderabad PoP went down for ~30 min during IPL 2022 final, taking 30 % of traffic offline.
- **Capacity ceilings** — every CDN has a contractual capacity in Tbps per region per second; a 50 M concurrent live event saturates *every* major CDN.
- **Regional pricing arbitrage** — CDN A is cheaper in India, CDN B is cheaper in EU; routing by cost saves real money.
- **Geopolitical** — some CDNs are blocked in some countries; you need redundancy for reach.

### 12.2 Cache hierarchy

```
[Player] ──► [CDN Edge PoP] ──► [CDN Regional Layer] ──► [Origin Shield] ──► [S3]
              (~5 ms)            (~30 ms)                  (~50 ms)            (~200 ms)
```

- **PoP edge cache**: the first hit. Each PoP has ~250 GB of working set, holds the most-popular content.
- **Regional layer**: the CDN's own regional cache. Catches PoP misses.
- **Origin shield**: *our* regional cache (one per region) that all CDNs hit. Without this, 5 CDNs each hitting origin = 5× origin egress; with it, 5 → 1.
- **Origin (S3)**: only hit when shield misses; rare for popular content.

Cache hit ratios, in production:
- PoP: 90–95 % for hot titles, 70 % for long-tail.
- Regional: 98 % aggregate.
- Origin shield: 99 % aggregate.

### 12.3 Cache key design

Keys are URL paths. Segments are immutable — a given segment URL serves identical bytes forever — so we use:

```
Cache-Control: public, max-age=31536000, immutable
```

The signed query parameters (`?expiry=…&sig=…`) are *not* part of the cache key (CDN config strips them before computing the cache key).

For manifests:
- VOD master playlist: `Cache-Control: max-age=300` (5 min; rarely changes).
- VOD media playlist (per rung): `max-age=86400` (1 day).
- Live manifest: `max-age=1` (refreshed every second).

### 12.4 Origin shielding

Origin Shield is a single regional cache layer in front of all CDNs. Implementation:
- Run our own NGINX/Varnish cluster per region (us-east, eu-west, ap-south).
- All CDNs configured to fetch from `https://shield.ott.example` rather than directly from S3.
- The shield cluster has 1–2 TB of SSD per node, ~10 nodes per region.
- Cache hit ratio at shield: 99 %.

The architectural concept: **collapse N edge requests for the same segment into 1 origin request**. During a flash-traffic event, this is the difference between origin handling 1 M req/s and 50 K req/s.

### 12.5 Pre-fetch hints (CMCD)

Common Media Client Data (CMCD) is a 2020 spec where the player annotates segment requests with metadata:

```
GET /segments/title_X/720p/seg42.m4s?CMCD=br%3D5000%2Cbl%3D30000%2Cnor%3D%22..%2Fseg43.m4s%22
```

Decoded: `bitrate=5000, buffer_length=30000ms, next_object_request="../seg43.m4s"`

The CDN reads `nor` (next-object-request) and pre-fetches segment 43 from origin shield while serving segment 42 to the player. Net effect: cold-cache segments 43+ are pre-loaded by the time the player asks. Eliminates the "first scrub-forward" stall.

### 12.6 Open Connect / ISP-side caching

For the long tail of edge cost, OTTs deploy **caching appliances inside the ISP** (Netflix Open Connect, Hotstar's "Ultra Cache" inside Jio). The ISP gets cheaper egress (no peering cost), the OTT gets sub-10ms cache delivery, the user gets lower rebuffer.

Distribution: ~15 K appliances globally, each a 256 TB / 10 Gbps box. Replenished off-peak from origin shield with the predicted "what will this region's users want tomorrow" working set (computed from viewing.events Spark batch).

### 12.7 Multi-CDN routing decisions

Computed every 30 s by Flink consuming `cdn.qoe`, output to etcd as a JSON config:

```json
{
  "region:IN:device:android": {
    "cdn_weights": {
      "akamai": 0.4, "cloudfront": 0.3, "cloudflare": 0.2, "limelight": 0.1
    },
    "cap_pct": { "akamai": 0.85 }
  },
  "region:IN:device:smart_tv": {
    "cdn_weights": {
      "akamai": 0.5, "open_connect": 0.4, "cloudfront": 0.1
    }
  }
}
```

Playback Svc reads this on every play, picks the CDN by weighted random, signs the manifest URL accordingly. **Capacity caps** (e.g., "akamai at 85 %") trigger spillover to the next CDN.

---

## 13. DRM, License Issuance & Content Protection

DRM is the *only* reason most of this architecture exists in its current form. Without studios contractually requiring **L1 Widevine / FairPlay / PlayReady**, you could ship plain TLS-signed MP4s and call it a day. With it, every play has to pass through a per-session license-issuance step that fans into hardware-backed signing, regional replication, and a per-device key wrap.

### 13.1 The three DRM systems we *must* support

| System        | Owner         | Devices                                                  | Robustness levels                          | Notes                                                                                                                  |
|---------------|---------------|----------------------------------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| **Widevine**  | Google        | Android, Chrome, ChromeOS, Cast, most Smart TVs, set-tops | **L3** (software TEE) → **L1** (hardware)  | L1 is mandatory for HD/4K under most studio contracts. L3 capped to 720p in policy.                                     |
| **PlayReady** | Microsoft     | Edge, Xbox, many Smart TVs and STBs (Tizen, WebOS)       | **SL150** → **SL2000** (hardware)          | SL2000 is the 4K-eligible tier.                                                                                         |
| **FairPlay**  | Apple         | iOS, iPadOS, macOS, tvOS, Safari                          | iOS hardware (Secure Enclave); macOS partial | Required for any Apple-platform playback. Provider cert (FPS) issued by Apple, deployed to our HSM.                    |

You **cannot** pick one. iOS won't play Widevine-only content. Android won't play FairPlay-only content. Smart TVs lean PlayReady. So the packager emits **CENC-encrypted ciphertext** in `cbcs` mode that **all three license systems** can decrypt — same bytes, three different licenses.

### 13.2 CENC (Common Encryption) — `cenc` vs `cbcs`

CENC defines two cryptographic modes for fragmented MP4 / CMAF segments:

- **`cenc`** (CTR mode): standard AES-CTR encryption. Original DASH spec. Works with Widevine + PlayReady, *not* FairPlay.
- **`cbcs`** (CBC + subsample patterning): AES-CBC with a configurable encrypt/skip pattern (e.g. encrypt 1 of every 10 blocks). Works with **all three**.

We use `cbcs` exclusively. The packager encrypts each segment once with the content key in `cbcs` mode, writes **per-DRM PSSH boxes** into the segment headers (or manifest), and ships. At play time:

1. Player extracts PSSH for its DRM system.
2. Player builds a **license challenge** containing the PSSH + device-public-key.
3. License server unwraps the content key, re-wraps it with the device's public key, signs the response with our regional delegate cert, returns it.
4. Player's DRM CDM (Content Decryption Module — Widevine on Android, FairPlay on iOS) unwraps inside the **TEE / Secure Enclave**, decrypts segments as they're fetched, hands frames to the hardware decoder. **The content key never leaves the TEE.**

### 13.3 Per-region delegate signing (the latency / availability win)

The naive approach is one global license server fronted by HSM. At 50 M concurrent at IPL kickoff, you'd see a license burst of ~5 M issuance/s; HSM signing throughput is ~10 K signatures/s/cluster — six orders of magnitude short.

The pattern we use:

```
Master HSM (FIPS-140-2 L3, primary region)
  └─> signs  "delegate signing certs"  daily
       (one delegate per region, rotating every 24 h)
                       │
                       ▼
         Regional license servers
         (each region has its delegate)
         Sign per-session licenses in-memory.
```

Each **regional license replica** holds:
- The current **delegate cert** (signed by master, valid 24 h).
- The next 6 days of **pre-signed delegate certs** in storage (so an HSM outage of up to a week doesn't tank issuance).
- A read-through Redis cache of `kid → wrapped_content_key` (for fast key lookup).

License signing is a single ECDSA signature in-memory on a delegate cert — ~200 µs on commodity hardware. We run ~50 license-server pods per region; each handles ~10 K sigs/s; aggregate ~500 K sigs/s/region; six regions ≈ 3 M sigs/s — covers the IPL spike with headroom.

### 13.4 License policy — what's in the blob

A license is more than a key. It's a small JSON / protobuf blob:

```json
{
  "kid":              "8a4e...",           // key id, 16 bytes hex
  "wrapped_key":      "base64...",         // content key wrapped with device pubkey
  "policy": {
    "max_playback_resolution": 2160,       // p, 2160 = 4K
    "hdcp_required":           "TYPE_1",   // HDCP 2.2 for 4K
    "output_protection":       "ALL",      // disallow analog output
    "license_duration_sec":    86400,      // 24 h cached on device
    "playback_duration_sec":   28800,      // 8 h after first frame
    "renewal_url":             "https://license.../renew?session=...",
    "persistent":              false       // false = session, true = download
  },
  "issued_at": 1714665600,
  "session_id": "s_abc",
  "device_id":  "d_iphone_xyz"
}
```

The **policy** is the lever that enforces our subscription contracts:

- **Basic plan** → `max_playback_resolution = 720`, `hdcp_required = NONE`.
- **Standard** → `1080`, `HDCP_TYPE_0`.
- **Premium** → `2160`, `HDCP_TYPE_1`, `output_protection = ALL`.

The CDM enforces these. If a 4K-policy license is on a TV without HDCP 2.2, the player **degrades to 1080p** automatically (the manifest selects the rung the policy allows).

### 13.5 Downloads & offline DRM

Downloads ("watch on a flight") are a special license profile:

- **Persistent license** (`persistent = true`).
- **30-day TTL from download time**, **48-h TTL from first play**.
- Bound to **device_id** (cannot copy to another device).
- Encrypted segments stored on device; license stored in DRM-managed encrypted storage.

The catch: **how do we revoke a download?** We don't, directly — we set the TTL. If a user shares their account with a thief, the worst case is the thief gets ≤ 48 h of playback per cycle on already-downloaded content.

For more aggressive revocation (e.g. fraud detection), we can:
1. Mark the device_id as revoked in our license server.
2. The next license-renewal request (or first-play of any other content on that device) returns `403 DEVICE_REVOKED`.
3. The downloaded content keeps playing for ≤ 48 h (until the offline TTL expires), then stops.

### 13.6 The license-issuance hot path (sequence)

```
[Player] generates challenge with PSSH + device pubkey
[Player] POST /license   { challenge, session_id }   ─►  [Playback Svc]
            (challenge body ~ 1–4 KB)
                │
                │  re-validates session (license:{session_id} in Redis)
                │  reads policy from JWT (plan, max_res, country)
                ▼
            [DRM License Server (regional)]
                │  unwraps content key from Redis cache (key:{kid})
                │  builds license blob (policy)
                │  re-wraps content key with device pubkey
                │  signs blob with delegate cert (ECDSA, ~200 µs)
                │
            ◄── license blob (~1 KB)
[Player]  CDM ingests license; segments now decryptable
```

p95 ≤ 200 ms end-to-end including network. Hot path inside the license server: ~5 ms.

### 13.7 HSM operations & key custody

The HSM is FIPS-140-2 L3, dual-controlled, audited. It holds:
- **Widevine provisioning key** (issued by Google to us as a content provider).
- **PlayReady server certificate** (from Microsoft).
- **FairPlay FPS certificate** (from Apple).
- **Edge URL signing keys** (rotated weekly).
- **JWT signing keys** (rotated daily).

Rotation runbook for delegate certs:
1. **00:00 UTC**: master HSM signs N+6 days of delegate certs (one per region per day).
2. Configuration system pushes new delegates to regional license replicas via signed gRPC.
3. License servers begin using **new delegate** at the boundary; the **old delegate** is still trusted for issued-license validation for 7 more days (so a license issued at 23:59 yesterday is still valid).
4. Audit log records the rotation; deviation pages on-call.

If the master HSM is unavailable:
- Already-deployed delegates continue to work for up to 6 days.
- We cannot mint new delegates → eventually license issuance fails.
- The on-call runbook escalates to vendor (CloudHSM / Thales) and triggers a region failover to the **secondary HSM cluster** (always-warm, async-replicated).

### 13.8 Edge / signed URL — the "cheap DRM" companion

Even with full DRM, we still **sign manifest URLs** as a defense-in-depth layer:

```
https://cdn.ott.example/v1/{title_id}/{variant}/master.m3u8
   ?Expires=1714665600
   &User=u_abc123
   &Signature=base64(HMAC-SHA256(secret, "{path}|{expiry}|{user}"))
```

Signature lives 5 min. CDN refuses any unsigned-or-stale request at the edge with `403`. This forces the player to re-fetch the manifest through Playback Svc on session restart, which re-runs the subscription / concurrent-stream check.

Without signed URLs, a leaked manifest URL is a free pass — anyone with the URL can fetch segments (the segments themselves are encrypted, but they could *try* to play them if they collected enough license blobs, which is a different attack but a related one).

### 13.9 What happens when DRM breaks

Real-world failure modes we've planned for:

| Failure                                               | Effect                                                | Mitigation                                                                                       |
|-------------------------------------------------------|-------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Master HSM unavailable                                | No new delegate certs                                 | 6 d of pre-signed delegates in storage; failover to secondary HSM cluster                        |
| Regional license server down                          | New plays in region fail                              | Multi-AZ replicas; cross-region failover (with ~50 ms latency penalty); circuit-breaker          |
| Widevine provisioning key compromised                 | All Widevine licenses must be re-issued               | Google-side revocation + we re-issue with new provisioning key; emergency runbook 4 h            |
| Studio reports leak via decoded recording             | Need to **forensically watermark** to find the leaker | Per-session A/B-frame watermark embedded at packaging; lookup in audit log; report to studio     |
| Player CDM bug fails to enforce HDCP                  | 4K plays on non-HDCP screens                          | Policy includes `hdcp_required`; failed enforcement is a CDM bug — patch via OS update           |
| Device clock-skew lets expired license keep working   | License "expires" but plays                            | License embeds an `issued_at` and a `nonce`; renewal challenge contains a server-issued nonce    |

---

## 14. Recommendation, Personalization & Search

The home page is the product. ~80 % of Netflix watch-time begins from a row on the home page (Netflix's own number). Recommendation quality moves the entire business — and recommendation latency moves it just as much.

### 14.1 The two-stage recommendation pipeline

Modern OTT recommendation looks like this:

```
                     ┌─────────────────────────────────┐
                     │  Candidate Generation (recall)   │
                     │  ───────────────────────────────  │
                     │  ~50 K titles → ~1 K candidates   │
                     │  collaborative filtering (matrix  │
                     │  factorisation), content-based,   │
                     │  popularity, freshness            │
                     └────────────────┬────────────────┘
                                      │
                                      ▼
                     ┌─────────────────────────────────┐
                     │  Ranker (precision)              │
                     │  ───────────────────────────────  │
                     │  ~1 K candidates → top 50         │
                     │  Deep neural net (two-tower)      │
                     │  features: user × video × context │
                     │  loss: weighted CE on watch-time  │
                     └────────────────┬────────────────┘
                                      │
                                      ▼
                     ┌─────────────────────────────────┐
                     │  Diversity / freshness re-rank   │
                     │  ───────────────────────────────  │
                     │  MMR (max marginal relevance)    │
                     │  category caps · cold-start mix  │
                     └────────────────┬────────────────┘
                                      │
                                      ▼
                     ┌─────────────────────────────────┐
                     │  Online re-rank (per session)   │
                     │  TF-Lite / ONNX in Reco Svc     │
                     │  features: last 5 plays, time   │
                     │  of day, currently-playing      │
                     │  ~5 ms p95                       │
                     └────────────────┬────────────────┘
                                      │
                                      ▼
                     12 home-page rows × ~50 titles each
                     stored at home:{profile_id} in Redis
```

### 14.2 Offline pipeline (Spark, nightly)

Inputs:
- `viewing_events` (last 90 d) → user × video implicit-feedback matrix.
- `recommendation.feedback` → impressions, clicks, dwell.
- Catalog metadata → genre, language, cast, year.
- User features → registration country, language preference, plan tier, time-of-day pattern.

Pipeline:
1. **Build the user × video matrix** (~50 M users × ~50 K titles, sparse; ~10 B non-zeros).
2. **Train embeddings** — Alternating Least Squares (ALS) for collaborative filtering → 64-dim user + video embeddings.
3. **Train the ranker** — TensorFlow two-tower DNN; user tower + video tower; cosine similarity → sigmoid → predicted watch-time. Loss = weighted cross-entropy.
4. **Compute candidate sets** per user — top-1000 video embeddings by inner-product (using ScaNN / FAISS).
5. **Rank candidates** — apply DNN ranker; take top-50.
6. **Diversity re-rank** — MMR with λ=0.5; cap each genre at ~30 % of the row.
7. **Generate rows** — *Top Picks*, *Continue Watching*, *Because You Watched X*, *New Releases*, *Trending in Your Country*, *Top 10*, plus genre rows (drama / comedy / sport / kids).
8. **Publish** — write `home:{profile_id} → {row_id → [title_ids]}` to Redis HSET, TTL 1 h.

Runtime: ~6 h on a 200-node Spark cluster nightly. Output ~50 M Redis writes (~50 GB).

### 14.3 Online ranker (per-session re-rank)

The home page is loaded ~5× per DAU per day = ~50 M home-loads/day. Each one is a Redis HGETALL + an optional re-rank.

We keep the offline rows in Redis as the **base set**. On each home load:

1. **HGETALL home:{profile_id}** → 12 rows × ~50 titles → ~600 title_ids.
2. **Read session-context** (last 5 plays, current local time, currently-playing) from Redis `session:{profile_id}`.
3. **Online re-rank** the *top of each row* (top 10 only — the user rarely scrolls past 10) using a small TF-Lite model loaded into the Reco Svc pods. Inputs: candidate's pre-computed embedding + session-context features → re-ranked score.
4. **Hydrate metadata** for the top ~120 titles (12 rows × ~10 visible) from Redis title cache.
5. **Return** to client.

p95 ≤ 50 ms total. The TF-Lite model is ~5 MB, ~2 ms inference per title batch.

### 14.4 Cold-start

Three cohorts:

- **New user** (just signed up) → onboarding survey: pick 3 favourite genres + 5 sample titles → bootstrap embeddings. Fall back to country/language *popularity row* (`fallback:{IN}:{hi}`) for 12 home rows until the offline pipeline catches up (~24 h after first play).
- **New title** (just published) → no `viewing_events` yet, so collaborative filtering gives nothing. Use **content-based** scoring: similarity-by-cast / genre / year against existing titles, plus editorial-tagged "Top Pick" boosts. Once watch data arrives (~1 K plays), CF kicks in and overshadows.
- **New region launch** → the first month is mostly popularity + content-based; we explicitly bootstrap with editorial picks reviewed by local editors.

### 14.5 Multi-armed bandit / exploration

Pure ranker exploitation calcifies — the user only sees titles they're "predicted to like", missing latent interests. We run **Thompson sampling exploration** in ~5 % of slots: sample alternative titles whose predicted score is in the top-quartile but not the top-decile. Tracks `recommendation.feedback` to see if exploration leads to discoveries.

Trade-off: too much exploration confuses users; too little stagnates. We A/B-test the exploration rate quarterly.

### 14.6 Search (Elasticsearch, the boring-and-correct part)

Index ~50 K title docs with:

```
{
  "title_id": "t_avengers",
  "name": "The Avengers",
  "name_phonetic": "thyii avenjurz",            # for fuzzy matching
  "synopsis": "Earth's mightiest heroes...",
  "cast": ["Robert Downey Jr.", "Chris Evans", ...],
  "directors": ["Joss Whedon"],
  "genres": ["action", "scifi", "superhero"],
  "language_primary": "en",
  "available_countries": ["US", "IN", "UK", ...],
  "popularity_score": 9.7,                      # editorial + watch-derived
  "release_year": 2012,
  "tags": ["marvel", "mcu", "team-up"]
}
```

Query path:

```
GET /search?q=avengrs (typo!)&country=IN

→ Elasticsearch query:
   bool:
     must:    multi_match{ query: "avengrs", fields: [name^3, name_phonetic^2, cast^2, tags, synopsis], fuzziness: AUTO }
     filter:  available_countries: "IN"
     should:  rank_feature{ field: popularity_score, boost: 1.5 }
              rank_feature{ field: recency_decay, boost: 0.5 }
```

Returns top 20. p95 ≤ 200 ms (single-shard query, 5-shard parallel).

### 14.7 Voice search from TVs

TV remotes capture noisy ASR text. We feed it through a "did you mean" pipeline:
1. Lowercase + strip punctuation + collapse repeated vowels ("avennngggers" → "avengers").
2. Soundex / Metaphone hash → check phonetic match against a precomputed `phonetic_index`.
3. Edit-distance fuzzy match (≤ 2) against the top-1000 popular titles (so we prefer popular results when tied).
4. If high confidence → suggest `Did you mean: "The Avengers"?` in the UI.

---

## 15. Live Streaming (LL-HLS / LL-DASH / WebRTC)

Live is a different beast from VOD. Cache friendliness is bad (every segment is fresh; nobody pre-fetched anything 6 h ago), latency targets are tight (sports need ≤ 5 s glass-to-glass), and the audience is concentrated in a single window (the IPL final is a 4-h burst, not a Long Tail Day).

### 15.1 The live pipeline, top to bottom

```
[Production truck (OB van)]
   │  HD-SDI baseband → encoded RTMP (legacy) or SRT (preferred)
   │  primary-publish + standby-publish to load balancer
   ▼
[Live Ingest Gateway]                     -- active+standby, etcd lease
   │  validates stream key
   │  re-injects sequence numbers
   │  forwards to Live Transcoder
   ▼
[Live Transcoder]                         -- NVENC GPU, CMAF chunked
   │  encodes 5 rungs in parallel (240p..1080p)
   │  output: CMAF fragments of ~200 ms each
   │  keyframe interval 1 s
   ▼
[Live Packager]                           -- LL-HLS / LL-DASH
   │  emits partial segments
   │  EXT-X-PRELOAD-HINT every 200 ms
   │  CENC cbcs encrypted
   ▼
[Origin Shield (regional)]                -- collapse N edge requests
   │  HTTP/2 chunked transfer
   ▼
[Multi-CDN]                               -- short manifest TTL (1 s)
   │  ↓
[Player]                                  -- LL-HLS preload-hint client
       glass-to-glass ~3-5 s
```

### 15.2 LL-HLS in detail

LL-HLS (Apple's spec, 2020) extends HLS for low latency:

- **Partial segments** — A 6-second segment is split into ~30 partial segments of ~200 ms each. Each part has its own URL.
- **`EXT-X-PRELOAD-HINT`** — Manifest tells the player "the next part will be at URL X". The player fetches that URL via HTTP/2; the server holds the request open until the part is ready, then streams chunked.
- **Block-of-data delivery** — `EXT-X-PART-INF` (server-side) and `EXT-X-PART` (player-side) describe the part schedule; player can fetch parts before the parent segment is complete.
- **`EXT-X-RENDITION-REPORT`** — Cross-rung sync hints so the player can switch bitrate without re-buffering across rungs.

### 15.3 LL-DASH in detail

LL-DASH (DASH-IF Low-Latency Live Profile, 2020) is the parallel spec on the DASH side:

- **CMAF chunks** — Same CMAF source as LL-HLS. Each segment contains N chunks; the chunks are emitted progressively.
- **Chunked transfer encoding** — HTTP/1.1 or HTTP/2 chunked transfer; player can begin decoding before the segment is fully written.
- **`availabilityStartTime`** + `MPD@minimumUpdatePeriod` — Tight clock alignment with the server.
- **`producerReferenceTime`** — Maps the wall clock to the media timeline (sub-100 ms accuracy).

### 15.4 Glass-to-glass latency budget — live

| Stage                                                         | Target  | p95     | Notes                                                              |
|---------------------------------------------------------------|---------|---------|--------------------------------------------------------------------|
| Camera → encoder (truck)                                      | 100 ms  | 200 ms  | broadcast hardware                                                  |
| Truck → Live Ingest (RTMP/SRT, public internet)                | 200 ms  | 500 ms  | SRT is forgiving; RTMP can spike on packet loss                    |
| Live Ingest → Live Transcoder                                  | 50 ms   | 100 ms  | in-AZ                                                              |
| Live Transcoder (CMAF chunked encode)                          | 200 ms  | 500 ms  | one keyframe interval                                               |
| Live Packager (chunk emit)                                     | 100 ms  | 200 ms  | minimal buffering                                                   |
| Origin Shield → CDN propagation (chunked transfer)             | 500 ms  | 1 s     | depends on PoP density                                              |
| CDN → player (player buffer of ~1 s for jitter)                | 1 s     | 1.5 s   | LL-HLS recommends 0.5–2 s buffer                                    |
| Decoder warm-up                                                | 100 ms  | 200 ms  |                                                                    |
| **Total p95 glass-to-glass**                                   | **~2.3 s** | **~4.2 s** | Within the 5-s SLO                                                 |

The biggest variable is the **player buffer**. Phones with flaky cellular need 2–3 s buffer; smart TVs on fibre can run at 1 s. We let the player adapt.

### 15.5 Active/standby ingest (the only real failover)

The production truck publishes the same RTMP/SRT feed to **two ingest pods** (primary + standby). Both decode and forward; the **active pod's output flows downstream** based on an etcd lease (`/ott/live/{event_id}/leader`).

If the active pod fails:
- etcd lease expires in 3 s.
- Standby acquires lease.
- Standby's output is *already running*; Live Transcoder seamlessly picks up because the feed sequence is continuous.
- Glitch ≤ 1 s, usually invisible to the viewer.

If the **truck's publish** fails (rare):
- We had the truck publish via two paths (e.g., dual SAT uplinks); secondary takes over.
- Or, we fall back to a **slate** (placeholder graphic) and an audio "we'll be right back" loop.

### 15.6 DVR (rewind window)

Live transcoder also writes 6-s segments to S3 for the entire event duration (~4 h DVR window). Players asking for `?dvr=true` get a **non-realtime manifest** that exposes the last 4 h of segments; standard segment-fetch flow from there.

After the event, the DVR recordings are post-processed into a proper VOD title with the 5-rung ladder (using the catch-up encoder pool). The live event row in `titles` is then updated with the VOD asset_id, and the show becomes a permanent VOD entry.

### 15.7 Live ad insertion (SSAI for live)

Ad pods in live (e.g., the IPL "innings break") are pre-scheduled or signaled mid-stream via **SCTE-35 cue tones** in the input feed:

```
[truck] SDI feed contains SCTE-35 splice point → "ad break in 5 s"
   ▼
[Live Transcoder] passes SCTE-35 through to packager
   ▼
[Live Packager] at ad-break-time, replaces segments with ad segments
                manifest now points to ad CMAF chunks (also signed)
                ad chunks come from a different S3 prefix (so cache key differs)
   ▼
[Player] plays ads as part of the same manifest;
         CMCD reports "playing-ad" for analytics
```

Ad targeting: server-side per-user rewriting of ad prefix is *not* feasible at 50 M concurrent (would explode CDN cache). Instead, we serve **per-region per-cohort** ad pods (~1000 cohorts), so the cache key has 1000 variants instead of 50 M.

### 15.8 Why not WebRTC?

WebRTC promises sub-second latency. Why don't we use it for live?

- **Doesn't scale** to 50 M concurrent — every viewer needs a peer connection; SFU/MCU media-server farms become economic absurdity at this scale.
- **No DRM** — WebRTC's encryption is SRTP, not Widevine/FairPlay/PlayReady. Studios won't license premium content over WebRTC.
- **Browser-fragmented** — Safari WebRTC support has historically been inconsistent.

WebRTC is the right choice for *interactive* live (auctions, sports betting odds, live shopping) where sub-1 s matters and the audience is small (10 K–100 K). For broadcast-style live (sports, news, premieres) where 5 s is acceptable, LL-HLS / LL-DASH wins on scale, DRM, and cost.

---

## 16. Subscription, Billing & Concurrent-Stream Limits

This section is "the boring stuff that loses you the most money if you get it wrong."

### 16.1 Plans

| Plan       | Max devices | Max resolution | HDR  | Ads        | Price (US) | Price (IN) |
|------------|-------------|----------------|------|------------|------------|-------------|
| Basic      | 1           | 720p            | No   | SSAI       | $6.99      | ₹149       |
| Standard   | 2           | 1080p           | No   | None       | $15.49     | ₹499       |
| Premium    | 4           | 2160p (4K)      | Yes (HDR10 + Dolby Vision) | None | $22.99 | ₹649 |

The plan dictates three things:
1. **Max bitrate the manifest exposes** (we strip 4K rungs from the manifest for non-Premium).
2. **Concurrent-stream count** (Redis ZSET `streams:{user_id}` cardinality cap).
3. **DRM policy** (license blob includes `max_playback_resolution` + `hdcp_required`).

### 16.2 Payment provider matrix

| Channel                    | Provider       | Fee     | Lifecycle owner       | Notes                                                  |
|----------------------------|----------------|---------|------------------------|-------------------------------------------------------|
| Web (US, EU, ROW)          | Stripe         | ~2.9%   | Us                    | Hosted Checkout; SCA / 3DS                             |
| Web (India)                | Razorpay       | ~2%     | Us                    | UPI, Cards, Netbanking                                 |
| iOS app                    | Apple IAP      | 30% / 15% | **Apple**             | StoreKit; we never see the card; cannot cross-sell    |
| Android app                | Google Play    | 30% / 15% | **Google**            | Billing Library                                        |
| Amazon Fire / Fire TV      | Amazon IAP     | 30%     | **Amazon**            | DRM = also Amazon's at the device level                |
| Smart TV (LG, Samsung)     | Stripe / on-TV | ~2.9%   | Us                    | Bluetooth-pair-with-phone hand-off pattern             |

The **lifecycle owner** column is the gotcha. If a user signs up via iOS, the subscription's renewal, refund, cancel, and price-change events are all driven by Apple's webhooks. Apple's contract says we cannot direct that user to web Stripe to "save" them from the 30 % fee — that's an Apple TOS violation. Each user has a `payment_provider` locked at signup; the upgrade/downgrade UI routes them to their provider's flow.

### 16.3 The webhook + reconciliation pattern

Webhooks are *events*, not the source of truth. We treat them as triggers, but we periodically reconcile.

**Webhook handling (idempotent):**

```sql
INSERT INTO webhook_events (provider, provider_event_id, payload, received_at)
VALUES ('stripe', 'evt_1NABCXYZ', '{...}', NOW())
ON CONFLICT (provider, provider_event_id) DO NOTHING
RETURNING *
```

If RETURNING returns 0 rows, this is a redelivered webhook — no-op. Otherwise, parse the event and apply.

**Reconciliation (daily):**

```python
for sub in subscriptions where status='ACTIVE':
    provider_state = call_provider_api(sub.payment_provider, sub.provider_sub_id)
    if provider_state.status != sub.status:
        # webhook was lost; reconcile
        update_subscription(sub, provider_state)
        emit_subscription_event(...)
```

This catches the rare case where Stripe sent a webhook and our endpoint was down (and webhook retries didn't reach us).

### 16.4 The dunning / failed-payment state machine

```
ACTIVE  ──(payment_fails)──►  PAST_DUE  ──(retry_1, retry_2, retry_3 over 7 d)──►  SUSPENDED
                                  │              │
                                  │              └── if any retry succeeds → ACTIVE
                                  │
                                  └── user can log in but no plays (manifest 402)
                                      grace period 7 d still streaming
                                      day 8: fully suspended
                                      day 30: subscription CANCELLED, user must re-sub
```

Every state transition emits `subscription.events.{state_name}` to Kafka. Notification service sends emails / push at every transition.

### 16.5 Concurrent-stream enforcement (the hot path)

Already covered in §9.1 — the relevant invariant is:

> The **license issuance** is the gate. ZADD into `streams:{user_id}` happens *atomically* with the license mint. Subsequent license requests check ZCARD before adding.

```
Lua script (atomic):
  local now      = redis.call('TIME')[1]*1000 + tonumber(redis.call('TIME')[2]/1000)
  redis.call('ZREMRANGEBYSCORE', 'streams:'..user_id, '-inf', now-30000)  -- prune dead
  local n = redis.call('ZCARD', 'streams:'..user_id)
  if n >= max_devices then
    return { err = 'MAX_DEVICES_REACHED' }
  end
  redis.call('ZADD',   'streams:'..user_id, now, session_id)
  redis.call('EXPIRE', 'streams:'..user_id, 3600)
  return { ok = true }
```

Heartbeats refresh the ZSET score (keeps the entry alive). 30 s of no heartbeat → entry pruned, slot is freed.

### 16.6 Eviction UX

5th device tries to play → `MAX_DEVICES_REACHED`. The app shows:

> You're streaming on 4 devices. Stop one and play here?
> &#x25fc; **Stop one and play here**
> &#x25fc; Cancel

If user picks "Stop one":
1. Client retries with `?evict=true`.
2. Playback Svc pops the OLDEST entry in `streams:{user_id}`, mints a license for the new device.
3. WS Push Gateway sends `STREAM_EVICTED` to the old device's persistent WebSocket.
4. The old device's player polls `/heartbeat` 30 s later, gets `410 Gone`, stops playback with a banner.

### 16.7 Watch-party / multi-viewer features

Some OTTs offer "Watch Party" (Disney+ GroupWatch, Hulu Watch Party, Prime Video Watch Party). Implementation:
- Host starts a session; party members join via a shared link.
- Each member plays their **own** segments (own license, own stream-slot) — no media-server in the middle.
- A small **sync service** (WebSocket-based) coordinates `play / pause / seek` across clients with an authoritative host.
- Chat is a separate WebSocket overlay.

The clever part: each member's stream still counts against their *own* concurrent-stream limit, but **the host** doesn't get penalized for inviting friends. The license is per-user, the playback is per-user, only the *control signals* are shared.

---

## 17. Edge Cases & Gotchas

The interview-grade list of "things that bite at 3 AM during the IPL final."

### 17.1 The thundering herd at live event start

7:30 PM IST, IPL kickoff. 50 M users tap Play within 60 s. Even if everything's pre-warmed:
- 50 M license issuance requests in ~30 s = ~1.5 M req/s peak.
- 50 M manifest fetches at the CDN.
- Origin shield gets ~1 % miss → 500 K shield req/s.

**Mitigations:**
- **Manifest pre-positioning** — push the manifest to all PoPs 30 min before kickoff.
- **Stagger via push-notif** — schedule push notifications "match starts in 5 min" with a jittered window (10 s spread).
- **DRM license replicas** scaled to 2× expected peak ahead of time.
- **CDN capacity reservations** — pre-purchased Tbps with all 5 CDNs for the event window.
- **App-level backoff** — if license issuance returns 503, exponential retry with jitter (max 30 s).

### 17.2 The "VPN traveller"

User signs up in India (Premium ₹649), travels to UK on a VPN, plays a US-only Marvel film. What do we do?

Layered geo-checks:
1. **Account home country** (set at signup, locked for 90 d) — payment + plan price.
2. **IP country** (per request, MaxMind lookup) — content licensing window check.
3. **Device GPS** (mobile only) — corroborates IP; large mismatch = VPN.

Rule: `licensing_country = max(ip_country, account_country, device_gps_country)`. If any of those isn't licensed for this title in this licensing window → 403.

VPN detection: maintain a list of known datacenter IP ranges (AWS, Azure, GCP, OVH, M247…) → flag and apply stricter rules. False positives (Apple Private Relay, iCloud Private Relay) → degrade to "show available content" rather than block outright.

### 17.3 Device clock skew

A user's TV has a clock that's 6 h off. Manifests have `Expires=…`; signed URLs have an expiry too. If the device's clock is far ahead, it might think the URL has expired before it actually has, and refuse to play.

**Mitigation:**
- Manifest URLs have generous TTL (5 min) — covers most clock skew.
- Player uses **server-relative time** (the manifest contains a `program-date-time` tag the player can sync against).
- DRM license is **server-bound expiry**, not client-side; if the player's clock is off, the *server* refuses to renew.

### 17.4 The "subtitle ahead of audio" bug

Encoding produces a 10 ms drift between subtitle WebVTT and the audio track. Acceptable for 1 episode, infuriating across an entire season.

**Mitigation:**
- Subtitle ingest pipeline syncs subtitle timestamps to **audio waveform** (cross-correlation with speech detection).
- QC checks subtitle drift on sample frames.
- Player can adjust subtitle delay manually (settings).

### 17.5 The 4K-on-non-HDCP-screen problem

User has Premium, plays a 4K HDR title on a 4K TV that doesn't support HDCP 2.2 (e.g., older 4K TV with HDMI 1.4).

**Behavior:** DRM CDM enforces `hdcp_required = TYPE_1` from the license. The player gets a key request fail → falls back to 1080p rung in the manifest. User sees 1080p, not 4K. We don't tell them why directly (would expose DRM mechanism); the UI shows "Quality: 1080p".

### 17.6 Account-sharing detection

A user shares their Premium account with their parents, sister, friend across 3 cities. The 4-device limit doesn't catch this if they're not all watching simultaneously. Netflix lost ~$3 B/year to this.

**Detection signals:**
- IP address diversity (frequent plays from > 3 cities/month).
- Device-fingerprint diversity (> 6 distinct device_ids in 30 d).
- Login pattern (overlapping plays from 2 countries simultaneously).
- ASN pattern (residential vs commercial).

Action: page the user to **verify residence** (Netflix's "household" feature), or upgrade to a "with extra members" plan. We don't outright ban — it's a paying customer.

### 17.7 The "first frame stuck" bug

Player gets the manifest, gets the first segment, has the license… but no first frame. Symptom: black screen for 5 s, then either succeeds or fails.

**Causes:**
- Init segment (`init.mp4`) failed to download (CDN cache miss + slow origin).
- License is for a different KID than the segment claims.
- Decoder hardware not initialized (cold path on app launch).

**Mitigation:**
- **Init segment** is in the same CDN cache as media segments; it's small (~100 KB) and pre-warmed too.
- License issuance **validates KID match** against the manifest's `<ContentProtection>` / `EXT-X-KEY:KEYFORMAT="urn:..."`.
- App pre-warms decoder on home page load (well before user taps Play).

### 17.8 Live-event "cold connection" stampede

Backstory: 50 M users on iOS opening the app at 7:29 PM. All ~30 % of them have an expired session JWT. They all need to refresh.

**Impact:** API GW Auth Svc gets ~15 M refresh requests in 30 s = ~500 K req/s.

**Mitigation:**
- **Long refresh-token TTL** (30 d) so a user logging in occasionally doesn't need a refresh.
- **Refresh tokens cached in Redis**, lookup is sub-ms.
- **Auto-refresh on app foreground** — the app refreshes proactively when it comes to foreground, *before* the user taps Play. So by the time they tap, they already have a fresh token.

### 17.9 CDN blackholing

A specific PoP starts returning 200 OK but with corrupted bytes (memory corruption on the cache server). Player decodes garbage, drops frames, rebuffers.

**Detection:**
- Player computes a CRC on the segment; if the manifest contains an `EXT-X-PRELOAD-HINT` with an expected hash, mismatch → emit `cdn.qoe.error.corrupt`.
- Aggregate corrupt-error rate per (CDN × PoP) in Flink; if > 0.1 %, decommission the PoP from the CDN-router config until the CDN team confirms recovery.

### 17.10 The "watch-progress ping-pong"

User watches on TV (position 40:21), pauses, opens phone (sees position 40:21), plays for 10 s on phone (position 40:31), pauses, returns to TV. The TV's last heartbeat said 40:21; if we're not careful, the TV resumes at 40:21 instead of 40:31.

**Fix:** UPSERT logic uses `MAX(stored_position, incoming_position)` if they differ by less than 60 s, otherwise prefer the more recent `updated_at_ms`. This handles the "bookmarks crossing devices" pattern.

### 17.11 Subtitles on a 4K title via downscaled rendering

Some smart TVs downscale 4K to 1080p but still try to render 4K subtitle bitmaps — looks blurry. Mitigation: subtitles are TTML/WebVTT (vector + font), not bitmap; they render at the *display* resolution, not the source resolution.

### 17.12 Live event runs over its scheduled window

The IPL final goes into super-over. Originally scheduled 7:30–11:00 PM. At 11:30 we're still live. Issues:
- DVR window of 4 h is rolling forward; segments older than 4 h ago drop off (we lose the start of the match from DVR).
- The `live_event` row in `titles` had a `license_window_end_ms` of 23:00; users who tap Play at 23:30 are blocked.

**Mitigation:**
- Operations team can extend `license_window_end_ms` via the Editorial Console; change is propagated within 30 s.
- DVR window is configurable per-event; for marquee events, set to 8 h.

### 17.13 Studio-mandated content-replacement

A studio decides to pull a film at midnight (rights expired). We must:
- Stop new plays at exactly midnight.
- Allow already-playing sessions to **finish** their current title (DRM license expires at end of session).
- Withdraw from search and recommendations.

```
Editorial sets license_window_end_ms = midnight
   ▼
Catalog Svc emits catalog.events.title_withdrawn
   ▼
Search indexer removes the title
   ▼
Recommendation invalidates rows containing the title
   ▼
Playback Svc rejects new POST /play (license window expired)
   ▼
Already-playing licenses continue (TTL on Redis license:{session_id})
   ▼
Heartbeat succeeds; segments still served from CDN; user finishes the movie
```

### 17.14 The "user wants a refund for a glitched stream"

User hit rebuffering during the IPL final. They want a refund. Customer Service queries:

```sql
SELECT *
FROM viewing_events
WHERE profile_id = $1
  AND event_ts_ms BETWEEN $2 AND $3
  AND event_type IN ('REBUFFER', 'BITRATE_DROP', 'ERROR')
ORDER BY event_ts_ms;
```

Plus QoE telemetry from `clickhouse.qoe`. If we see > 5 % rebuffer ratio for that session, automatic 24-h refund eligible.

This is why we keep the audit/qoe pipeline detailed — it's the source of truth for service quality refunds, royalty disputes, and studio audits.

---

## 18. Security, DRM Compliance, Geo-Restrictions

OTT security is a layered onion: account, content licensing, device, network, audit.

### 18.1 Account security

- **2FA**: optional for retail, strongly recommended; mandatory for editorial / admin.
- **Password**: bcrypt with cost 12; pwned-password check (HIBP) on signup.
- **Refresh-token rotation**: on every refresh, issue a new refresh token; old token is invalidated in Redis.
- **Session revocation**: user can sign out all devices via account portal; Redis revokes all `session:{token_jti}` keys for that user.
- **Suspicious-login alerts**: new device / new country → email + push.

### 18.2 Per-region geo / licensing

| Layer            | Where            | Check                                                             |
|------------------|------------------|-------------------------------------------------------------------|
| Account country  | JWT claim        | locked at signup; matters for billing and "home country"          |
| Request IP country | MaxMind / IP DB | matters for content licensing window per title                     |
| Device GPS       | mobile SDK       | corroborates IP; large mismatch flagged                           |
| Title windows    | Postgres         | `available_countries`, `license_window_start_ms`, `_end_ms`       |
| VPN detection    | IP DB + heuristics | flag + degrade                                                    |

`Effective country = strictest of {ip, account, gps}`. License denied if title is not available in `effective_country` at `now()`.

### 18.3 Forensic watermarking

Studios audit OTTs for piracy. If a leaked copy is matched to our service, we can identify the leaker via **forensic watermarking** at the per-session level — embed a near-invisible mark (varying B-frame ordering, sub-pixel chroma) that survives re-encoding.

Implementation:
- Two **A/B versions** of each segment are encoded (mark = 0 or mark = 1).
- The `manifest_url` for a session interleaves A and B segments based on the `session_id`'s 64-bit pattern.
- Forensic recovery: extract the bit pattern from a leaked file → look up `session_id` → look up `audit.events` → find the user.

This adds ~0.5 % bandwidth overhead and is **the only real anti-piracy lever** for premium content (alongside DRM + signed URLs).

### 18.4 Compliance

| Regulation     | Region       | Implication                                                                                       |
|----------------|--------------|---------------------------------------------------------------------------------------------------|
| **GDPR**       | EU           | Right to erasure; explicit consent for cookies; data residency in EU                              |
| **CCPA**       | California   | "Do not sell my data" toggle; PII deletion requests within 45 d                                  |
| **DMCA**       | US           | Takedown workflow; counter-notice; safe-harbor as long as we act on notices                       |
| **Indian IT Rules 2021** | India | DPO appointment; user-grievance reply within 24 h; content labeling; CSAM removal within 24 h    |
| **COPPA**      | US           | < 13 y kids cannot have ad-targeted accounts; verifiable parental consent                         |
| **DPDP Act**   | India        | Data residency; consent manager integration                                                       |

### 18.5 PII encryption at rest

All PII fields in Postgres (email, phone, address, billing details) are encrypted with KMS:

```sql
-- Application-level encryption with KMS-derived key
INSERT INTO users (user_id, email_enc, phone_enc, ...)
VALUES ($1, pgp_sym_encrypt($2, key), pgp_sym_encrypt($3, key), ...)
```

The KMS key is in HSM; rotated every 90 d. Read path decrypts in memory; never logs PII.

### 18.6 Network security

- **TLS 1.3** everywhere; 0-RTT for return visitors.
- **Certificate pinning** in mobile apps (defeats MITM via compromised CA).
- **mTLS** between internal services in the cluster (Istio / Linkerd).
- **Zero-trust** on the ops VPN — every internal admin op is per-request authenticated.
- **WAF** at CDN edge (block obvious abuse: SQLi, XSS, scrapers).

### 18.7 Audit logging — the studio audit

`audit.events` topic, S3 WORM bucket, 7-year retention. Every play emits:

```json
{
  "event_id": "ae_...",
  "ts_ms": 1714665600000,
  "type": "license_issued",
  "title_id": "t_...",
  "profile_id": "p_...",
  "user_id": "u_...",
  "device_id": "d_...",
  "drm_system": "widevine",
  "session_id": "s_...",
  "ip_country": "IN",
  "license_policy": { "max_resolution": 2160, ... },
  "signing_cert_id": "delegate_2026-05-01_ap-south"
}
```

Sigma OTT / studio auditors query this monthly. Royalty calculations build on it. DMCA investigations build on it. Forensic watermark recovery joins on `session_id`.

### 18.8 Kill-switches

Operations needs the ability to:
- **Block a user** (e.g., fraud detected): sets `users.status='SUSPENDED'`; subsequent /play returns 401.
- **Withdraw a title** globally: sets `available_countries=[]`; all license windows close.
- **Disable a CDN**: removes weight from etcd config; new plays route around it.
- **Revoke a delegate cert**: marks it in `revoked_delegates` table; license server checks this on every issuance.
- **Pause new sign-ups**: feature flag in etcd.

All kill-switches are change-controlled, audited, and tested in chaos engineering drills.

---

## 19. Observability & QoE Telemetry

You can't ship 99.99 % availability without seeing what's happening, in seconds, across 250 M devices.

### 19.1 The three QoE pillars

1. **TTFF — Time-To-First-Frame.** From "tap Play" to "first decoded frame on screen." Measured client-side, beaconed in `viewing.events.PLAY_START_TIMING`.
2. **Rebuffer ratio.** `Σ rebuffer_ms / Σ play_time_ms` per session, per region, per CDN, per device. SLO < 0.4 %.
3. **Average bitrate served.** Higher is better (we paid for higher rungs); much higher than expected might indicate plan-policy bypass; much lower indicates poor CDN / network / device.

Plus: **error rate** (license errors, segment 404, decoder errors).

### 19.2 Beacon protocol

Every player ships a small SDK that beacons every 10 s (and on key events: PLAY_START, PAUSE, REBUFFER_BEGIN, REBUFFER_END, BITRATE_CHANGE, SEEK, ERROR, COMPLETE). The beacon goes to a stateless **Beacon Collector** service (separate from the API GW path) that produces to `viewing.events` and `cdn.qoe`.

The beacon endpoint is a small `POST /beacon` that takes a batched JSON array of events. Stateless writes to Kafka; ~5 ms p95.

### 19.3 Dashboards

- **Grafana** — operator dashboard with TTFF p50/p95/p99, rebuffer ratio, error rate, all sliced by region, CDN, device type, plan tier.
- **Studio Analytics** — viewership reports per title per region, watch-time minutes per title per day, drop-off curves (e.g. "65 % of viewers stop watching at episode 4").
- **Real-time live event** — special dashboard for live events: concurrent stream count by region, CDN distribution, glass-to-glass latency, ingest health.

### 19.4 Alerting

Pages on:
- TTFF p95 > 3 s for any (region × CDN × device) combination, > 5 min.
- Rebuffer ratio > 1 % for any region, > 5 min.
- License-issuance error rate > 1 % for any DRM, > 3 min.
- Encoder backlog > 6 h for P0 (live) or P1 (new release).
- Kafka consumer lag > 60 s on any hot topic.
- Postgres replication lag > 30 s.
- Cassandra hint accumulation > 10 GB on any node.

### 19.5 Tracing

OpenTelemetry; every API request gets a trace_id. The trace flows through:
API GW → Playback Svc → Postgres → Redis → DRM Svc → HSM → Kafka producer → Beacon Collector. Jaeger backend stores 7 d hot, S3 cold for the rest.

### 19.6 Chaos engineering

We run quarterly drills:
- Kill a CDN region (disable Akamai-IN entirely) — check that QoE-driven failover routes traffic to other CDNs within 60 s.
- Kill a Cassandra rack — ensure eventual consistency holds and cross-rack repair does not cascade.
- Kill the master HSM — ensure pre-signed delegate certs continue issuance for ≥ 6 d.
- Kill the live-ingest active pod mid-event — measure failover latency (target < 3 s).

### 19.7 Royalty calculation pipeline

Studios are paid based on watch minutes per title per region per month. The pipeline:

```
Cassandra viewing_events (last 30 d hot)
   │  Spark batch nightly
   ▼
royalty.daily.{title_id, region, day}
   │  → ClickHouse for studio dashboard
   │  → CSV export to studio per contract terms
   ▼
royalty.monthly.{title_id, region, month}
   │  →  per-studio aggregation per contract rate
   ▼
royalty.payable.{studio_id, month}
   │  →  finance system → ACH / SWIFT payment
```

This is one of the most-audited pipelines in the entire OTT — studios will sue if numbers don't match. We maintain **two parallel implementations** (one in Spark, one in Flink-batch) and reconcile monthly; > 0.01 % delta = page on-call.

---

## 20. Technology Choices — Final Verdict

| Concern                       | Choice                                        | Why                                                                                                  |
|-------------------------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------|
| Catalog OLTP                  | **Postgres** (multi-region read replica)      | Editorial writes are low; reads cached in Redis; schema-rich; mature                                  |
| Watch-progress / events       | **Cassandra**                                 | 700 K writes/s, partition-friendly, append-mostly, eventual cross-region                              |
| Hot cache                     | **Redis Cluster**                             | Sub-ms reads for sessions, license, streams ZSET, reco rows                                           |
| Search                        | **Elasticsearch / OpenSearch**                | Multi-language tokenisers, fuzzy + ANN, well-understood                                               |
| Async backbone                | **Kafka** (RF=3, idempotent producer)         | Exactly-once-into-store via outbox; tiered storage; mature ecosystem                                  |
| Real-time stream              | **Apache Flink**                              | Exactly-once windowing for QoE/concurrent-stream/CDN routing; mature ML feature pipelines             |
| Batch ML                      | **Apache Spark**                              | Recommendation training, royalty calc, feature pipelines                                              |
| OLAP / dashboards             | **ClickHouse / Druid**                        | Sub-second queries on billions of QoE events                                                          |
| Object storage (mezz)         | **S3 (3-region · WORM)**                      | 11-9s durability, the only irreplaceable artifact                                                     |
| Object storage (packaged)     | **S3 (single-region · cross-region replica)**  | Recoverable from mezz; cheaper class                                                                  |
| CDN                           | **Akamai + CloudFront + Cloudflare + Limelight + Open-Connect appliances** | Multi-CDN is non-negotiable; OC handles long-tail egress                                              |
| DRM                           | **Widevine + PlayReady + FairPlay (CENC cbcs)** | Studio-mandated; one ciphertext, three license servers                                                |
| HSM                           | **Cloud HSM / Thales** (FIPS 140-2 L3)        | Studio compliance; daily delegate signing                                                             |
| Encoder                       | **NVIDIA NVENC (GPU) + AWS MediaConvert**     | GPU for live + new releases; managed for catch-up                                                     |
| Packager                      | **Shaka Packager / Bento4**                   | CMAF-native, HLS+DASH from one source                                                                 |
| Live transcode                | **FFmpeg + NVENC** (chunked CMAF)             | Industry-standard, low-latency encoding                                                               |
| Live ingest                   | **SRT (preferred) / RTMP (legacy)**           | SRT is forgiving over public internet                                                                 |
| Player platforms              | **ExoPlayer (Android), AVPlayer (iOS), Shaka / hls.js (Web), platform-native (TV/console)** | DRM is platform-specific; one player per platform                                                     |
| Recommendation                | **TensorFlow / TF-Lite + Spark for training** | Mature, GPU-friendly, TF-Lite for online re-rank in <5 ms                                             |
| Workflow orchestrator         | **Argo Workflows / Temporal**                  | DAG executor for transcoding, native Kubernetes                                                       |
| Coordination                  | **etcd / Consul**                             | Live-event leader, multi-CDN weights, kill-switches                                                   |
| Notification                  | **FCM + APNS + SES + Twilio**                 | Standard stack; idempotent on event_id                                                                |
| Auth                          | **OAuth2 / OIDC** (custom IdP)                | SSO with Apple/Google/Facebook; refresh tokens 30 d                                                   |
| Service runtime               | **Go for hot-path (Playback Svc, Beacon), Java for heavyweight (Subscription, Catalog)** | Go for low GC; Java for ecosystem (Stripe, Razorpay clients)                                          |
| Service mesh                  | **Istio (mTLS, traffic shifting)**             | Zero-trust between services                                                                           |
| API gateway                   | **Envoy (multi-region active-active)**        | High-performance L7 LB                                                                                |
| Observability                 | **Prometheus + Grafana + OpenTelemetry + Jaeger** | Standard CNCF stack                                                                                   |

### 20.1 Summary trade-offs

The architecture is **pessimistic on the hot path** (every play hits Postgres + Redis + DRM + Kafka before serving) and **optimistic on the bulk path** (segments served from CDN with a 5-min signed URL). The pessimism is the cost of correctness — concurrent-stream limits, regional licensing, DRM compliance — that studios contractually require. The optimism is the cost of scale — we can't push 250 Tbps through our origin during a live final.

**One-liner for the interview:** "OTT is a CDN-first system. The CDN serves 95 % of bytes; our app servers see ~5 % of traffic, and that 5 % is *all* high-correctness state — subscription, concurrent-stream, DRM license, geo-licensing, audit."

---

## 21. Q&A Defense — Top 25 Tough Interview Questions

> Use this as a rapid-fire defense checklist before the interview. Each answer is self-contained.

### Round 1 — Playback correctness & DRM

**Q1. A user shares their Premium account with their parents in a different city. Both watch on 4 devices simultaneously. The user has 4-device limit. What stops the 5th, 6th, 7th, 8th device?**

**A.** The Redis ZSET `streams:{user_id}` enforces a hard cap at license issuance. When device 5 tries to play, the Lua atomic block sees `ZCARD ≥ max_devices` and returns `MAX_DEVICES_REACHED` *before* a license is minted. No license = no decryptable segments. The CAS guarantees that even if devices 5, 6, 7, 8 hit four different Playback-Svc pods at the same nanosecond, only at-most-one of them can succeed (Redis Lua is single-threaded per key). Soft eviction ("kick the oldest") gives a tolerable UX. Beyond that, we use account-sharing-detection signals (IP / ASN / device-fingerprint diversity) and prompt for "household verification" — rather than outright ban, since the user is paying.

**Q2. The DRM license is cached on the device for 24 h. The user downgrades from Premium to Basic at hour 6. They keep watching 4K. Is that a bug?**

**A.** It's a **deliberate consistency trade-off** — we don't claw back already-issued licenses. The mitigation: license TTL is short enough (24 h max, often 8 h) that the next play will mint a fresh license from the new policy. For "real-time downgrade enforcement," we'd need to track and revoke per-license, which would 100× the DRM server load and is **not** required by any studio contract. If the use case demanded it, we'd shorten the license TTL to 1 h on plan-change events.

**Q3. Two viewers at home both watch on the same TV (account-sharing within a household). They want to play different titles on the same TV's two HDMI ports. Does the architecture support that?**

**A.** Yes — concurrent-stream count is keyed by `device_id`, not `(device_id, profile_id)`. A single TV with two HDMI ports = two device_ids registered to the platform = two concurrent streams = two slots in `streams:{user}`. Premium plan allows 4 concurrent → fits.

**Q4. During the IPL final, the master HSM fails. What happens to the next 100 M license requests?**

**A.** Nothing visible to users for ≥ 6 days. Each region holds 6 days of pre-signed delegate certificates; the regional license servers continue minting licenses signed by the current delegate. We can't *rotate* delegates until master HSM is recovered, but issuance continues. Beyond 6 days, we'd run out of delegates and licensing would degrade region-by-region. We'd page vendor (CloudHSM / Thales), failover to the secondary HSM cluster, or in the very-worst case, temporarily lower the resolution policy and serve plain-DRM for non-premium content while the studio-mandated tiers wait.

**Q5. A pirate captures a 4K segment and the license blob from their Widevine-L3 (software TEE) Android device. Can they decrypt and re-distribute?**

**A.** No, not at meaningful scale:
1. The license is **wrapped with the device's public key**; only that device's TEE can unwrap it.
2. The decrypted content key never leaves the TEE — the OS user-space cannot read it.
3. L3 (software TEE) is contractually capped to 720p in the license policy; 4K requires L1 (hardware TEE), which is far harder to extract from.
4. Even if they recover the key for one segment, our **forensic watermarking** identifies their `session_id` — we revoke their account, file with the studio, and (in egregious cases) law enforcement.

The realistic threat is *capture from screen* (HDMI hijacking, screen recording at the OS level), and HDCP defeats most of those. The remaining "phone camera at the TV" attack is unfixable but produces poor-quality copies that don't compete commercially.

### Round 2 — Latency & scale

**Q6. The live IPL final has 50 M concurrent streams. How does the architecture not melt?**

**A.** Three levers:
1. **Multi-CDN with origin-shielding.** 5 CDNs × 100 PoPs × 250 GB working set = ~125 TB hot cache. Live segments are short (200 ms) but there are many of them; Origin Shield collapses N edge requests → 1 origin pull. We pre-purchased Tbps with each CDN ahead of the event.
2. **License-server scale.** Regional delegates allow ~3 M signatures/s aggregate; we scale license-server pods 4× ahead of the event.
3. **App-side staggering.** Push notifications schedule with jittered "match starts in 5 min" to spread the 50 M tap-Play within a 30-s window.

The architecture's 99th-percentile failure mode at this scale is **license-server burst capacity**, *not* segment delivery (which scales horizontally with CDN spend). Pre-warming is the difference between meeting SLO and not.

**Q7. TTFF p95 budget is 2 s. Can you justify each step?**

**A.** Yes — see §5. The non-negotiable floors: 200 ms mobile RTT to the nearest API GW PoP, 400 ms DRM license challenge round-trip, 600 ms first segment fetch (CDN cache hit). Adding API GW (20 ms), Playback Svc (30 ms), manifest parsing (300 ms), decoder warm-up (200 ms), and a 200 ms safety margin = ~1.95 s. The dominant variable is **CDN cache hit ratio**; pre-warming to 95 %+ is the single most important knob.

**Q8. Why Cassandra for watch-progress, not Postgres?**

**A.** 700 K writes/s sustained, 5 M/s peak. Postgres WAL chokes around 50 K writes/s/shard; we'd need 10–100× shard count, painful operations, and the queries are still single-partition lookups (`profile_id` → progress on a title). Cassandra with `PRIMARY KEY ((profile_id), title_id)` partitions cleanly, RF=3 across AZs, LCS compaction, default TTL 90 d. Single 30-node ring handles it on commodity hardware. This is the textbook Cassandra workload — and Postgres for the catalog (50 K rows, schema-rich, low-write) is the textbook Postgres workload.

**Q9. The VOD play hot path goes API GW → Playback Svc → Postgres → Redis → DRM → Kafka, then back. Why so many hops?**

**A.** Each hop is **necessary for correctness or compliance**:
- API GW: rate-limit + auth (security).
- Playback Svc: subscription + geo + concurrent-stream + license issuance.
- Postgres (read replica): title metadata + licensing window check.
- Redis: concurrent-stream CAS + license cache.
- DRM: signs the per-session license blob.
- Kafka: fire `viewing.events.PLAY_START` for analytics + audit.

We **can't skip Postgres** (license window check is per-title), can't skip Redis (concurrent-stream truth), can't skip DRM (studio mandate). The total is ~30 ms p95 — acceptable on the path, and amortized over a 2-hour movie.

### Round 3 — Multi-CDN & live

**Q10. How do you decide which CDN to send each user to?**

**A.** Server-side primary + client-side fallback.
- Flink consumes `cdn.qoe` in 30-s windows; computes a score per `(CDN × region × device)`: `w1·throughput - w2·rebuffer - w3·err - w4·cost`.
- Writes weights to etcd.
- Playback Svc reads weights on every `POST /play`; weighted-random selection of CDN; manifest URL signed for that CDN.
- Capacity caps (e.g. "akamai at 85 % of contracted Tbps") trigger spillover.
- Player-side fallback: if a streak of 3 segment errors on the chosen CDN, the player switches to the alternate URL embedded in the manifest's `EXT-X-MEDIA` `RENDITION-REPORT`.

**Q11. A specific PoP starts returning corrupted bytes (~0.5 % errors). How fast can you cut it out?**

**A.** ~60 s.
- Player CRCs incoming segments; mismatch emits `cdn.qoe.error.corrupt` to Kafka.
- Flink aggregates per-PoP error rate in 30-s windows.
- Threshold (e.g. 0.1 % corrupt) triggers an alert + automatic decommission: Flink job writes the PoP to a **blacklist** in etcd.
- Multi-CDN router excludes blacklisted PoPs; new manifests route around it.
- Existing sessions: client-side fallback after 3 errors switches CDN.

**Q12. Live ingest is active+standby. Failover takes 3 s. What's visible to the viewer?**

**A.** Glitch ≤ 1 s — usually invisible. The standby pod was already encoding the same input (the truck dual-publishes to a load balancer that fans out to both); only the *output* was suppressed. On primary failure, etcd lease expires in 3 s, standby acquires lease, output flows downstream. Live Transcoder sees a continuous sequence (the standby was always there). The CDN's chunked-transfer connection might break and reconnect; the player reconnects within ~1 s using `EXT-X-PRELOAD-HINT`. Net: a single missed 200-ms partial segment → invisible.

### Round 4 — Recommendation & search

**Q13. Why offline pre-compute home rows? Why not real-time?**

**A.** 10 M home-page loads/day × ~50 ms ranker call × ~50 KB feature vector = ~25 GB/s of memory bandwidth on the inference path. Real-time would require a feature store hot-path with ~10 M users × ~1 KB features = 10 GB hot, plus a ~50 ms DNN inference per page. We pre-compute the 12 rows nightly into Redis (~50 GB), serve via HGETALL in ~5 ms, and **online re-rank only the top 10** of each row using a small TF-Lite model (~5 ms). Net: trade 5 ms of online compute for 100× cost reduction.

**Q14. Cold start: a user signs up at 8 PM and opens the home page at 8:01 PM. What do they see?**

**A.** The fallback row: country/language popularity (`fallback:{country}:{language}`) + Top 10 + an "Onboarding survey" row that asks them to pick 3 favourite genres. After the survey, we bootstrap their embeddings to the genre centroids. Once the offline pipeline runs (next nightly batch), they get personalized rows. For the first 24 h they see hybrid (popularity + genre-centroid).

**Q15. The Top 10 row says "#1 in India today" — can it be wrong?**

**A.** Yes, by ~1 hour. We compute Top 10 from `viewing.events` aggregated in Spark every 1 hour; before that, the previous hour's snapshot is shown. For real-time accuracy (e.g. "trending now in the last 5 min" during a live event), we'd swap to a Flink streaming job updating a real-time row in Redis every 60 s. We don't do this universally because the CPU cost outweighs the perceived freshness benefit.

### Round 5 — Subscription & billing

**Q16. A user pays via Apple-IAP on iPhone, then opens our web app and tries to "upgrade" via Stripe. What happens?**

**A.** The web upgrade flow checks `users.payment_provider`. If it's `apple_iap`, the UI redirects to "Manage your subscription in the App Store" — we cannot legally bill them via Stripe (Apple TOS owns the lifecycle). The user has to upgrade via iOS, where StoreKit handles the proration. Apple's webhook then arrives → we update `subscriptions.plan = PREMIUM`.

**Q17. Stripe sends a `customer.subscription.deleted` webhook, but our endpoint was down. The user keeps streaming. How do we catch up?**

**A.** Daily reconciliation. A scheduled job iterates all `ACTIVE` subscriptions and queries the provider's API; any divergence (provider says cancelled, we say active) is fixed by emitting the missed event to our Kafka topic, then the normal webhook-handling path runs. Worst case: ~24 h of "free streaming" for a cancelled user. Acceptable trade-off vs. operating perfectly-reliable webhook ingest.

**Q18. The user's payment fails. They keep streaming during the dunning grace period. Then we suspend. They re-pay. How quickly can they stream again?**

**A.** ~30 s. Payment success → Stripe webhook → Subscription Svc UPSERT → Outbox → Kafka `subscription.events.ACTIVATED` → Notification Svc + Cache invalidator. The cache invalidator immediately updates Redis `subscription:{user_id}` and the next play uses the fresh state. JWT refresh on next foreground brings the new plan.

### Round 6 — Operations

**Q19. The marquee Marvel film drops at midnight. What's the runbook?**

**A.**
- T-7 days: editorial publishes the title → `catalog.events.title_published` → Search indexer updates → Recommendation pipeline begins boosting it in `Top Picks`.
- T-24 hours: CDN Pre-warmer pushes the first 10 % of segments (~12 PB across all CDNs and PoPs) to all caches.
- T-1 hour: scale up Playback Svc + DRM License Svc by 2×.
- T-30 minutes: send "premiere starts in 30 min" push notif with jittered window.
- T-0: title becomes available; license window opens.
- T+1 minute: monitor TTFF p95, rebuffer ratio, license errors. If any anomaly, page on-call.

**Q20. Cassandra hint accumulation > 10 GB on one node. What does that mean and what do you do?**

**A.** A node was offline; its peers stored "hints" (writes intended for it) and will replay on rejoin. >10 GB means the node has been down for hours and writes are piling up — risk of disk full, risk of slow replay on rejoin. Action: bring the node back online (or, if irrecoverable, decommission and bootstrap a replacement); during replay, monitor cluster CPU and stream throughput. We auto-throttle hint replay to 50 MB/s/node to avoid impacting live writes.

**Q21. The encoder pool is backlogged 6 hours on P1 (new release) priority. What do you do?**

**A.**
1. **Spillover** to AWS MediaConvert (it's slower and pricier but elastic) for the next 6 hours.
2. **Demote P2 (catch-up)** to nighttime spot-instance only, freeing GPU for P1.
3. **Profile** — is it CPU-bound or GPU-bound? Often the bottleneck is HDR tone-mapping (CPU-only). Reroute those rungs to a CPU-rich pool.
4. Operations alarm if backlog still > 6 h after 30 min.

### Round 7 — DR & extensions

**Q22. The primary region's Postgres goes down. How long are you down?**

**A.** ~5 min.
- API GW health-checks fail-over to secondary region; reads route to its read-replicas (which are async-replicated, ≤ 30 s lag).
- For writes (catalog edits, subscription mutations), we promote a replica to primary in the secondary region; this is a managed-RDS operation, ~5 min.
- Already-playing sessions are unaffected (their licenses are cached, segments served from CDN).
- New plays: the title metadata is read-cached in Redis with 1 h TTL, so we can serve plays even if Postgres is down for ~1 h.

The architecture is **read-resilient by design** — the most painful case is if both Postgres regions are simultaneously down, which requires a multi-region disaster.

**Q23. Studios want a "premiere window" — only Premium users can watch for the first 14 days. How would you build that?**

**A.** Add `premiere_window_end_ms` to the `titles` row. Playback Svc's "subscription / geo / window" check adds: `if now < premiere_window_end_ms AND plan != PREMIUM → 402`. The catalog page filters out the title from non-Premium home rows, or shows it with an "Upgrade to Premium" upsell. After the premiere window, the title becomes generally available (no plan restriction).

**Q24. Add support for "Watch Together" (host + 3 friends synced playback). Top-level approach?**

**A.** Three new components:
1. **Sync Service** — WebSocket-based; the host's player publishes `play / pause / seek` events; members' players subscribe and apply the same actions.
2. **Per-user license** — each member still has their own license, their own concurrent-stream slot, their own QoE telemetry.
3. **Chat overlay** — a separate WebSocket channel for messages.

The trick: the sync service should be **eventually consistent** with a leader (the host). Network jitter causes some members to lag by ~500 ms; we don't try to sub-second-sync — that requires WebRTC and isn't worth the complexity for a "let's all watch together" UX.

**Q25. We want to launch in a new country (e.g., Brazil). What's the engineering checklist?**

**A.**
1. **Regional infrastructure** — provision Postgres replica, Cassandra DC, Kafka, Redis cluster, Origin Shield, DRM license server in the new region.
2. **Catalog licensing** — negotiate per-title rights with studios for Brazil; backfill `available_countries` in `titles`.
3. **Payment provider** — integrate the local equivalent (Mercado Pago, Pix); add to Subscription Svc; multi-currency in the plan price table.
4. **Languages** — add Portuguese (BR) to ICU tokenisers in Search; UI translation; subtitle / dub tracks; ASR for voice search.
5. **Compliance** — LGPD (Brazil's GDPR); local data residency requirements; ANATEL or equivalent regulator if any.
6. **CDN PoPs** — add São Paulo, Rio coverage; pre-position content based on Brazilian-language preferences.
7. **Recommendation cold-start** — seed with "Top picks for Brazil" editorial picks for the first month.
8. **Operations** — local on-call coverage in BRT timezone; Portuguese-speaking customer support.
9. **Marketing / pricing** — region-specific plans (often lower than US), local currencies, local payment methods.

The technical work is ~3 months; the licensing and compliance work is the long pole, often ~12 months.

---

## 22. Acronyms & Abbreviations — Cheat-Sheet

> Every acronym in this document, expanded once, with a forward-link to the in-depth coverage.

| Acronym | Meaning | First / Key reference |
|---------|---------|------------------------|
| **OTT** | Over-The-Top — internet-delivered video, bypassing traditional broadcasters | [§1](#1-problem-statement) |
| **VOD** | Video On Demand — pre-encoded titles played any time | [§1](#1-problem-statement) |
| **SVOD** | Subscription VOD — Netflix, Prime, Disney+ | [§1](#1-problem-statement) |
| **AVOD** | Advertising-supported VOD — JioCinema free tier, Tubi | [§1](#1-problem-statement) |
| **TVOD** / **EST** | Transactional VOD / Electronic Sell-Through — rent / buy on Apple TV | [§1](#1-problem-statement) |
| **ABR** | Adaptive Bitrate (streaming) — multiple rungs, switched on the fly | [§3](#3-requirements), [§10.2](#102-ladder-design) |
| **HLS** | HTTP Live Streaming (Apple) | [§10](#10-video-ingest--transcoding-pipeline) |
| **DASH** | Dynamic Adaptive Streaming over HTTP (MPEG) | [§10](#10-video-ingest--transcoding-pipeline) |
| **LL-HLS** | Low-Latency HLS — partial segments, preload hints | [§15.2](#152-ll-hls-in-detail) |
| **LL-DASH** | Low-Latency DASH | [§15.3](#153-ll-dash-in-detail) |
| **CMAF** | Common Media Application Format — single fragmented MP4 for HLS+DASH | [§10.4](#104-why-cmaf-is-non-negotiable) |
| **DRM** | Digital Rights Management | [§13](#13-drm-license-issuance--content-protection) |
| **CDM** | Content Decryption Module — the DRM client in the player | [§13.2](#132-cenc-common-encryption--cenc-vs-cbcs) |
| **CENC** | Common Encryption (ISO/IEC 23001-7) | [§10.5](#105-drm-packaging), [§13.2](#132-cenc-common-encryption--cenc-vs-cbcs) |
| **PSSH** | Protection System Specific Header — DRM init data in a segment / manifest | [§10.5](#105-drm-packaging) |
| **KID** | Key ID — 16-byte identifier per content key | [§10.5](#105-drm-packaging) |
| **EME** | Encrypted Media Extensions — browser API for DRM playback | [§3](#3-requirements) |
| **MSE** | Media Source Extensions — browser API for ABR | [§3](#3-requirements) |
| **CDN** | Content Delivery Network — geographically distributed cache fleet | [§12](#12-cdn-multi-cdn--edge-caching-strategy) |
| **PoP** | Point of Presence — a single CDN cache site | [§12.2](#122-cache-hierarchy) |
| **CMCD** | Common Media Client Data — player annotations on segment requests | [§12.5](#125-pre-fetch-hints-cmcd) |
| **QoE** | Quality of Experience — rebuffer ratio, throughput, error rate | [§19](#19-observability--qoe-telemetry) |
| **SSAI** | Server-Side Ad Insertion — manifest-level ad stitching | [§3](#3-requirements), [§15.7](#157-live-ad-insertion-ssai-for-live) |
| **HDR** | High Dynamic Range — 10-bit, wider colour gamut (HDR10, Dolby Vision) | [§10.2](#102-ladder-design) |
| **HDCP** | High-bandwidth Digital Content Protection — over-HDMI link encryption | [§13.4](#134-license-policy--whats-in-the-blob) |
| **TEE** | Trusted Execution Environment — the hardware key store on the device | [§13.2](#132-cenc-common-encryption--cenc-vs-cbcs) |
| **HSM** | Hardware Security Module — FIPS-140-2-compliant key store | [§13.7](#137-hsm-operations--key-custody) |
| **VMAF** | Video Multi-method Assessment Fusion — Netflix's perceptual quality metric | [§10.8](#108-qc-quality-control) |
| **NVENC** | NVIDIA's hardware video encoder | [§10.3](#103-per-title-encode-time) |
| **AAC** | Advanced Audio Coding — common audio codec | [§10.6](#106-subtitle-and-audio-tracks) |
| **WebVTT** | Web Video Text Tracks — modern subtitle format for HLS | [§10.6](#106-subtitle-and-audio-tracks) |
| **TTML** | Timed Text Markup Language — subtitle format used in DASH | [§10.6](#106-subtitle-and-audio-tracks) |
| **SCTE-35** | Society of Cable & Telecommunications Engineers ad-marker spec | [§15.7](#157-live-ad-insertion-ssai-for-live) |
| **RTMP** | Real-Time Messaging Protocol — Adobe-era ingest | [§15.1](#151-the-live-pipeline-top-to-bottom) |
| **SRT** | Secure Reliable Transport — modern UDP-based ingest | [§15.1](#151-the-live-pipeline-top-to-bottom) |
| **SDI** | Serial Digital Interface — broadcast-baseband video link in OB vans | [§15.1](#151-the-live-pipeline-top-to-bottom) |
| **DVR** | Digital Video Recorder — rewind window during a live event | [§15.6](#156-dvr-rewind-window) |
| **TTFF** | Time-To-First-Frame — tap-Play to first-decoded-frame | [§5](#5-the-latency-budget--play-start-ttfp), [§19.1](#191-the-three-qoe-pillars) |
| **TTFP** | Time-To-First-Play (often used interchangeably with TTFF) | [§5](#5-the-latency-budget--play-start-ttfp) |
| **RPO** | Recovery Point Objective — max acceptable data loss in a disaster | [§3](#3-requirements) |
| **RTO** | Recovery Time Objective — max acceptable downtime in a disaster | [§3](#3-requirements) |
| **RF** | Replication Factor (Cassandra) — number of replicas of each piece of data | [§7.9](#79-sample-cassandra-schema--watch_progress) |
| **CAS** | Compare-And-Swap (Redis Lua / Postgres SERIALIZABLE) | [§9.1](#91-playback-service--the-gatekeeper) |
| **CDC** | Change Data Capture — Debezium-style log-tailing | [§9.6](#96-subscription--billing-service) |
| **MAU / DAU** | Monthly / Daily Active Users | [§4](#4-capacity-estimation-back-of-the-envelope) |
| **IAP** | In-App Purchase — Apple's / Google's billing channel | [§16.2](#162-payment-provider-matrix) |
| **OIDC** | OpenID Connect — OAuth2 identity layer | [§3](#3-requirements) |
| **JWT** | JSON Web Token — signed bearer credential | [§5](#5-the-latency-budget--play-start-ttfp) |
| **SCA** | Strong Customer Authentication — EU PSD2 mandate (3DS) | [§16.2](#162-payment-provider-matrix) |
| **GDPR** | General Data Protection Regulation (EU) | [§18.4](#184-compliance) |
| **CCPA** | California Consumer Privacy Act | [§18.4](#184-compliance) |
| **DMCA** | Digital Millennium Copyright Act (US) | [§18.4](#184-compliance) |
| **DPDP** | India's Digital Personal Data Protection Act | [§18.4](#184-compliance) |
| **WAF** | Web Application Firewall (at CDN edge) | [§18.6](#186-network-security) |
| **mTLS** | mutual TLS — both client + server authenticate via certificates | [§18.6](#186-network-security) |
| **WORM** | Write-Once-Read-Many — immutable audit storage | [§18.7](#187-audit-logging--the-studio-audit) |
| **MMR** | Maximal Marginal Relevance — diversity re-ranking | [§14.1](#141-the-two-stage-recommendation-pipeline) |
| **ALS** | Alternating Least Squares — collaborative filtering | [§14.2](#142-offline-pipeline-spark-nightly) |
| **ANN** | Approximate Nearest Neighbour (ScaNN, FAISS, HNSW) | [§14.6](#146-search-elasticsearch-the-boring-and-correct-part) |
| **OB van** | Outside Broadcast van — production truck for live sports | [§15.1](#151-the-live-pipeline-top-to-bottom) |
| **SDK** | Software Development Kit (player + telemetry on device) | [§19.2](#192-beacon-protocol) |
| **CTV** | Connected TV — Smart TVs and STBs that support apps | [§3](#3-requirements) |
| **STB** | Set-Top Box (Roku, Apple TV, Fire TV) | [§3](#3-requirements) |

---

> **Editor's note.** This document is a **living interview defense** for an OTT-platform high-level design. The companion diagram is [`assets/05-ott-platform-hld.drawio`](./assets/05-ott-platform-hld.drawio) — open it in Cursor / VS Code (with the *Draw.io Integration* extension) or [diagrams.net](https://app.diagrams.net/) and walk through the **8 numbered colour-coded flows** (Browse · VOD-Play · Live-Play · Heartbeat · Concurrent-Stream · Ingest · Recommendation · Multi-CDN-Routing) in the bottom legend. Pair the diagram with [§8.5 — Plain-English Walkthrough](#85-plain-english-walkthrough--every-box-on-the-diagram), [§11 — End-to-End Flows](#11-end-to-end-flows), and [§21 — Q&A Defense](#21-qa-defense--top-25-tough-interview-questions) for an 80-minute whiteboard story end-to-end.
