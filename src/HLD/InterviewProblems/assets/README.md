# Diagrams — authoring guide

This folder holds **editable architecture diagrams** for the system-design write-ups in `src/HLD/InterviewProblems/`.

## TL;DR

- Author with **draw.io / [diagrams.net](https://app.diagrams.net/)**.
- Save as **`.drawio.svg`** (the SVG that *embeds* the mxGraph source).
- That single file is **renderable on GitHub** *and* **editable in draw.io** — no separate "source vs. export" step.

## File-naming convention

| Extension          | Purpose                                                                    | Embed in markdown? |
|--------------------|----------------------------------------------------------------------------|--------------------|
| `*.drawio`         | Pure draw.io source (XML). Use when you don't need GitHub to render it.    | No                 |
| `*.drawio.svg`     | **Preferred.** Rendered SVG with the editable XML embedded inside.         | **Yes**            |
| `*.drawio.png`     | Rendered PNG with embedded XML. Use only if you need a raster image.       | Yes                |

Naming pattern: `<doc-number>-<short-title>.drawio.svg`, e.g. `03-job-scheduler-hld.drawio.svg`.

## Authoring workflow

### One-time setup

1. Open this repo in **Cursor** or **VS Code**.
2. Accept the workspace's recommended extensions (`.vscode/extensions.json`):
   - `hediet.vscode-drawio` — Draw.io Integration
   - `bierner.markdown-mermaid` — Mermaid preview in markdown
3. The workspace `settings.json` already maps `*.drawio*` files to the draw.io editor — double-clicking just works.

### Creating a new diagram

1. Create a file `src/HLD/InterviewProblems/assets/<n>-<title>.drawio.svg`.
2. Open it — Cursor/VS Code launches the draw.io editor inline.
3. Draw, then `Cmd/Ctrl+S` — the SVG is saved with the source embedded.
4. Reference it from the markdown:

   ```markdown
   ![Job Scheduler HLD](./assets/03-job-scheduler-hld.drawio.svg)
   ```

5. Commit. GitHub will render the SVG; reviewers can re-open and edit it locally.

### Editing an existing diagram

- In Cursor/VS Code: just open the `.drawio` or `.drawio.svg` file.
- In a browser: [https://app.diagrams.net/](https://app.diagrams.net/) → **File → Open from Device**.

## Style conventions (so diagrams in this repo feel consistent)

- **Layout:** left-to-right data flow; control plane on the left.
- **Stacked rectangles** — services with N replicas (Submission API, Dispatcher, Workers).
- **Cylinders** — durable stores, Kafka topics, indexes.
- **Sticky notes** (`shape=note`) — invariants, latency budgets, trade-offs, RF/quorum settings.
- **Solid arrows** — hot-path data flow.
- **Dashed arrows** — control / async / best-effort / heartbeats.
- **Color palette** (defaults from draw.io):
  - Stateless services / dispatchers — blue (`#dae8fc / #6c8ebf`)
  - Durable stores / DBs — green (`#d5e8d4 / #82b366`)
  - Caches / hot tiers — red-pink (`#f8cecc / #b85450`)
  - Streaming / queues — purple (`#e1d5e7 / #9673a6`)
  - Coordination / control plane — yellow (`#fff4c3 / #d6b656`)
  - Sweepers / safety nets — orange (`#ffe6cc / #d79b00`)
  - Sticky notes — light yellow (`#fff2cc / #d6b656`)

## Mermaid vs draw.io — when to use which

| Use Mermaid (in markdown) when…                          | Use draw.io (`.drawio.svg`) when…                                  |
|----------------------------------------------------------|--------------------------------------------------------------------|
| Diagram is small (< ~15 nodes) and mostly linear.        | Diagram needs custom shapes, sticky notes, callouts, multi-zones.  |
| You want code-review-friendly text diffs.                | Layout matters more than text diff.                                |
| Sequence / flowchart / ER suffices.                      | Architectural diagram with annotations / heterogeneous shapes.     |

The convention in this repo is: **Mermaid for sequence/flow** in `§ HLD` sections, **draw.io for the headline architecture diagram** (referenced from `§ HLD` near the Mermaid version, so a reader can pick either rendering).

## Existing diagrams

- [`03-job-scheduler-hld.drawio`](./03-job-scheduler-hld.drawio) — High-Level Architecture for [`../03-HighPrecisionDistributedJobScheduler.md`](../03-HighPrecisionDistributedJobScheduler.md). Production-grade design with Cassandra durable store, Redis ZSET hot tier, etcd shard leases, and Kafka dispatch transport.
- [`03-job-scheduler-utkarsh-design-v2.drawio`](./03-job-scheduler-utkarsh-design-v2.drawio) / [`v3`](./03-job-scheduler-utkarsh-design-v3.drawio) / [`v4-with-dag`](./03-job-scheduler-utkarsh-design-v4-with-dag.drawio) — iteratively-hardened versions of the scheduler with numbered, colour-coded flows.
- [`04-stock-broking-platform-hld.drawio.svg`](./04-stock-broking-platform-hld.drawio.svg) — High-Level Architecture for [`../04-StockBrokingPlatform.md`](../04-StockBrokingPlatform.md). Zerodha / Robinhood-style retail broker: API GW + WebSocket gateway, OMS + RMS + Funds (double-entry) + Portfolio, Kafka backbone, Exchange Gateway with FIX + drop-copy, Market Data multicast → fan-out, GTT / Squareoff / Settlement engines. Eight numbered, colour-coded flows on the right-hand legend. Rendered SVG with editable XML embedded — open in draw.io / VS Code extension to edit.
- [`05-ott-platform-hld.drawio`](./05-ott-platform-hld.drawio) — High-Level Architecture for [`../05-DesignOTTPlatform.md`](../05-DesignOTTPlatform.md). Netflix / Amazon Prime / Disney+ Hotstar-style OTT: Mobile / Web / TV / Console clients → Multi-CDN edge (Akamai · CloudFront · Cloudflare · Limelight · Open-Connect-style ISP appliances) + Origin Shield, API Gateway + WS-push + Live-Ingest + Auth, core services (Catalog · Search · **Playback ★ Gatekeeper** · Recommendation · Subscription/Billing · DRM License Server · Notification · Live Transcoder · Concurrent-Stream Counter · Heartbeat), Kafka backbone (viewing.events · playback.heartbeat · cdn.qoe · subscription.events · catalog.events · ingest.events · recommendation.feedback · audit.events · live.segments), Pipeline workers (Workflow Orchestrator · Encoder Pool · Packager + DRM · QC · CDN Pre-warmer · Multi-CDN Router · Recommendation Pipeline · Search Indexer), Stores (S3 mezzanine WORM · S3 packaged · Postgres titles/users/subs · Cassandra watch_progress · Elasticsearch · Redis hot · HSM · ClickHouse · etcd · audit · cold archive), and external (payment partners · ad servers · studios · DRM CAs · legal). Eight numbered, colour-coded flows: B (Browse), P (VOD Play), L (Live Play), H (Heartbeat), C (Concurrent-stream), I (Studio Ingest), R (Recommendation), D (Multi-CDN routing & DRM audit). Pure draw.io XML — open in draw.io / VS Code extension to edit; export to .drawio.svg if you want GitHub-rendered.
- [`06-design-youtube-hld.drawio`](./06-design-youtube-hld.drawio) — High-Level Architecture for `../06-DesignYouTube.md` (companion). YouTube-scale UGC platform: Mobile / Web / TV / Creator-Studio / Live Encoder clients → Global CDN (GFE / Cloud-CDN-equivalent + ISP YouTube-Cache nodes) + Origin Shield, API GW + WS Push + RTMP/SRT Ingest + Auth, core services (Upload · Watch/Manifest · Search · Recommendation · Engagement · Notification · Ad Decision · Live Transcoder · View Beacon · DRM), Kafka backbone (video.uploaded · video.transcoded · video.indexed · watch.events · engagement.events · notify.events · audit.events · live.segments · features.stream · moderation.events), Pipeline workers (Transcoding (GPU+CPU) · Thumbnail · ASR/Captions · Content-ID · Safety/NSFW · View Aggregator (Flink) · Trending · Recommendation Pipeline · Search Indexer · Notification Worker · Outbox), Stores (Origin RAW · Origin TRANSCODED · Spanner metadata · Bigtable comments/counters · Search Index · Feature Store · Model Store · Redis hot · Cold/Archive · Audit), Analytics (BigQuery DWH · Druid/Pinot real-time OLAP) + external (Rights-Holders DB · Ad Exchanges · Payment Partners · Legal/DMCA). Eight numbered, colour-coded flows: U (Upload), T (Transcoding), W (Watch), R (Recommendation), E (Engagement), V (View-count), L (Live), S (Search). Pure draw.io XML.
- [`06-design-youtube-hld.drawio`](./06-design-youtube-hld.drawio) — High-Level Architecture for [`../06-DesignYouTube.md`](../06-DesignYouTube.md). Planet-scale UGC video platform: 5 client surfaces (mobile, web, TV, Creator Studio, Live Encoder), multi-CDN + ISP-cache (Open-Connect-style YouTube-Cache appliances), Origin Shield, Upload / Watch / Search / Recommendation / Engagement / View-Beacon / Live-Transcoder / DRM services, Kafka backbone (`video.uploaded`, `video.transcoded`, `watch.events`, `engagement.events`, `notify.events`, `live.segments`, `features.stream`, `moderation.events`, `audit.events`, `*.dlq`), processing workers (Transcoding pool with GOP-distributed encoding, Thumbnail / ASR / ContentID / Safety, View Aggregator, Trending Engine, Recommendation Pipeline, Search Indexer, Notification Worker, Outbox Publisher), storage tier (GCS Origin RAW + Transcoded, Spanner metadata, Bigtable comments + counters, Elasticsearch + Vespa search, Feature Store + Model Store, Redis hot, Cold Archive, Audit Log), analytics (BigQuery + Druid + Prometheus + Stuck-Job Sweeper + Moderation Console), external actors (Rights-Holders DB, Ad Exchanges, Payment Partners, DMCA). **Eight numbered, colour-coded flows** on the right-hand legend: U (Upload, blue), T (Transcoding, green), W (Watch, red), R (Recommendation, purple), E (Engagement, teal), V (View Count, orange), L (Live, brown), S (Search, slate). Four sticky-note invariants (TTFF budget, transcoding throughput, CDN egress, consistency contract) + capacity back-of-envelope.
