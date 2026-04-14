# Design Stock Exchange / Trading System

> **Difficulty:** Hard | **Frequency:** ★★★☆☆ | **Companies:** Goldman Sachs, Citadel, Amazon

---

## 1. Requirements

### Functional
- Place orders (buy/sell, limit/market)
- Match buy/sell orders (order matching engine)
- Real-time price updates (market data feed)
- Portfolio management
- Order book maintenance

### Non-Functional
- Ultra-low latency (< 1ms for order matching)
- High throughput (millions of orders/day)
- Strong consistency (no double-spending, correct matching)
- Exactly-once order execution
- Fairness (FIFO ordering)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                     STOCK EXCHANGE SYSTEM                             │
│                                                                      │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────────────┐       │
│  │ Trader   │───►│   Gateway    │───►│   Order Sequencer    │       │
│  │ Client   │    │  (Validate,  │    │   (Single queue,     │       │
│  └──────────┘    │   Auth)      │    │    FIFO ordering)    │       │
│                  └──────────────┘    └──────────┬───────────┘       │
│                                                 │                    │
│                                                 ▼                    │
│                                      ┌──────────────────┐           │
│                                      │  MATCHING ENGINE  │           │
│                                      │  (Core - in memory│           │
│                                      │   single-threaded)│           │
│                                      │                   │           │
│                                      │  Order Book:      │           │
│                                      │  BUY   │  SELL    │           │
│                                      │  $150  │  $151    │           │
│                                      │  $149  │  $152    │           │
│                                      │  $148  │  $153    │           │
│                                      └────────┬─────────┘           │
│                                               │                      │
│                                    ┌──────────┴──────────┐           │
│                                    ▼                     ▼           │
│                             ┌──────────────┐     ┌──────────────┐   │
│                             │ Trade Log    │     │ Market Data  │   │
│                             │ (persist     │     │ Publisher    │   │
│                             │  trades)     │     │ (prices to   │   │
│                             └──────────────┘     │  all clients)│   │
│                                                  └──────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Order Matching Engine

```
ORDER BOOK (per stock):

  BUY ORDERS (Bids)           SELL ORDERS (Asks)
  Sorted: highest first       Sorted: lowest first
  ┌───────┬──────┬──────┐    ┌───────┬──────┬──────┐
  │ Price │ Qty  │ Time │    │ Price │ Qty  │ Time │
  ├───────┼──────┼──────┤    ├───────┼──────┼──────┤
  │ $150  │ 100  │ T1   │    │ $151  │ 200  │ T2   │
  │ $149  │ 50   │ T3   │    │ $152  │ 150  │ T1   │
  │ $148  │ 200  │ T2   │    │ $153  │ 300  │ T3   │
  └───────┴──────┴──────┘    └───────┴──────┴──────┘

MATCHING RULES:
  - Price-Time Priority (FIFO at same price)
  - Buy order matches if buy_price >= lowest ask_price
  
  New order: BUY 100 shares at $152 (limit order)
  → Matches with SELL at $151 (100 of 200 shares)
  → Trade executed at $151
  → Remaining SELL: 100 shares at $151

CRITICAL: Matching engine is SINGLE-THREADED
  Why? Avoids lock contention, guarantees FIFO, deterministic
  One thread processes millions of orders/sec
  (LMAX Disruptor pattern — ring buffer)
```

---

## 4. Key Points for Interview

1. **Single-threaded matching engine** for deterministic ordering
2. **Order book** with price-time priority matching
3. **In-memory processing** for ultra-low latency
4. **Event sourcing** — log every order and trade for replay
5. **Sequencer** ensures FIFO ordering before matching engine
6. **Market data fan-out** via pub/sub to all subscribers
7. **Strong consistency** — no eventual consistency for financial trades
