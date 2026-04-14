# Design Instagram / Photo Sharing

> **Difficulty:** Medium | **Frequency:** ★★★★☆ | **Companies:** Meta, Google, Pinterest

---

## 1. Requirements

### Functional
- Upload photos with captions
- Follow users, view feed
- Like and comment on photos
- Explore/discover page
- Stories (24-hour ephemeral content)

### Scale
- 1B users, 500M DAU
- 100M photos uploaded/day
- Average photo: 200KB (after compression)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     INSTAGRAM SYSTEM                              │
│                                                                  │
│  ┌──────┐   ┌────┐   ┌──────────────────────────────────────┐  │
│  │Client│──►│ LB │──►│              API Servers              │  │
│  └──────┘   └────┘   └──────────┬─────────────┬─────────────┘  │
│                                 │             │                  │
│          ┌──────────────────────┼─────────────┼───────┐         │
│          ▼                      ▼             ▼       ▼         │
│  ┌──────────────┐    ┌──────────────┐  ┌────────┐ ┌──────┐    │
│  │ Photo Upload │    │ Feed Service │  │ User   │ │Search│    │
│  │ Service      │    │              │  │Service │ │ Svc  │    │
│  └──────┬───────┘    └──────┬───────┘  └────────┘ └──────┘    │
│         │                   │                                   │
│    ┌────┴────┐         ┌────┴─────┐                             │
│    ▼         ▼         ▼          ▼                             │
│ ┌──────┐ ┌──────┐  ┌──────┐  ┌──────┐                         │
│ │  S3  │ │ DB   │  │Redis │  │Social│                         │
│ │(imgs)│ │(meta)│  │(feed │  │Graph │                         │
│ │      │ │      │  │cache)│  │(DB)  │                         │
│ └──────┘ └──────┘  └──────┘  └──────┘                         │
│                                                                  │
│  CDN ← serves images globally (80%+ of traffic)                 │
└──────────────────────────────────────────────────────────────────┘

PHOTO UPLOAD:
  1. Client → resize/compress locally
  2. Upload to S3 via pre-signed URL
  3. Generate thumbnails (multiple sizes)
  4. Store metadata in DB (photo_id, user, caption, S3_path, timestamp)
  5. Trigger feed fan-out (async via Kafka)

FEED GENERATION:
  Hybrid fan-out (same as News Feed design):
  - Regular users: push to followers' feed caches
  - Celebrities: pull at read time
```

---

## 3. Data Model

```
Users Table (PostgreSQL):
  user_id | username | bio | profile_pic_url | followers_count

Photos Table (PostgreSQL, sharded by user_id):
  photo_id | user_id | s3_url | caption | created_at | location

Follows Table (Social Graph — Cassandra or Graph DB):
  follower_id | followed_id | created_at

Likes Table (Cassandra):
  photo_id | user_id | created_at

Comments Table (Cassandra):
  photo_id | comment_id | user_id | text | created_at

Feed Cache (Redis Sorted Set):
  user:{id}:feed → [(photo_id, score=timestamp), ...]
```

---

## 4. Key Design Decisions

```
IMAGE STORAGE:
  - Original uploaded to S3
  - Thumbnails generated: 150x150, 350x350, 640x640, 1080x1080
  - Serve via CDN with URL: cdn.instagram.com/photos/{photo_id}/640.jpg
  - WebP format for smaller size (30% smaller than JPEG)

SHARDING:
  Shard photos table by user_id (all user's photos on same shard)
  → "Get user's photos" query stays on one shard

LIKE COUNTING:
  Redis: INCR photo:{id}:likes (real-time count)
  "Has user X liked this?" → Redis SET: photo:{id}:likers
```

---

## 5. Key Points for Interview

1. **S3 + CDN** for image storage and delivery
2. **Pre-signed URLs** for direct upload to S3
3. **Thumbnail generation** for multiple device sizes
4. **Hybrid fan-out** for feed (same approach as News Feed)
5. **Shard by user_id** for data locality
6. **Redis** for feed cache, like counts, and activity feeds
