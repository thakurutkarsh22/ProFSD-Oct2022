# Consistent Hashing

> **Difficulty:** Easy-Medium | **Time:** 2 hours | **Priority:** Must Know
> **Source:** Alex Xu Vol 1 Chapter 5, Amazon Dynamo Paper

---

## The Problem with Simple Hashing

```
Simple Hash: server = hash(key) % N

With 4 servers:
  hash("user_1") % 4 = 1 → Server 1
  hash("user_2") % 4 = 3 → Server 3
  hash("user_3") % 4 = 0 → Server 0

Now ADD Server 4 (N=4 → N=5):
  hash("user_1") % 5 = 3 → Server 3   ← CHANGED! (was 1)
  hash("user_2") % 5 = 0 → Server 0   ← CHANGED! (was 3)
  hash("user_3") % 5 = 2 → Server 2   ← CHANGED! (was 0)

Almost ALL keys need to be remapped!
If this is a cache → massive cache miss → DB overload (cache avalanche)
```

---

## 1. How Consistent Hashing Works

```
Step 1: Create a hash ring (0 to 2^32 - 1)

                        0
                    ┌───────┐
                   /         \
                  /           \
           2^32-1│             │ 1
                 │   HASH      │
                 │   RING      │
           3/4  │             │ 1/4
                  \           /
                   \         /
                    └───────┘
                      1/2

Step 2: Place servers on the ring using hash(server_ip)

                        0
                    ┌───────┐
                   /    S0   \
                  /           \
                 │             │
                 │             │ S1
                 │             │
                  \    S2     /
                   \         /
                    └───────┘

Step 3: Place keys on ring using hash(key), go CLOCKWISE to find server

                        0
                    ┌───────┐
                   /  ●S0    \
                  /  k1↗      \
                 │              │
            k3 ● │              │ ●S1
                 │              │↖k2
                  \   ●S2     /
                   \         /
                    └───────┘

  k1 → clockwise → S0
  k2 → clockwise → S1
  k3 → clockwise → S2
```

### Adding a Server

```
Add S3 between S0 and S1:

                        0
                    ┌───────┐
                   /  ●S0    \
                  /  k1↗      \
                 │        ●S3  │     ← NEW server
                 │              │
            k3 ● │              │ ●S1
                 │              │↖k2
                  \   ●S2     /
                   \         /
                    └───────┘

Only k1 needs to move! (from S0 to S3 — it's now closer to S3)
k2 still → S1 ✓
k3 still → S2 ✓

Only K/N keys move on average! (K=total keys, N=servers)
vs. simple hashing where almost ALL keys move.
```

---

## 2. Virtual Nodes (Vnodes)

Problem: With few servers, distribution is uneven.

```
WITHOUT Virtual Nodes:               WITH Virtual Nodes:
(uneven distribution)                 (even distribution)

        0                                     0
    ┌───────┐                             ┌───────┐
   / S0      \                           / S0a S1b \
  /           \                         /  S2a      \
 │             │                       │ S1a    S0b  │
 │             │S1                     │              │
 │             │                       │ S2b    S1c  │
  \   S2      /                         \  S0c      /
   \         /                           \ S2c S1d /
    └───────┘                             └───────┘

S0 handles 60%                        Each server has multiple
S1 handles 10%  ← unfair!            "virtual" positions on ring
S2 handles 30%                        → much more even distribution

Each real server → 100-200 virtual nodes on the ring
```

### How Virtual Nodes Work
```
Server S0 → Virtual nodes: S0_1, S0_2, S0_3, ..., S0_150
Server S1 → Virtual nodes: S1_1, S1_2, S1_3, ..., S1_150
Server S2 → Virtual nodes: S2_1, S2_2, S2_3, ..., S2_150

All 450 virtual nodes placed on ring.
Key → clockwise → hits virtual node → maps to real server.

Benefits:
1. Even distribution regardless of number of real servers
2. When server fails, its load spreads across ALL remaining servers
3. Heterogeneous servers: powerful server gets MORE virtual nodes
```

---

## 3. Consistent Hashing in Real Systems

```
┌──────────────────────────────────────────────────────────────┐
│            WHERE CONSISTENT HASHING IS USED                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Amazon DynamoDB                                             │
│  ─ Partitioning data across storage nodes                    │
│  ─ From the famous Dynamo paper (2007)                       │
│                                                              │
│  Apache Cassandra                                            │
│  ─ Distributing data across cluster nodes                    │
│  ─ Virtual nodes for rebalancing                             │
│                                                              │
│  Discord                                                     │
│  ─ Routing messages to correct server                        │
│  ─ Scaled to 5M concurrent users                             │
│                                                              │
│  Akamai CDN                                                  │
│  ─ Consistent hashing was invented at Akamai!                │
│  ─ Distributing content across edge servers                  │
│                                                              │
│  Memcached / Redis Cluster                                   │
│  ─ Client-side consistent hashing for cache sharding         │
│                                                              │
│  Load Balancers                                              │
│  ─ Google Maglev (software load balancer)                    │
│  ─ Consistent hashing for connection affinity                │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Key Takeaways for Interviews

1. **Use consistent hashing** whenever you need to distribute data across N servers
2. **Virtual nodes** solve the uneven distribution problem
3. When adding/removing a server, only **K/N keys** need to move (minimal disruption)
4. Mention it for: cache sharding, database sharding, load balancing, CDN
5. Real-world proof points: DynamoDB, Cassandra, Discord, Akamai
