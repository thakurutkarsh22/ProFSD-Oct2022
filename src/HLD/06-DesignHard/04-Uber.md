# Design Uber / Ride Sharing

> **Difficulty:** Hard | **Frequency:** ★★★★★ | **Companies:** Uber, Lyft, Grab, Google

---

## 1. Requirements

### Functional
- Riders request rides (pickup → destination)
- Match riders with nearby drivers
- Real-time driver location tracking
- ETA calculation
- Dynamic pricing (surge)
- Payment processing
- Trip history

### Non-Functional
- Low latency matching (< 5 seconds)
- Real-time location updates (every 3-5 seconds from drivers)
- High availability

### Scale
- 100M riders, 5M drivers
- 20M rides/day
- 5M active drivers sending location every 4 seconds = 1.25M updates/second

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         UBER SYSTEM                                   │
│                                                                      │
│  ┌──────────┐              ┌──────────────┐           ┌──────────┐  │
│  │  Rider   │              │  API Gateway │           │  Driver  │  │
│  │  App     │─────────────►│              │◄──────────│  App     │  │
│  └──────────┘              └──────┬───────┘           └──────────┘  │
│                                   │                                  │
│       ┌───────────────────────────┼───────────────────────┐         │
│       ▼               ▼          ▼           ▼            ▼         │
│  ┌─────────┐   ┌──────────┐ ┌────────┐ ┌─────────┐ ┌─────────┐   │
│  │  Trip   │   │ Matching │ │Location│ │  ETA    │ │ Pricing │   │
│  │ Service │   │ Service  │ │Service │ │ Service │ │ Service │   │
│  │         │   │          │ │        │ │         │ │ (Surge) │   │
│  │ Manage  │   │ Find     │ │Track   │ │ Route + │ │         │   │
│  │ trip    │   │ nearest  │ │drivers │ │ traffic │ │ Supply/ │   │
│  │ state   │   │ driver   │ │in real │ │ = ETA   │ │ demand  │   │
│  └─────────┘   │ for rider│ │time    │ └─────────┘ └─────────┘   │
│                 └──────────┘ └────────┘                            │
│                                  │                                  │
│                         ┌────────┴────────┐                         │
│                         ▼                 ▼                         │
│                   ┌──────────┐     ┌──────────────┐                │
│                   │ Spatial  │     │  Driver      │                │
│                   │ Index    │     │  Location DB │                │
│                   │(Quadtree/│     │  (Redis/     │                │
│                   │ Geohash) │     │   in-memory) │                │
│                   └──────────┘     └──────────────┘                │
│                                                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ Trip DB      │  │ Payment Svc  │  │ Notification │            │
│  │ (PostgreSQL) │  │              │  │ Service      │            │
│  └──────────────┘  └──────────────┘  └──────────────┘            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Location Tracking (1.25M updates/second)

```
DRIVER LOCATION UPDATES:

  Driver App ──(every 4s)──► Location Service ──► Spatial Index

  Driver sends: { driver_id, lat, lng, timestamp, heading, speed }

  Storage: In-memory spatial index (NOT database for real-time)
  
  QUADTREE APPROACH:
  ┌───────────────────────┐
  │           │            │
  │    NW     │    NE      │
  │  (sparse) │  (dense→   │
  │           │  subdivide)│
  ├───────────┤────┬───────┤
  │           │    │       │
  │    SW     │ SE1│ SE2   │
  │           │    │  ●●●  │ ← lots of drivers here
  │           │────┼───────│   so this quadrant is
  │           │ SE3│ SE4   │   subdivided further
  └───────────┴────┴───────┘

  Update: Remove driver from old cell, insert into new cell
  Query "Find nearby": Get all drivers in same + adjacent cells

  GEOHASH ALTERNATIVE:
  Driver at (37.7749, -122.4194) → Geohash: "9q8yy"
  Store in Redis: GEOADD drivers -122.4194 37.7749 "driver_123"
  Query: GEORADIUS drivers -122.4194 37.7749 5 km
  → Returns all drivers within 5km radius
```

---

## 4. Ride Matching Algorithm

```
MATCHING FLOW:

  1. Rider requests ride from location (lat, lng)
  2. Matching Service:
     a. Query spatial index: find drivers within 5km radius
     b. Filter: available drivers only (not on trip)
     c. Calculate ETA for each nearby driver
     d. Rank by: ETA, driver rating, acceptance rate
     e. Send ride request to top driver
  3. Driver has 15 seconds to accept
     - Accept → match confirmed → trip begins
     - Decline/timeout → try next driver
     - 3 failures → expand radius and retry

  OPTIMIZATION (Uber's approach):
  Don't just find nearest driver — optimize for the SYSTEM.
  
  Consider: If Driver A is 2 min from Rider 1 and 10 min from Rider 2,
  but Driver B is 3 min from Rider 1 and 3 min from Rider 2,
  
  Assign: A→Rider1, B→Rider2  (total wait: 5 min)
  vs.     A→Rider2, B→Rider1  (total wait: 13 min)
  
  This is a bipartite matching problem (Hungarian algorithm).
```

---

## 5. Surge Pricing

```
  Supply = available drivers in a region
  Demand = ride requests in a region
  
  Region divided into hexagonal cells (H3 by Uber):
  
  ┌──────┐
  │      │  If demand/supply > threshold:
  │ Cell │  Surge multiplier = f(demand/supply)
  │ 1.5x │  
  └──────┘  Price = base_price × surge_multiplier
  
  Updated every few minutes based on real-time data.
  Incentivizes drivers to go to high-demand areas.
```

---

## 6. Trip State Machine

```
  ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐
  │ REQUESTED │───►│ MATCHED   │───►│ DRIVER    │───►│ IN_TRIP   │
  │           │    │           │    │ ARRIVING  │    │           │
  └───────────┘    └───────────┘    └───────────┘    └─────┬─────┘
       │                │                                   │
       ▼                ▼                                   ▼
  ┌───────────┐    ┌───────────┐                     ┌───────────┐
  │ NO_DRIVER │    │ CANCELLED │                     │ COMPLETED │
  │ FOUND     │    │           │                     │ → Payment │
  └───────────┘    └───────────┘                     └───────────┘
```

---

## 7. Key Points for Interview

1. **Geospatial index (Quadtree/Geohash)** for driver location tracking
2. **In-memory spatial index** — NOT database for real-time location (too slow)
3. **Matching algorithm** — nearest driver with ETA optimization
4. **WebSocket** for real-time location updates (driver→server→rider)
5. **Surge pricing** based on supply/demand ratio in geographic cells
6. **H3 hexagonal grid** (Uber's open-source) for geographic partitioning
7. **Trip state machine** for managing ride lifecycle
8. **1.25M location updates/second** — need highly efficient ingestion pipeline
