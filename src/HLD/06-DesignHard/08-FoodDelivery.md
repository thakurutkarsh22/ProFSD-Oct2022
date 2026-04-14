# Design Food Delivery (DoorDash / Uber Eats)

> **Difficulty:** Hard | **Frequency:** ★★★★☆ | **Companies:** DoorDash, Uber, Amazon

---

## 1. Requirements

### Functional
- Browse restaurants and menus
- Place food orders
- Real-time order tracking
- Match orders to delivery drivers
- Payment processing
- Rating & reviews
- Estimated delivery time

### Non-Functional
- Low latency for browsing (< 200ms)
- Real-time order status updates
- Handle order surges (lunch/dinner peaks)

### Scale
- 50M users, 1M restaurants, 500K drivers
- 5M orders/day, peak: 200 orders/second

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    FOOD DELIVERY SYSTEM                               │
│                                                                      │
│  ┌──────────┐   ┌────────────┐                                      │
│  │ Customer │──►│ API Gateway│                                      │
│  │   App    │   └─────┬──────┘                                      │
│  └──────────┘         │                                              │
│                 ┌─────┼─────────────┬──────────────┐                 │
│                 ▼     ▼             ▼              ▼                 │
│          ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│          │Restaurant│ │  Order   │ │ Delivery │ │ Search   │       │
│          │ Service  │ │ Service  │ │ Service  │ │ Service  │       │
│          │          │ │          │ │          │ │          │       │
│          │ Menus,   │ │ Create,  │ │ Match    │ │ Find     │       │
│          │ hours,   │ │ track,   │ │ driver,  │ │ nearby   │       │
│          │ inventory│ │ status   │ │ track    │ │ restrnts │       │
│          └──────────┘ └────┬─────┘ └──────────┘ └──────────┘       │
│                            │                                         │
│                     ┌──────┴──────┐                                  │
│                     ▼             ▼                                  │
│              ┌──────────┐  ┌──────────┐                              │
│              │  Kafka   │  │  Order   │                              │
│              │  (events)│  │  DB      │                              │
│              └──────────┘  └──────────┘                              │
│                                                                      │
│  ORDER LIFECYCLE:                                                    │
│  ┌────────┐  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ PLACED │─►│CONFIRMED│─►│PREPARING │─►│PICKED UP │─►│DELIVER-│ │
│  │        │  │(by rest) │  │(cooking) │  │(by driver)│  │  ED    │ │
│  └────────┘  └─────────┘  └──────────┘  └──────────┘  └────────┘ │
│                                                                      │
│  ETA CALCULATION:                                                   │
│  Total ETA = prep_time + driver_to_restaurant + restaurant_to_user  │
│            = 15 min    + 8 min                + 12 min = 35 min     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Challenges

### Driver-Order Matching (Similar to Uber)
```
BATCHED MATCHING:
  Instead of matching one order at a time, batch orders every 60 seconds.
  
  Orders waiting:   [O1, O2, O3, O4]
  Available drivers: [D1, D2, D3]
  
  Optimize total delivery time across ALL orders (not just one).
  
  Consider:
  - Driver proximity to restaurant
  - Driver already heading near the restaurant
  - Multi-order batching (one driver picks up from 2 restaurants on the way)
```

### Restaurant Capacity
```
  Restaurant can only handle N orders simultaneously.
  If at capacity → increase estimated prep time or temporarily hide from search.
  
  Restaurant throttling:
  - Max 50 concurrent orders
  - Order 51 → "Currently busy, try in 15 minutes"
```

---

## 4. Key Points for Interview

1. **Three-sided marketplace**: customers, restaurants, drivers
2. **Geospatial search** for nearby restaurants (Elasticsearch with geo_distance)
3. **Driver matching** similar to Uber (spatial index + optimization)
4. **Order state machine** for tracking lifecycle
5. **ETA = prep_time + pickup_time + delivery_time** (ML model for accuracy)
6. **Real-time tracking** via WebSocket (driver location → customer)
7. **Kafka for events** — order status changes published as events
8. **Peak load handling** — surge pricing for drivers, queue management
