# Design Google Docs (Collaborative Editing)

> **Difficulty:** Hard | **Frequency:** ★★★★★ | **Companies:** Google, Microsoft, Notion

---

## 1. Requirements

### Functional
- Create/edit documents (rich text)
- Real-time collaborative editing (multiple users simultaneously)
- See other users' cursors in real-time
- Version history and undo
- Sharing with permissions (view, comment, edit)
- Offline editing with sync

### Non-Functional
- Real-time sync (< 100ms between collaborators)
- No data loss (all edits preserved)
- Conflict resolution without user intervention
- Handle 100+ concurrent editors on single document

---

## 2. The Core Challenge: Conflict Resolution

```
THE PROBLEM:

  User A types "Hello" at position 0
  User B types "World" at position 0 (simultaneously)
  
  What should the result be?
  - "HelloWorld"?
  - "WorldHello"?
  - "HWeolrllod"?  (interleaved — terrible!)
  
  Both edits are valid. Need deterministic resolution.

TWO APPROACHES:
  1. OT (Operational Transformation) — Used by Google Docs
  2. CRDT (Conflict-free Replicated Data Types) — Used by Figma, Notion
```

---

## 3. OT (Operational Transformation)

```
CONCEPT: Transform operations against concurrent operations.

  User A: Insert("H", pos=0)    User B: Insert("W", pos=0)
  
  These happen concurrently. Server receives both.
  
  Server applies A's op first: "H"
  Now B's op needs transformation:
    B wanted Insert("W", pos=0) but A already inserted at 0
    Transform: Insert("W", pos=1)  (shift position by 1)
  
  Result: "HW" (deterministic!)
  
  Server sends transformed ops to both clients.

OT SERVER FLOW:
  ┌────────┐   op: Insert("H",0)    ┌──────────┐
  │ User A │ ───────────────────────►│          │
  └────────┘                         │  OT      │
                                     │  Server  │
  ┌────────┐   op: Insert("W",0)    │          │
  │ User B │ ───────────────────────►│Transform │
  └────────┘                         │ & Apply  │
                                     └────┬─────┘
                                          │
                                     Send transformed ops
                                     back to both clients
  
  Pros: Well-understood, used in production (Google Docs since 2010)
  Cons: Requires central server for transformation
        Complex to implement for rich text operations
```

---

## 4. CRDT (Conflict-free Replicated Data Types)

```
CONCEPT: Data structure designed so concurrent operations 
automatically converge without coordination.

  Each character has a unique position ID (not array index).
  
  User A types "H": assigns position ID = (1.0, A)
  User B types "W": assigns position ID = (1.0, B)
  
  Sort by position: (1.0, A) < (1.0, B) → "HW"
  
  Both users independently arrive at same result!
  No central server needed for conflict resolution.

  Position IDs:
  "Hello"
  H: 0.25    e: 0.50    l: 0.75    l: 0.875    o: 0.9375
  
  Insert "X" between "e" and "l":
  X gets position: (0.50 + 0.75) / 2 = 0.625
  
  Any other user inserting at same spot gets unique position
  due to tie-breaking by user ID.

  Pros: No central server needed (P2P possible)
        Eventually consistent by design
  Cons: Tombstones for deletions (memory overhead)
        Position IDs can grow unbounded
```

---

## 5. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    GOOGLE DOCS SYSTEM                                 │
│                                                                      │
│  ┌──────┐  WebSocket   ┌──────────────┐                             │
│  │User A│◄────────────►│              │                             │
│  └──────┘               │  Collab     │    ┌──────────────┐         │
│                         │  Server     │───►│  Document    │         │
│  ┌──────┐  WebSocket   │              │    │  Storage     │         │
│  │User B│◄────────────►│  - OT Engine │    │  (DB/S3)     │         │
│  └──────┘               │  - Session  │    └──────────────┘         │
│                         │    Manager  │                              │
│  ┌──────┐  WebSocket   │  - Cursor   │    ┌──────────────┐         │
│  │User C│◄────────────►│    Sync     │───►│  Version     │         │
│  └──────┘               └──────────────┘    │  History     │         │
│                                              │  (Event Log) │         │
│                                              └──────────────┘         │
│                                                                      │
│  EDITING FLOW:                                                       │
│  1. User A types character → sends operation over WebSocket          │
│  2. Collab Server receives operation                                 │
│  3. Transform against any concurrent ops (OT engine)                 │
│  4. Apply to server document state                                   │
│  5. Broadcast transformed op to User B, C via WebSocket              │
│  6. Periodically persist document to storage                         │
│  7. Log operation to version history                                 │
│                                                                      │
│  CURSOR SYNC:                                                        │
│  Each user's cursor position broadcast to others every 50ms          │
│  Shown as colored cursor with user's name                            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6. Version History

```
  Every operation is logged as an event:
  
  [Rev 1] Insert("H", 0) by User A at T1
  [Rev 2] Insert("e", 1) by User A at T2
  [Rev 3] Insert("W", 0) by User B at T2  (transformed to pos 2)
  [Rev 4] Delete(pos=2) by User B at T3
  ...
  
  Version history = replay of all operations from beginning.
  Can restore any previous version by replaying up to that revision.
  
  Snapshots taken periodically (every N revisions) to avoid
  replaying from the start every time.
```

---

## 7. Key Points for Interview

1. **OT or CRDT** for conflict resolution — know the difference
2. **WebSocket** for real-time bidirectional communication
3. **Central server** for OT (transforms operations), decentralized for CRDT
4. **Version history** as event log (event sourcing pattern)
5. **Periodic snapshots** to avoid replaying entire history
6. **Document permissions** stored in metadata DB
7. **Auto-save** with debouncing (don't persist every keystroke)
8. Mention Google uses OT (2010), Figma/Notion use CRDT (newer approach)
