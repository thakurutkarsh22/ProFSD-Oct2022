# Design Zoom (Video Conferencing)

> **Difficulty:** Hard | **Frequency:** ★★★★☆ | **Companies:** Zoom, Google, Microsoft

---

## 1. Requirements

### Functional
- 1:1 and group video calls (up to 1000 participants)
- Screen sharing
- Chat during call
- Recording
- Mute/unmute audio/video

### Non-Functional
- Low latency (< 200ms end-to-end for real-time feel)
- Adaptive quality based on bandwidth
- Handle packet loss gracefully (audio > video priority)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    VIDEO CONFERENCING SYSTEM                          │
│                                                                      │
│  1:1 CALL (Peer-to-Peer):                                          │
│  ┌──────┐     Direct Connection      ┌──────┐                      │
│  │User A│◄───────────────────────────►│User B│                      │
│  └──────┘     (WebRTC P2P)            └──────┘                      │
│                                                                      │
│  GROUP CALL (Server-Mediated):                                      │
│  ┌──────┐                                     ┌──────┐              │
│  │User A│───stream──►┌─────────────┐◄──stream─│User B│              │
│  └──────┘            │   SFU       │          └──────┘              │
│  ┌──────┐            │ (Selective  │          ┌──────┐              │
│  │User C│───stream──►│  Forwarding│◄──stream─│User D│              │
│  └──────┘            │  Unit)     │          └──────┘              │
│                      └──────┬──────┘                                │
│                             │                                        │
│                      Forwards selected                               │
│                      streams to each                                 │
│                      participant                                     │
│                                                                      │
│  SIGNALING (Setting up the call):                                   │
│  ┌──────┐    ┌──────────────┐    ┌──────┐                          │
│  │User A│───►│  Signaling   │◄───│User B│                          │
│  └──────┘    │  Server      │    └──────┘                          │
│              │              │                                        │
│              │ Exchange:    │                                        │
│              │ - SDP offers │                                        │
│              │ - ICE cands  │                                        │
│              │ - TURN/STUN  │                                        │
│              └──────────────┘                                        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Media Server Architectures

```
1. MESH (P2P): Each user sends to every other user.
   Users: A, B, C, D
   A sends 3 streams, receives 3 → total: 12 connections
   Works only for 2-4 people. Doesn't scale.

2. MCU (Multipoint Control Unit): Server mixes all streams into one.
   All users → MCU → single combined stream → all users
   Server CPU-intensive (transcoding/mixing)
   Low bandwidth for clients but expensive server.

3. SFU (Selective Forwarding Unit): BEST for most cases.
   Each user sends ONE stream to SFU.
   SFU forwards streams selectively to other users.
   
   User A sends 1 stream ──► SFU ──► sends A's stream to B, C, D
   User B sends 1 stream ──► SFU ──► sends B's stream to A, C, D
   
   SFU selects quality per recipient based on their bandwidth.
   No transcoding needed (just forwarding).
   Used by: Zoom, Google Meet, Discord

┌───────────────┬─────────┬──────────┬──────────────┐
│ Architecture  │ Mesh    │ MCU      │ SFU          │
├───────────────┼─────────┼──────────┼──────────────┤
│ Max Users     │ 4-6     │ 100+     │ 100-1000     │
│ Server CPU    │ None    │ Very High│ Low          │
│ Client BW     │ High    │ Low      │ Medium       │
│ Latency       │ Lowest  │ High     │ Low          │
│ Quality       │ Fixed   │ Uniform  │ Adaptive     │
│ Used By       │ WebRTC  │ Legacy   │ Zoom, Meet   │
└───────────────┴─────────┴──────────┴──────────────┘
```

---

## 4. Protocols

```
WebRTC STACK:
  ┌────────────────────────────────┐
  │     Application (Video/Audio)  │
  ├────────────────────────────────┤
  │     SRTP (Secure Real-time     │  ← Encrypted media
  │     Transport Protocol)        │
  ├────────────────────────────────┤
  │     DTLS (Datagram TLS)        │  ← Key exchange
  ├────────────────────────────────┤
  │     ICE / STUN / TURN          │  ← NAT traversal
  ├────────────────────────────────┤
  │     UDP                        │  ← Fast, lossy OK
  └────────────────────────────────┘

  Why UDP? Video/audio tolerates some packet loss.
  A dropped frame is better than waiting (TCP retransmit = delay).
  
  STUN: Discover your public IP behind NAT
  TURN: Relay server when P2P isn't possible (firewall)
  ICE: Framework to find the best connection path
```

---

## 5. Simulcast & Adaptive Quality

```
  Each sender streams multiple quality layers simultaneously:

  Sender → SFU:
  ┌─────────┐
  │ 1080p   │  High quality layer
  │ 720p    │  Medium quality layer  
  │ 360p    │  Low quality layer
  └─────────┘

  SFU decides per recipient:
  - User B (good bandwidth) → receives 1080p from all
  - User C (poor bandwidth) → receives 360p from all
  - Active speaker → everyone gets 1080p of speaker
  - Non-speakers in gallery → 360p thumbnails
  
  Bandwidth adaptation:
  Monitor packet loss and RTT → switch quality layers dynamically
```

---

## 6. Key Points for Interview

1. **SFU architecture** is the answer for group video (not mesh, not MCU)
2. **WebRTC** for real-time media (built-in to browsers)
3. **UDP for media** (packet loss OK), TCP for signaling/chat
4. **Simulcast** for adaptive quality per recipient
5. **STUN/TURN** for NAT traversal
6. **< 200ms latency** target for real-time conversation
7. **Priority**: audio > video > screen share (audio never drops)
8. **Recording**: SFU sends a copy to recording service → store in S3
