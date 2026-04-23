# Service Discovery & Membership

> **Difficulty:** Medium | **Time:** 1.5 hours | **Priority:** Must Know

In a dynamic system where instances come and go, a caller needs to
answer two questions every time it makes a request:
**"Who provides this service?"** and **"Where (IP:port) are they right now?"**
This document walks through the canonical discovery patterns
(client-side, server-side, service mesh), the membership protocols
behind them, and the operational gotchas that bite in production.

---

## Table of Contents

1. [What Discovery Solves](#1-what-discovery-solves)
2. [The Registration Step](#2-the-registration-step)
3. [Client-Side Discovery](#3-client-side-discovery)
4. [Server-Side Discovery (via Load Balancer)](#4-server-side-discovery-via-load-balancer)
5. [Service Mesh](#5-service-mesh-sidecar-discovery)
6. [DNS as Discovery](#6-dns-as-discovery)
7. [Health Checks & Deregistration](#7-health-checks--deregistration)
8. [Membership Protocols](#8-membership-protocols)
9. [Real-World Systems](#9-real-world-systems)
10. [Interview Q&A](#10-interview-qa)

---

## 1. What Discovery Solves

```
 Before:
   Client code:  call("orders-service:8080")
                        │
                        ▼
           fixed IP — breaks when:
              - instance restarts with new IP
              - scaling up/down
              - failover to different AZ
              - blue/green deployment

 After (with discovery):
   Client code:  call("orders-service")
                        │
                        ▼
           Discovery layer returns a CURRENT list of healthy instances.
           Caller picks one; if it fails, picks another.
```

### 1.1 Concerns it addresses

```
  DYNAMIC TOPOLOGY    instances added/removed constantly
  FAILURE ROUTING     don't send to dead instances
  LOAD BALANCING      distribute requests fairly
  HETEROGENEITY       v1 and v2 of service coexist during deploy
  GEO-PROXIMITY       prefer same-AZ instance
```

---

## 2. The Registration Step

Before anyone can discover an instance, the instance has to
**register its existence** somewhere.

### 2.1 Self-registration

```
  Instance on startup:
      registry.register(svc="orders", host="10.0.1.42", port=8080)

  Instance periodically renews its entry (heartbeat lease):
      registry.renew(svc="orders", host="10.0.1.42", ttl=30s)

  Instance on shutdown:
      registry.deregister(svc="orders", host="10.0.1.42")

  If the instance CRASHES, its lease expires → registry drops it.
```

### 2.2 Third-party registration (sidecar / orchestrator)

```
  In Kubernetes: the Service + kubelet handle registration via:
     - Pod IPs go into Endpoints object
     - kube-proxy updates iptables / IPVS rules
     - kubelet reports pod readiness

  In Consul: a local agent on each host registers its services.
  In Nomad / ECS: the scheduler registers running tasks.

  No app-code changes needed.
```

### 2.3 Self- vs Third-party trade-offs

```
  SELF-REGISTRATION          THIRD-PARTY
  ─────────────────          ────────────
  + Simple (library call)    + No app dependency
  + App knows its readiness  + Orchestrator source of truth
  − Code must handle         − Needs infra (K8s, Consul, etc.)
    retries / deregister
  − Language-specific clients
  − App failures can leave    
    stale entries             
```

Most modern stacks use **third-party registration** via Kubernetes or
Consul sidecars.

---

## 3. Client-Side Discovery

```
        ┌────────────┐
        │  Client    │
        └─────┬──────┘
              │ 1. ask registry
              ▼
        ┌────────────┐
        │  Registry  │   (Consul, etcd, Eureka)
        └─────┬──────┘
              │ 2. returns [inst1, inst2, inst3]
              ▼
        ┌────────────┐
        │  Client    │
        └─────┬──────┘
              │ 3. pick one (RR / random / latency)
              ▼
      ┌───────┴───────┐
      ▼       ▼       ▼
   inst1   inst2   inst3
```

### 3.1 Pros

```
  ✓ No extra network hop (client → server directly)
  ✓ Smart routing (pick by latency, zone affinity, least-loaded)
  ✓ Simpler infra (no LB box to scale)
```

### 3.2 Cons

```
  ✗ Requires a DISCOVERY CLIENT in EVERY language
  ✗ Clients cache stale data (need invalidation / TTL)
  ✗ Clients must handle retries / failover logic
  ✗ Can create thundering herd on registry
```

### 3.3 Caching & staleness

Every client caches "who provides X?" locally:

```
  Cache: { "orders": [A, B, C], ttl=30s }

  On cache miss or TTL expiry → re-query registry.
  On call failure → evict that instance + retry another.
  Listen for registry push updates (watch API, streaming RPC).
```

### 3.4 Examples

```
  Netflix Eureka + Ribbon        classic Java stack
  Finagle (Twitter)              client-side LB + discovery
  gRPC with xDS                  modern: Envoy control plane
  Kubernetes headless service    DNS returns all pod IPs, client picks
```

---

## 4. Server-Side Discovery (via Load Balancer)

```
        ┌────────────┐
        │  Client    │   Client just calls a stable LB address.
        └─────┬──────┘
              │
              ▼
        ┌────────────┐
        │    LB      │   LB queries registry + routes to instance.
        └─────┬──────┘
              │
      ┌───────┴───────┐
      ▼       ▼       ▼
   inst1   inst2   inst3
```

### 4.1 Pros

```
  ✓ Client code is dumb: just call "https://orders.example.com"
  ✓ LB handles health / failover / LB algorithms centrally
  ✓ Works for any client language
  ✓ Can enforce policies (TLS, auth, rate limit) in one place
```

### 4.2 Cons

```
  ✗ Extra hop → added latency
  ✗ LB itself must be highly available (→ HA LB, Anycast, etc.)
  ✗ LB may become bottleneck
  ✗ Less locality-aware routing (unless LB is aware)
```

### 4.3 Examples

```
  AWS ALB / NLB
  GCP HTTPS Load Balancer
  HAProxy, Nginx with active health checks
  K8s Service (ClusterIP via kube-proxy)
  Traefik, Envoy as ingress
```

---

## 5. Service Mesh (sidecar discovery)

A hybrid: each pod runs a **sidecar proxy** (Envoy) that acts like a
local LB. The app calls the sidecar at `localhost:port`, and the
sidecar talks to peers.

### 5.1 Architecture

```
   ┌──────────── pod ─────────────┐        ┌──────────── pod ─────────────┐
   │                              │        │                              │
   │   ┌──────┐    ┌──────────┐   │        │   ┌──────┐    ┌──────────┐   │
   │   │ App  │─►─►│  Envoy   │───┼────────┼─►─│Envoy │─►─►│  App     │   │
   │   │      │    │(sidecar) │   │        │   │      │    │          │   │
   │   └──────┘    └────┬─────┘   │        │   └──────┘    └──────────┘   │
   │                    │         │        │                              │
   └────────────────────┼─────────┘        └──────────────────────────────┘
                        │
                        ▼
                 ┌────────────┐
                 │  Control   │   Istio / Linkerd pushes config
                 │   Plane    │   (endpoints, mTLS, retries, etc.)
                 └────────────┘
```

### 5.2 Pros

```
  ✓ Language-agnostic: app is unaware of discovery
  ✓ Client-side LB benefits (smart routing, latency)
  ✓ Centrally-controlled policies (retries, mTLS, circuit breakers)
  ✓ Observability (metrics, tracing) out of the box
```

### 5.3 Cons

```
  ✗ Operational complexity (extra component per pod)
  ✗ CPU / memory overhead (small per-pod cost)
  ✗ Control-plane is another critical dependency
```

### 5.4 Examples

```
  Istio + Envoy
  Linkerd (Rust, simpler)
  Consul Connect + Envoy
  AWS App Mesh
```

---

## 6. DNS as Discovery

DNS is the original service-discovery mechanism. It still works for
many use cases.

### 6.1 Simple A / AAAA records

```
  orders.svc.cluster.local. →  10.0.1.42, 10.0.1.43, 10.0.1.44
  Client resolves → picks one (often first, unless smart resolver).
```

### 6.2 SRV records

```
  _http._tcp.orders.example.com.
   priority=10 weight=5 port=8080 target=orders-1.ex.com
   priority=10 weight=5 port=8080 target=orders-2.ex.com
   priority=20 weight=5 port=8080 target=orders-dr.ex.com

  Gives clients hostname + port + priority + weight.
```

### 6.3 DNS pitfalls

```
  ✗ TTL granularity (seconds minimum) → slow failover
  ✗ Negative caching → dead IPs linger
  ✗ Some clients cache FOREVER (JVM default) — you must tune
  ✗ Only A/AAAA → no health metadata
  ✗ UDP truncation at 512 bytes → limited number of A records
```

### 6.4 When DNS is enough

```
  ✓ Internal K8s (CoreDNS + readiness probes remove bad pods
     before DNS even returns them)
  ✓ Public-facing services behind CDNs + health-checked DNS
     (Route53 health-checked records, etc.)
  ✓ Simple environments with low churn
```

### 6.5 K8s "Headless Service" trick

```
  A ClusterIP:None service in K8s:
     DNS returns ALL healthy pod IPs for the service.
  Client-side picks one.
  This is K8s' native client-side discovery.
```

---

## 7. Health Checks & Deregistration

Stale entries in the registry point clients at dead instances. Vital
to keep fresh.

### 7.1 Health-check types

```
  ACTIVE        Registry periodically pings instance
                (GET /health every 5s).

  PASSIVE       Registry observes real client traffic and evicts
                instances with too many failures.

  SELF-REPORT   Instance sends "I'm healthy" heartbeats; registry
                drops entries whose lease expires.
```

### 7.2 Liveness vs readiness

```
  LIVENESS      "am I still running?"
                - Fails → restart the container (k8s restartPolicy)

  READINESS     "should I receive traffic RIGHT NOW?"
                - Fails → remove from LB / discovery
                - Don't restart; the pod may need to warm up caches

  STARTUP       "have I finished initial setup?"
                - Fails during boot → give more time; don't restart yet
```

### 7.3 The "drain" step

On shutdown, a graceful instance:

```
  1. Mark self as NOT ready (/readyz returns 503).
  2. LB / discovery notices → stops sending new requests.
  3. Wait for in-flight requests to finish (drain window, 10-30s).
  4. Close listening sockets.
  5. Exit.

  Without drain: connections get RST, in-flight requests fail.
```

### 7.4 Stale registration bugs

```
  INSTANCE CRASHES without calling deregister:
     - Self-heartbeats stop → lease expires → evicted.
     - But there's a window (lease_ttl) of dead entries.

  LEASE_TTL too short:
     - Blips → false evictions → churn.

  LEASE_TTL too long:
     - Dead entries linger → clients get errors until expiry.

  Typical defaults: ~30s heartbeat, 90s TTL.
```

---

## 8. Membership Protocols

The registry itself must agree on "who's in the cluster." Two camps:

### 8.1 Strongly consistent (consensus)

```
  etcd / Consul / ZooKeeper:
     Membership changes go through Raft / ZAB consensus.
     Every node sees the SAME membership at any linearizable read.

  Pros: reliable, ordered, linearizable
  Cons: scale limit (typically 5-7 voting members);
        requires quorum to write (unavailable during partition minority)
```

### 8.2 Gossip-based

```
  Serf (HashiCorp) / Cassandra / ScyllaDB / Netflix Eureka:
     Nodes gossip membership changes peer-to-peer.
     Eventual consistency; scales to 10,000+ nodes.

  Pros: no single point of failure, enormous scale
  Cons: eventual consistency (brief disagreement windows)
```

### 8.3 Hybrid

```
  Consul:  Raft for KV + ACLs + central state;
           gossip (Serf) for membership across possibly-many DCs.

  Best of both — strong where it matters, scalable where it doesn't.
```

See [`04-FailureDetection.md`](./04-FailureDetection.md) for
details on SWIM / gossip and [`../02-Consensus.md`](../02-Consensus.md)
for Raft.

---

## 9. Real-World Systems

```
┌───────────────────┬──────────────────────────────────────────────┐
│ System            │ Discovery / membership approach               │
├───────────────────┼──────────────────────────────────────────────┤
│ Kubernetes        │ kube-apiserver + kube-proxy + CoreDNS         │
│                   │ (watched Endpoints object, iptables rules)    │
│ Consul            │ Raft (KV) + Serf/SWIM (gossip membership)     │
│ etcd              │ Raft only; watched DNS-SRV for discovery      │
│ ZooKeeper         │ ZAB consensus; ephemeral znodes + watches     │
│ Eureka (Netflix)  │ AP registry, peer replication; self-register  │
│ Istio / Envoy xDS │ Control plane pushes endpoints to sidecars    │
│ AWS Cloud Map     │ Managed, integrates w/ Route 53 + ECS         │
│ Service Fabric    │ Microsoft's registry + placement service      │
│ Nomad             │ Consul-based discovery                        │
│ Cassandra         │ Gossip + token-ring ownership                 │
└───────────────────┴──────────────────────────────────────────────┘
```

---

## 10. Interview Q&A

### Q1. "How does Kubernetes do service discovery?"

> Every Service has a stable virtual IP (ClusterIP) and a DNS name.
> kube-proxy on each node watches Endpoints objects (the live list of
> healthy pod IPs) and sets up iptables/IPVS rules to DNAT traffic
> destined for the ClusterIP to a random pod. CoreDNS serves DNS
> names as A records pointing to the ClusterIP (or all pod IPs for
> headless Services).

### Q2. "Client-side vs server-side discovery — which do you prefer?"

> Depends on control over clients and scale needs:
> - **Client-side** (Eureka/Ribbon, gRPC xDS): best latency, smart
>   routing, no LB hop. Needs a discovery library in every language.
> - **Server-side** (AWS ALB, K8s kube-proxy): simpler clients, any
>   language, centralized policy enforcement. Extra hop.
> - **Service mesh** (Istio/Linkerd): best of both — client-side
>   logic without touching app code, at the cost of running a sidecar.

### Q3. "How does a service in K8s learn about its peers moving?"

> It doesn't — kube-proxy does. When a pod starts/stops/fails its
> readiness probe, the kube-apiserver updates the Endpoints object.
> Every kube-proxy is watching; each updates its local iptables/IPVS
> rules. Traffic routed to the ClusterIP automatically picks healthy
> pods. Clients simply call the Service name.

### Q4. "A client keeps calling a dead instance. What's wrong?"

> Stale cache / DNS. Causes:
> - TTL too long or JVM caching forever (fix: `networkaddress.cache.ttl`).
> - Instance didn't deregister on shutdown (fix: graceful shutdown +
>   readiness probe false before exit).
> - Heartbeat TTL too long → stale entry survived crash.
> - Caller isn't evicting failing endpoints from its local LB pool
>   (fix: passive outlier detection in Envoy/Ribbon).

### Q5. "Why is DNS alone often not enough for service discovery?"

> Second-granularity TTLs are too slow for high-churn environments,
> negative caching holds stale records, A records have limited metadata
> (no health / weight info), and misbehaving clients cache aggressively.
> Modern systems use DNS as a convenient naming layer on top of a
> richer discovery system (K8s Endpoints, Consul catalog, Envoy xDS).

### Q6. "What's the purpose of a readiness probe vs a liveness probe?"

> **Liveness** asks "should Kubernetes restart me?" — if false, kill
> and respawn. **Readiness** asks "should the LB send me traffic?" —
> if false, remove from Service Endpoints but keep running. You want
> readiness to fail first (warm-up, graceful shutdown drain); you
> want liveness to be a harsh safety net (hung thread, deadlock).

### Q7. "What is a service mesh and when do you need one?"

> A service mesh (Istio, Linkerd) adds a sidecar proxy to every pod
> that handles discovery, retries, mTLS, circuit breaking, tracing —
> without touching application code. You need it when:
> - You run many microservices in many languages,
> - You need per-service policy (retries, mTLS, rate limits)
>   without rewriting code,
> - Observability across all calls matters.
>
> You probably don't need it if you run < 10 services in one language.

### Q8. "What's the 'thundering herd' risk in client-side discovery?"

> When many clients simultaneously refresh their registry cache (e.g.,
> on TTL expiry or after a registry restart), they all hit the registry
> at once, potentially overwhelming it. Mitigations: stagger TTLs
> with jitter, use streaming / watch APIs so the registry PUSHES
> changes, cache aggressively with eventual consistency, and run the
> registry itself in a highly available cluster.

### Q9. "How do you deregister gracefully on shutdown?"

> Signal readiness=false immediately (discovery/LB removes you).
> Wait for a drain window (10-30s) so in-flight requests complete and
> upstreams observe the change. Close listen sockets. Finalize
> cleanup. Exit. In Kubernetes: set `terminationGracePeriodSeconds`
> longer than your drain, and use a `preStop` hook to call a
> /drain endpoint.

### Q10. "How does Consul combine strong and eventual consistency?"

> Consul uses **Raft** for its central KV store (strong, linearizable)
> and **Serf/SWIM gossip** for failure detection and membership
> propagation across potentially thousands of nodes and multiple
> datacenters. Strong where correctness matters; gossip where scale
> matters.

---

## 11. Further Reading

- Chris Richardson, "Microservices Patterns" — discovery patterns
- Netflix Tech Blog — Eureka design and postmortems
- HashiCorp Consul docs — architecture overview
- Envoy xDS API docs — modern discovery control plane
- Kubernetes docs — Services, Endpoints, kube-proxy
- Das/Gupta/Motivala, "SWIM" (2002) — gossip membership foundations

---

> **Previous:** [12-BackPressureAndRetries.md](./12-BackPressureAndRetries.md) ·
> **Next:** [14-DeadlockDetection.md](./14-DeadlockDetection.md)
