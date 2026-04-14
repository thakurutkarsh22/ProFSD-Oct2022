# Design YouTube / Video Streaming

> **Difficulty:** Medium | **Frequency:** ★★★★★ | **Companies:** Google, Netflix, Amazon
> **Source:** Alex Xu Vol 1 Chapter 14

---

## 1. Requirements

### Functional
- Upload videos
- Stream/watch videos
- Search videos
- Like, comment, subscribe
- Video recommendations

### Non-Functional
- Smooth playback (no buffering)
- High availability
- Support multiple resolutions (adaptive bitrate)

### Scale
- 2B users, 800M DAU
- Average watch time: 30 min/day
- 500 hours of video uploaded per minute

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        YOUTUBE SYSTEM                                │
│                                                                      │
│  UPLOAD FLOW:                                                        │
│  ┌──────┐    ┌─────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │Client│───►│ Upload  │───►│  Original    │───►│ Transcoding  │   │
│  │      │    │ Service │    │  Storage     │    │ Service      │   │
│  └──────┘    └─────────┘    │  (S3/Blob)   │    │              │   │
│                             └──────────────┘    │ 360p, 480p,  │   │
│                                                  │ 720p, 1080p, │   │
│                                                  │ 4K + codecs  │   │
│                                                  └──────┬───────┘   │
│                                                         │           │
│                                                         ▼           │
│                                                  ┌──────────────┐   │
│                                                  │  Transcoded  │   │
│                                                  │  Storage     │   │
│                                                  │  (S3/Blob)   │   │
│                                                  └──────┬───────┘   │
│                                                         │           │
│                                                         ▼           │
│                                                  ┌──────────────┐   │
│                                                  │     CDN      │   │
│                                                  │  (Global)    │   │
│                                                  └──────────────┘   │
│                                                                      │
│  WATCH FLOW:                                                         │
│  ┌──────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │Client│───►│  CDN Edge    │───►│  Origin      │                   │
│  │      │◄───│  (nearest)   │    │  (if miss)   │                   │
│  └──────┘    └──────────────┘    └──────────────┘                   │
│                                                                      │
│  METADATA:                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Video        │  │ User         │  │ Comment      │              │
│  │ Metadata DB  │  │ Service      │  │ Service      │              │
│  │ (title, desc,│  │              │  │              │              │
│  │  views, etc) │  │              │  │              │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Video Upload Pipeline

```
UPLOAD → PROCESS → DISTRIBUTE

  1. UPLOAD:
     Client uploads video (chunked upload for large files)
     Pre-signed URL from S3 → client uploads directly to S3
     Parallel uploads of chunks for speed
  
  2. TRANSCODING (Encoding):
     Convert original video into multiple formats:
     
     Original (4K, MOV, 5GB)
         │
         ├──► 360p  H.264  (50MB)
         ├──► 480p  H.264  (100MB)
         ├──► 720p  H.264  (300MB)
         ├──► 1080p H.264  (700MB)
         ├──► 1080p VP9    (500MB)
         └──► 4K    H.265  (2GB)
     
     Transcoding is CPU-intensive: use dedicated worker pool
     Queue: Kafka → Transcoding Workers → Store results
     
     Also: generate thumbnails, extract audio, add watermarks

  3. DISTRIBUTE:
     Push transcoded videos to CDN edge servers globally
     Popular videos → pre-pushed to more edge locations
     Unpopular → pulled on demand
```

---

## 4. Video Streaming (Adaptive Bitrate)

```
ADAPTIVE BITRATE STREAMING (ABR):

  Protocols: HLS (Apple) or DASH (standard)
  
  Video split into small segments (2-10 seconds each):
  
  ┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐
  │Seg 1 ││Seg 2 ││Seg 3 ││Seg 4 ││Seg 5 │ ...
  │ 4s   ││ 4s   ││ 4s   ││ 4s   ││ 4s   │
  └──────┘└──────┘└──────┘└──────┘└──────┘
  
  Each segment available in multiple qualities:
  Seg 1: [360p] [480p] [720p] [1080p] [4K]
  
  Client monitors bandwidth:
  - Good bandwidth → request 1080p segments
  - Bandwidth drops → switch to 480p mid-stream
  - Bandwidth recovers → switch back to 1080p
  
  No rebuffering! Quality adjusts dynamically.
  
  Manifest file (.m3u8 for HLS):
  Lists all segments and available qualities.
  Client downloads manifest first, then segments.
```

---

## 5. Key Design Decisions

```
STORAGE:
  - Original video: S3/Blob storage
  - Transcoded videos: S3 → replicated to CDN
  - Metadata: SQL database (PostgreSQL)
  - Comments: NoSQL or SQL (sharded by video_id)
  - View counts: Redis (atomic increment) → async persist to DB

CDN STRATEGY:
  - Top 20% popular videos: pre-push to all edge locations
  - Remaining 80%: pull-through caching
  - Very old/unpopular: serve from origin only

VIEW COUNTING:
  Challenge: "Gangnam Style" gets 100K views/second
  
  Real-time: Redis INCR (atomic, fast)
  Batch: Kafka → aggregate → write to DB every minute
  Accuracy: eventual (view count may lag by minutes) — acceptable
```

---

## 6. Key Points for Interview

1. **Pre-signed URLs** for direct client-to-S3 upload (bypass app server)
2. **Transcoding pipeline** for multiple resolutions + codecs
3. **CDN** is critical — most video traffic served from edge
4. **Adaptive bitrate streaming (HLS/DASH)** for smooth playback
5. **Chunked upload** for reliability (resume from last chunk on failure)
6. **Separate hot/cold storage** — popular on CDN, old on cheap storage
7. **Async processing** — upload returns immediately, transcoding is async
