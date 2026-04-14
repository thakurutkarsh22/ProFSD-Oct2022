# CDN, Proxies & API Gateway

> **Difficulty:** Easy-Medium | **Time:** 1.5 hours | **Priority:** Must Know

---

## 1. CDN (Content Delivery Network)

A CDN is a geographically distributed network of servers that caches content close to users.

```
WITHOUT CDN:                           WITH CDN:

  User (India)                          User (India)
      │                                     │
      │ ~200ms latency                      │ ~20ms latency
      │ (across the globe)                  │ (nearby edge)
      ▼                                     ▼
  ┌──────────┐                         ┌──────────┐
  │ Origin   │                         │ CDN Edge │
  │ Server   │                         │ (Mumbai) │
  │ (US-East)│                         └────┬─────┘
  └──────────┘                           Cache│HIT? → Serve directly
                                           │
                                         MISS│ → Fetch from origin
                                           │
                                           ▼
                                      ┌──────────┐
                                      │  Origin  │
                                      │  Server  │
                                      │ (US-East)│
                                      └──────────┘
```

### Push CDN vs Pull CDN

```
PUSH CDN:                              PULL CDN:
─────────                              ─────────
You upload content to CDN              CDN fetches on first request

Origin ──push──► CDN Edge              User ──request──► CDN Edge
                                                          │ MISS
"Here's the new image"                                   │
                                                          ▼
Pros: Content always available          Fetches from Origin, caches it
      No first-request latency
Cons: You manage what's on CDN         Pros: Simple, automatic
      Storage costs                    Cons: First request is slow (cold)
                                             TTL management needed
Use: Video streaming (Netflix)
     Large static files               Use: Images, CSS, JS (most websites)
```

### What to Put on CDN
- Static assets: images, CSS, JS, fonts
- Video content (HLS/DASH segments)
- API responses that rarely change
- HTML pages (for static sites)

### CDN Invalidation
```
Problem: Cached content is stale after an update

Solutions:
1. TTL (Time-To-Live): Content expires after X seconds
2. Versioned URLs: /style.v2.css (new URL = new cache)
3. Cache purge: Explicitly tell CDN to drop cached content
4. Cache-Control headers: no-cache, no-store, max-age
```

---

## 2. Proxy Servers

### Forward Proxy
```
Sits in FRONT of clients. Clients talk through the proxy.

  ┌──────────┐     ┌──────────────┐     ┌──────────┐
  │ Client A │────►│              │────►│          │
  │ Client B │────►│Forward Proxy │────►│  Server  │
  │ Client C │────►│              │────►│          │
  └──────────┘     └──────────────┘     └──────────┘

  Server sees proxy's IP, not client's IP.

  Use cases:
  - Corporate firewalls (block certain websites)
  - Anonymity (hide client IP)
  - Content filtering
  - Caching for clients
```

### Reverse Proxy
```
Sits in FRONT of servers. Clients think they're talking to the server.

  ┌──────────┐     ┌───────────────┐     ┌──────────┐
  │          │────►│               │────►│ Server A │
  │  Client  │────►│ Reverse Proxy │────►│ Server B │
  │          │────►│   (Nginx)     │────►│ Server C │
  └──────────┘     └───────────────┘     └──────────┘

  Client sees proxy's IP, not server's IP.

  Use cases:
  - Load balancing
  - SSL termination
  - Caching
  - Compression
  - DDoS protection
  - A/B testing
```

---

## 3. API Gateway

An API Gateway is a reverse proxy + extra features for microservices.

```
WITHOUT API Gateway:                    WITH API Gateway:

  Client makes 5 calls:                Client makes 1 call:
  
  ┌────────┐                           ┌────────┐
  │ Client │                           │ Client │
  └───┬────┘                           └───┬────┘
      │                                     │
      ├──► User Service                     ▼
      ├──► Order Service              ┌────────────┐
      ├──► Product Service            │ API Gateway │
      ├──► Payment Service            └─────┬──────┘
      └──► Notification Service             │
                                      ┌─────┼──────────────┐
  5 round trips!                      ▼     ▼     ▼        ▼
  Client must know all services     User  Order  Product  Payment
  No centralized auth/logging       Service Service Service Service
```

### API Gateway Responsibilities
```
┌────────────────────────────────────────────────────────┐
│              API GATEWAY FEATURES                       │
├────────────────────────────────────────────────────────┤
│                                                        │
│  ✓ Request Routing      Route /api/users → User Service│
│  ✓ Authentication       Verify JWT/API keys            │
│  ✓ Rate Limiting        Throttle abusive clients       │
│  ✓ Load Balancing       Distribute across instances    │
│  ✓ Request Aggregation  Combine multiple service calls │
│  ✓ SSL Termination      Handle HTTPS at gateway        │
│  ✓ Caching              Cache frequent responses       │
│  ✓ Logging/Monitoring   Centralized request logging    │
│  ✓ Circuit Breaking     Stop calls to failing services │
│  ✓ Request Transform    Modify headers, body           │
│                                                        │
│  Examples: Kong, AWS API Gateway, Nginx, Envoy         │
│                                                        │
└────────────────────────────────────────────────────────┘
```

---

## 4. Service Mesh (Advanced)

For microservice-to-microservice communication.

```
Traditional (API Gateway only):        Service Mesh (Sidecar pattern):

  ┌──────────┐                          ┌─────────────────┐
  │ Service A│──direct──►│Service B│    │ ┌─────┐ ┌─────┐│
  └──────────┘           └────────┘    │ │Svc A│─│Proxy││
                                        │ └─────┘ └──┬──┘│
  No encryption, no retry,             │             │    │
  no circuit breaking between          │     mTLS    │    │
  internal services                    │             │    │
                                        │ ┌─────┐ ┌──┴──┐│
                                        │ │Svc B│─│Proxy││
                                        │ └─────┘ └─────┘│
                                        └─────────────────┘

  Every service gets a sidecar proxy (Envoy/Linkerd)
  Handles: mTLS, retries, circuit breaking, observability
  Examples: Istio, Linkerd, Consul Connect
```

---

## 5. Key Takeaways for Interviews

1. **Always mention CDN** for any system serving static content globally
2. **Reverse proxy (Nginx)** in front of your web servers is standard
3. **API Gateway** is essential in any microservices architecture
4. **CDN = geographic caching**, Cache (Redis) = application caching — different layers
5. **Pull CDN** for most use cases, Push CDN for large media (video streaming)
