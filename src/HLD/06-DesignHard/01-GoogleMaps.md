# Design Google Maps

> **Difficulty:** Hard | **Frequency:** ★★★★☆ | **Companies:** Google, Uber, Grab

---

## 1. Requirements

### Functional
- Display map tiles (zoom in/out, pan)
- Search for places/addresses (geocoding)
- Calculate route between two points (navigation)
- Real-time traffic updates
- ETA calculation

### Non-Functional
- Low latency map rendering (< 200ms)
- Real-time traffic data
- Offline support (download map regions)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                       GOOGLE MAPS SYSTEM                              │
│                                                                      │
│  ┌──────┐   ┌────┐   ┌──────────────────────────────────────────┐  │
│  │Client│──►│ LB │──►│              API Gateway                  │  │
│  └──────┘   └────┘   └────────┬──────────┬──────────┬───────────┘  │
│                                │          │          │              │
│          ┌─────────────────────┘          │          └──────┐       │
│          ▼                                ▼                 ▼       │
│  ┌──────────────┐               ┌──────────────┐   ┌────────────┐ │
│  │ Map Tile     │               │  Routing     │   │ Location   │ │
│  │ Service      │               │  Service     │   │ Search Svc │ │
│  │              │               │              │   │(Geocoding) │ │
│  │ Pre-rendered │               │  Dijkstra/A* │   │            │ │
│  │ tiles at     │               │  on road     │   │ "Starbucks"│ │
│  │ each zoom    │               │  graph       │   │  → lat,lng │ │
│  └──────┬───────┘               └──────┬───────┘   └────────────┘ │
│         │                              │                           │
│    ┌────▼─────┐                  ┌─────▼──────┐                    │
│    │  CDN     │                  │Road Graph  │                    │
│    │ (tiles)  │                  │ DB         │                    │
│    └──────────┘                  └────────────┘                    │
│                                                                    │
│  ┌──────────────┐    ┌──────────────┐                              │
│  │ Traffic Svc  │    │ ETA Service  │                              │
│  │              │    │              │                              │
│  │ Aggregate    │    │ Route +      │                              │
│  │ GPS data     │    │ traffic =    │                              │
│  │ from users   │    │ estimated    │                              │
│  │              │    │ time         │                              │
│  └──────────────┘    └──────────────┘                              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Map Tiles

```
MAP TILING SYSTEM:

  World map divided into tiles at each zoom level:
  
  Zoom 0: 1 tile (whole world)
  ┌─────────┐
  │         │
  │  World  │
  │         │
  └─────────┘
  
  Zoom 1: 4 tiles (2×2)
  ┌────┬────┐
  │    │    │
  ├────┼────┤
  │    │    │
  └────┴────┘
  
  Zoom 2: 16 tiles (4×4)
  Zoom N: 4^N tiles
  
  Zoom 20: ~1 trillion tiles (street level)
  
  Each tile: 256×256 pixel PNG image
  URL: /tiles/{zoom}/{x}/{y}.png
  
  Client:
  1. User pans/zooms → calculate which tiles are visible
  2. Request only visible tiles from CDN
  3. CDN serves cached tiles (most tiles pre-rendered)
  4. Cache MISS → render on demand from map data
  
  Storage: Pre-render popular zoom levels → store on CDN
  Zoom 0-15: ~5 billion tiles → CDN
  Zoom 16-20: render on demand (too many tiles to pre-render)
```

---

## 4. Routing (Navigation)

```
ROAD GRAPH:

  Intersections = nodes, roads = edges with weights (time/distance)
  
     A ──5min──► B ──3min──► C
     │                       │
    2min                    4min
     │                       │
     ▼                       ▼
     D ──7min──► E ──1min──► F (destination)

  Algorithm: A* (A-star) — improved Dijkstra with heuristic
  
  But full graph is HUGE (billions of nodes globally)
  
  OPTIMIZATION: Hierarchical routing
  ┌─────────────────────────────────────────┐
  │ Level 3: Highways between cities        │  (few nodes)
  │ Level 2: Major roads within city        │  (medium nodes)
  │ Level 1: Local streets                  │  (many nodes)
  └─────────────────────────────────────────┘
  
  Long route: Local streets → Major roads → Highways → Major roads → Local
  Only expand full detail at start and end of route.
  
  Used by: Google (Contraction Hierarchies algorithm)
```

---

## 5. Real-Time Traffic

```
DATA SOURCES:
  - GPS data from millions of smartphones (anonymized)
  - Historical traffic patterns (ML models)
  - Road incidents (accidents, construction)

PIPELINE:
  GPS Updates ──► Kafka ──► Stream Processor ──► Traffic DB
  (from phones)             (aggregate speed     (road segment
                             per road segment)    → current speed)

  Road segment "Main St block 5":
  - Normal speed: 60 km/h
  - Current speed: 15 km/h (from GPS data)
  - Status: HEAVY TRAFFIC (red on map)
  
  ETA = Σ (segment_distance / segment_current_speed) for all segments in route
```

---

## 6. Key Points for Interview

1. **Map tiles + CDN** — pre-rendered tiles served from edge
2. **Geospatial indexing** (Quadtree/Geohash) for "nearby" searches
3. **Graph algorithm (A*)** with hierarchical routing for navigation
4. **Real-time traffic** from aggregated GPS data via streaming pipeline
5. **Contraction Hierarchies** to speed up long-distance routing
6. **Geocoding**: address text → lat/lng (Elasticsearch with geo-index)
