# Load Balancing for System Design

> **Difficulty:** Easy | **Time:** 1.5 hours | **Priority:** Must Know

---

## Why Load Balancing Matters

A single server can handle ~1K-10K QPS. Beyond that, you need multiple servers with a load balancer distributing traffic.

```
Without Load Balancer:                  With Load Balancer:

  ┌──────────┐                           ┌──────────┐
  │  Clients  │                           │  Clients  │
  └────┬─────┘                           └────┬─────┘
       │                                       │
       │  ALL traffic                          │
       ▼                                       ▼
  ┌──────────┐                          ┌──────────────┐
  │ Server 1 │ ← overloaded!           │Load Balancer │
  │ 10K QPS  │   crashes!              └──────┬───────┘
  └──────────┘                           ┌────┼────┐
                                         ▼    ▼    ▼
                                      ┌────┐┌────┐┌────┐
                                      │ S1 ││ S2 ││ S3 │
                                      │3.3K││3.3K││3.3K│
                                      └────┘└────┘└────┘
                                      Evenly distributed!
```

---

## 1. Types of Load Balancers

```
┌─────────────────────────────────────────────────────────────┐
│             LOAD BALANCER TYPES                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  L4 (Transport Layer)           L7 (Application Layer)      │
│  ─────────────────────          ────────────────────────     │
│  Works at TCP/UDP level         Works at HTTP/HTTPS level   │
│  Sees: IP + Port                Sees: URL, Headers, Cookies │
│  Very fast (no content          Can make smart routing       │
│  inspection)                    decisions                    │
│                                                             │
│  ┌─────────┐                    ┌─────────┐                 │
│  │ Client  │                    │ Client  │                 │
│  └────┬────┘                    └────┬────┘                 │
│       │ TCP packet                   │ HTTP Request          │
│       ▼                              ▼                       │
│  ┌──────────┐                   ┌──────────┐                │
│  │  L4 LB   │                   │  L7 LB   │                │
│  │Sees: IP, │                   │Sees: URL,│                │
│  │port only │                   │headers,  │                │
│  └────┬─────┘                   │cookies,  │                │
│       │                         │body      │                │
│  Routes by                      └────┬─────┘                │
│  IP hash or                          │                       │
│  round-robin                    /api → API servers           │
│                                 /img → Image servers         │
│  Examples:                      /ws  → WebSocket servers     │
│  - AWS NLB                                                   │
│  - HAProxy (TCP mode)           Examples:                    │
│                                 - AWS ALB                    │
│                                 - Nginx                      │
│                                 - HAProxy (HTTP mode)        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Load Balancing Algorithms

```
1. ROUND ROBIN (Default, Simplest)
   Requests distributed sequentially to each server.
   
   Request 1 → Server A
   Request 2 → Server B
   Request 3 → Server C
   Request 4 → Server A  (cycles back)
   
   Pros: Simple, even distribution
   Cons: Ignores server capacity and current load

2. WEIGHTED ROUND ROBIN
   Servers with more capacity get more requests.
   
   Server A (weight=5): Gets 5 requests
   Server B (weight=3): Gets 3 requests
   Server C (weight=2): Gets 2 requests
   
   Use when: servers have different specs

3. LEAST CONNECTIONS
   Route to server with fewest active connections.
   
   Server A: 5 connections  ← gets next request? NO
   Server B: 2 connections  ← gets next request? YES
   Server C: 8 connections  ← gets next request? NO
   
   Use when: requests have varying processing times

4. IP HASH
   Hash client IP to always route to same server.
   
   hash(client_ip) % num_servers = server_index
   
   User 1 (IP: 1.2.3.4)  → always Server A
   User 2 (IP: 5.6.7.8)  → always Server B
   
   Use when: need session affinity without sticky sessions

5. LEAST RESPONSE TIME
   Route to server with fastest response + fewest connections.
   
   Use when: servers have varying performance

6. RANDOM
   Randomly pick a server. Surprisingly effective at scale.
   
   Use when: all servers are identical, simple setup
```

---

## 3. Load Balancer Placement

```
┌──────────────────────────────────────────────────────────────────┐
│          MULTIPLE LOAD BALANCER LAYERS                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐                                                    │
│  │  Users   │                                                    │
│  └────┬─────┘                                                    │
│       │                                                          │
│       ▼                                                          │
│  ┌────────────┐  Layer 1: Between Users and Web Servers          │
│  │    LB 1    │  (L7 - Nginx/ALB)                                │
│  └──────┬─────┘  SSL termination, path-based routing             │
│    ┌────┼────┐                                                   │
│    ▼    ▼    ▼                                                   │
│  ┌───┐┌───┐┌───┐  Web / API Servers                             │
│  │W1 ││W2 ││W3 │                                                │
│  └─┬─┘└─┬─┘└─┬─┘                                                │
│    └────┼────┘                                                   │
│         ▼                                                        │
│  ┌────────────┐  Layer 2: Between Web and App Servers            │
│  │    LB 2    │  (L4/L7 - internal)                              │
│  └──────┬─────┘                                                  │
│    ┌────┼────┐                                                   │
│    ▼    ▼    ▼                                                   │
│  ┌───┐┌───┐┌───┐  Application / Microservices                   │
│  │A1 ││A2 ││A3 │                                                │
│  └─┬─┘└─┬─┘└─┬─┘                                                │
│    └────┼────┘                                                   │
│         ▼                                                        │
│  ┌────────────┐  Layer 3: Between App and Database               │
│  │    LB 3    │  (Read replicas load balancing)                  │
│  └──────┬─────┘                                                  │
│    ┌────┼────┐                                                   │
│    ▼    ▼    ▼                                                   │
│  ┌───┐┌───┐┌───┐  Database Replicas                             │
│  │DB1││DB2││DB3│                                                │
│  └───┘└───┘└───┘                                                │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. High Availability of Load Balancers

The LB itself can be a single point of failure. Solution: Active-Passive setup.

```
                    ┌──────────┐
                    │  Users   │
                    └────┬─────┘
                         │
                         ▼
           ┌─────────────────────────┐
           │      DNS / VIP          │
           │  (Virtual IP Address)   │
           └────────┬────────────────┘
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
    ┌──────────┐       ┌──────────┐
    │ Active   │       │ Passive  │
    │ LB       │◄─────►│ LB       │
    │ (serves  │ heart │ (standby │
    │  traffic)│ beat  │  ready)  │
    └──────────┘       └──────────┘

If Active LB fails:
  1. Heartbeat stops
  2. Passive LB detects failure
  3. Passive takes over VIP
  4. Traffic flows to new Active
  5. Failover: 1-30 seconds
```

---

## 5. Global Server Load Balancing (GSLB)

For multi-region deployments, route users to the nearest datacenter.

```
   User in India          User in US           User in Europe
        │                     │                      │
        ▼                     ▼                      ▼
  ┌───────────────────────────────────────────────────────┐
  │              GSLB (DNS-based routing)                  │
  │         Routes to nearest healthy datacenter           │
  └──────┬──────────────────┬────────────────────┬────────┘
         │                  │                    │
         ▼                  ▼                    ▼
   ┌──────────┐      ┌──────────┐        ┌──────────┐
   │ Mumbai   │      │ US-East  │        │ Frankfurt│
   │ DC       │      │ DC       │        │ DC       │
   │ LB→Srvrs│      │ LB→Srvrs│        │ LB→Srvrs│
   └──────────┘      └──────────┘        └──────────┘
```

---

## 6. Key Takeaways for Interviews

1. **Always add a load balancer** between clients and servers in your design
2. **L7 LB for web traffic** (path routing, SSL termination), **L4 for internal**
3. **Round Robin is the default** — mention Least Connections for varying workloads
4. **HA setup** — Active-Passive LBs to avoid single point of failure
5. **Health checks** — LB periodically pings servers, removes unhealthy ones
6. **Sticky sessions** — when session state is on a server (avoid if possible, use Redis for sessions instead)
7. **SSL termination at LB** — reduces crypto overhead on application servers
