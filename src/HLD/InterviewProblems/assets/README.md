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
- [`03-job-scheduler-whiteboard.drawio`](./03-job-scheduler-whiteboard.drawio) — Simpler whiteboard-style recreation (Postgres + watcher + Kafka + Redis cancel cache). Pairs with the same write-up for the easier interview narrative.
