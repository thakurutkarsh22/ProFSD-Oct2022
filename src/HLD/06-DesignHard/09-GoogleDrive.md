# Design Google Drive / Dropbox (File Sync)

> **Difficulty:** Hard | **Frequency:** ★★★★★ | **Companies:** Google, Dropbox, Microsoft
> **Source:** Alex Xu Vol 1 Chapter 15

---

## 1. Requirements

### Functional
- Upload and download files
- Automatic file sync across devices
- File sharing with permissions
- File versioning (revision history)
- Offline editing with auto-sync when online

### Non-Functional
- Reliability (never lose user's files)
- Fast sync (changes propagated within seconds)
- Bandwidth efficient (don't re-upload entire file for small changes)
- Handle large files (up to 10GB)

### Scale
- 500M users, 100M DAU
- Average user: 200 files, 500 MB storage
- Total storage: 100 PB

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                   FILE SYNC SYSTEM                                    │
│                                                                      │
│  ┌──────────────┐                                                    │
│  │ Desktop/     │                                                    │
│  │ Mobile Client│                                                    │
│  │              │                                                    │
│  │ ┌──────────┐│     ┌──────────────┐      ┌──────────────┐        │
│  │ │ Local    ││────►│  Block      │─────►│  Cloud       │        │
│  │ │ Watcher  ││     │  Server     │      │  Storage     │        │
│  │ │ (detects ││     │             │      │  (S3)        │        │
│  │ │  changes)││     │ Chunk,      │      │              │        │
│  │ └──────────┘│     │ compress,   │      │ Blocks:      │        │
│  │ ┌──────────┐│     │ encrypt,    │      │ block_abc123 │        │
│  │ │ Chunking ││     │ dedup       │      │ block_def456 │        │
│  │ │ Engine   ││     └──────┬──────┘      └──────────────┘        │
│  │ └──────────┘│            │                                      │
│  │ ┌──────────┐│     ┌──────▼──────┐      ┌──────────────┐        │
│  │ │ Local DB ││     │  Metadata  │      │ Notification │        │
│  │ │ (SQLite) ││     │  Service   │      │ Service      │        │
│  │ └──────────┘│     │            │      │              │        │
│  └──────────────┘     │ Files, vers│      │ Long polling │        │
│                       │ blocks,    │      │ / WebSocket  │        │
│                       │ sharing    │      │ for sync     │        │
│                       └────────────┘      └──────────────┘        │
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐                               │
│  │  Metadata DB │    │  Sync Queue  │                               │
│  │  (PostgreSQL)│    │  (per user)  │                               │
│  └──────────────┘    └──────────────┘                               │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Innovation: Block-Level Sync

```
PROBLEM: User edits a 1GB file, changing just 1 line.
Re-uploading entire 1GB is wasteful!

SOLUTION: Split file into blocks, only sync changed blocks.

  File: report.docx (1 GB)
  
  Split into 4MB blocks:
  ┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
  │Block 1 ││Block 2 ││Block 3 ││Block 4 ││Block N │
  │ 4MB    ││ 4MB    ││ 4MB    ││ 4MB    ││ ...    │
  │hash:abc││hash:def││hash:ghi││hash:jkl││        │
  └────────┘└────────┘└────────┘└────────┘└────────┘

  User edits → Block 3 changes → new hash: xyz
  
  Only Block 3 is uploaded! (4MB instead of 1GB)
  
  File Metadata:
  ┌──────────────────────────────────────────────┐
  │ file: report.docx                            │
  │ version: 5                                   │
  │ blocks: [abc, def, xyz, jkl, ...]            │
  │         (block 3 hash changed abc→xyz)       │
  └──────────────────────────────────────────────┘

  DEDUPLICATION:
  If another user uploads a file with same block hash → don't store again!
  Just reference the existing block.
  Dropbox saves ~60% storage through deduplication.
```

---

## 4. Sync Protocol

```
UPLOAD SYNC:
  1. Client detects file change (local watcher)
  2. Chunk file into blocks, compute hashes
  3. Compare hashes with server → find changed blocks
  4. Upload only changed blocks (compressed + encrypted)
  5. Update file metadata with new block list + version
  6. Notify other devices via notification service

DOWNLOAD SYNC:
  1. Client receives notification: "file X has new version"
  2. Fetch new metadata (block list for latest version)
  3. Compare with local blocks → find missing blocks
  4. Download only missing blocks
  5. Reassemble file locally

CONFLICT RESOLUTION:
  User A edits file offline, User B edits same file offline.
  Both come online → CONFLICT!
  
  Solutions:
  1. Create conflict copy: "report (User B's conflict).docx"
  2. Last-write-wins (simpler but may lose data)
  3. Three-way merge (for text files, like git)
```

---

## 5. Version History

```
  Each save creates a new version (only changed blocks stored):
  
  Version 1: blocks [A, B, C, D]        Total new storage: 4 blocks
  Version 2: blocks [A, B, C', D]       Total new storage: 1 block (C')
  Version 3: blocks [A, B', C', D]      Total new storage: 1 block (B')
  Version 4: blocks [A, B', C'', D]     Total new storage: 1 block (C'')
  
  Rollback to Version 2: reconstruct from blocks [A, B, C', D]
  All blocks still exist in cloud storage.
  
  Old versions can be garbage-collected after retention period.
```

---

## 6. Key Points for Interview

1. **Block-level chunking** — only sync changed blocks (not entire files)
2. **Deduplication** by block hash — massive storage savings
3. **Delta sync** — compare block hashes to find what changed
4. **Compression + encryption** before uploading blocks
5. **Metadata DB** tracks file versions and block references
6. **Long polling / WebSocket** for real-time sync notifications
7. **Conflict resolution** — conflict copies for simultaneous offline edits
8. **S3 for block storage** — durable, scalable object storage
