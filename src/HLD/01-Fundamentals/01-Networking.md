# Networking Basics for System Design

> **Difficulty:** Easy | **Time:** 2 hours | **Priority:** Must Know

---

## Why Networking Matters in System Design

Every system design question involves components talking to each other. You MUST understand how data flows between client, server, CDN, databases, and microservices.

---

## 1. DNS (Domain Name System)

DNS translates human-readable domain names to IP addresses.

```
How DNS Resolution Works:

  User types "google.com"
         │
         ▼
  ┌──────────────┐     Cache miss     ┌──────────────────┐
  │  Browser DNS  │ ──────────────►   │   OS DNS Cache    │
  │    Cache      │                   │                    │
  └──────────────┘                    └────────┬───────────┘
                                           Cache miss
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │  ISP Recursive  │
                                      │   DNS Resolver  │
                                      └───────┬────────┘
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
                   ┌────────────┐    ┌──────────────┐    ┌──────────────┐
                   │  Root DNS   │    │   TLD DNS    │    │ Authoritative│
                   │  Server (.) │───►│ Server (.com)│───►│  DNS Server  │
                   └────────────┘    └──────────────┘    │  (google.com)│
                                                          └──────┬───────┘
                                                                 │
                                                          Returns IP:
                                                          142.250.80.46
```

### DNS Record Types
| Record | Purpose | Example |
|--------|---------|---------|
| A | Maps domain → IPv4 | google.com → 142.250.80.46 |
| AAAA | Maps domain → IPv6 | google.com → 2607:f8b0:... |
| CNAME | Alias to another domain | www.google.com → google.com |
| NS | Nameserver for domain | google.com → ns1.google.com |
| MX | Mail server | google.com → mail.google.com |

### Why DNS Matters in Design
- **Load distribution**: DNS can return different IPs (round-robin DNS)
- **Geo-routing**: Route users to nearest datacenter
- **Failover**: Switch traffic when a server goes down
- **TTL (Time-to-Live)**: How long DNS result is cached (trade-off: low TTL = fast failover but more DNS queries)

---

## 2. HTTP / HTTPS

HTTP is the foundation of web communication. Every API call, page load, and asset fetch uses HTTP.

```
HTTP Request-Response Cycle:

  Client                                    Server
    │                                         │
    │──── HTTP Request ──────────────────────►│
    │     GET /api/users HTTP/1.1             │
    │     Host: api.example.com               │
    │     Authorization: Bearer token123      │
    │                                         │
    │◄─── HTTP Response ─────────────────────│
    │     HTTP/1.1 200 OK                     │
    │     Content-Type: application/json      │
    │     { "users": [...] }                  │
    │                                         │
```

### HTTP Methods
| Method | Purpose | Idempotent? | Safe? |
|--------|---------|-------------|-------|
| GET | Read resource | Yes | Yes |
| POST | Create resource | No | No |
| PUT | Replace resource entirely | Yes | No |
| PATCH | Partial update | No | No |
| DELETE | Remove resource | Yes | No |

### HTTP Status Codes (Must Know)
| Range | Meaning | Common Codes |
|-------|---------|-------------|
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirect | 301 Moved, 304 Not Modified |
| 4xx | Client Error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Too Many Requests |
| 5xx | Server Error | 500 Internal Error, 502 Bad Gateway, 503 Service Unavailable |

### HTTP/1.1 vs HTTP/2 vs HTTP/3
```
HTTP/1.1                 HTTP/2                   HTTP/3
┌──────────┐            ┌──────────┐             ┌──────────┐
│ One req  │            │Multiplexed│            │Multiplexed│
│ per conn │            │ streams   │            │ streams   │
│          │            │ on single │            │ over QUIC │
│ Text-    │            │ TCP conn  │            │ (UDP)     │
│ based    │            │           │            │           │
│ headers  │            │ Binary    │            │ No head-  │
│          │            │ framing   │            │ of-line   │
│ No       │            │           │            │ blocking  │
│ server   │            │ Server    │            │           │
│ push     │            │ push      │            │ Faster    │
│          │            │           │            │ handshake │
│ Head-of- │            │ Header    │            │           │
│ line     │            │ compress  │            │ Built-in  │
│ blocking │            │ (HPACK)   │            │ encryption│
└──────────┘            └──────────┘             └──────────┘
```

---

## 3. TCP vs UDP

```
                 TCP                              UDP
        ┌─────────────────┐              ┌─────────────────┐
        │  Connection-     │              │  Connectionless  │
        │  oriented        │              │                  │
        │  (3-way          │              │  No handshake    │
        │   handshake)     │              │                  │
        │                  │              │  No guarantee    │
        │  Guaranteed      │              │  of delivery     │
        │  delivery        │              │                  │
        │                  │              │  No ordering     │
        │  Ordered packets │              │                  │
        │                  │              │  Very fast       │
        │  Flow control    │              │  Low latency     │
        │                  │              │                  │
        │  Slower but      │              │  Used for:       │
        │  reliable        │              │  - Video stream  │
        │                  │              │  - Gaming        │
        │  Used for:       │              │  - DNS queries   │
        │  - HTTP/HTTPS    │              │  - VoIP          │
        │  - Email         │              │                  │
        │  - File transfer │              │                  │
        └─────────────────┘              └─────────────────┘

TCP 3-Way Handshake:
  Client                    Server
    │── SYN ──────────────►│
    │◄─ SYN-ACK ───────────│
    │── ACK ──────────────►│
    │   Connection Open     │
```

### When to Use What in System Design
- **TCP**: API calls, database connections, file transfers, chat messages
- **UDP**: Video streaming, live audio, gaming, DNS lookups, health checks

---

## 4. WebSockets

WebSockets provide full-duplex, persistent connections between client and server.

```
HTTP (Request-Response):                WebSocket (Full Duplex):
                                        
  Client         Server                 Client         Server
    │── Request ──►│                      │── Upgrade ──►│
    │◄── Response ─│                      │◄── 101 ──────│
    │               │                      │               │
    │── Request ──►│                      │◄──── msg ────│  Server pushes!
    │◄── Response ─│                      │──── msg ────►│
    │               │                      │◄──── msg ────│  Server pushes!
    │  (Connection  │                      │               │
    │   closed)     │                      │  (Connection  │
    │               │                      │   stays open) │

  Half-duplex                            Full-duplex
  New connection per request             Single persistent connection
  Client always initiates                Either side can send
```

### Comparison of Real-Time Communication

| Feature | Short Polling | Long Polling | SSE | WebSocket |
|---------|--------------|-------------|-----|-----------|
| Direction | Client→Server | Client→Server | Server→Client | Bidirectional |
| Connection | New each time | Held open | Held open | Persistent |
| Latency | High | Medium | Low | Lowest |
| Overhead | Very High | Medium | Low | Lowest |
| Use Case | Simple checks | Notifications | Live feed | Chat, Gaming |

```
Short Polling:          Long Polling:           Server-Sent Events:
C──req──►S             C──req──────►S          C──req──►S
C◄─res───S             C    (waits) S          C◄─event─S
C──req──►S             C◄─res──────S           C◄─event─S
C◄─res───S             C──req──────►S          C◄─event─S
C──req──►S             C    (waits) S          C◄─event─S
C◄─res───S             C◄─res──────S          (one-directional)
(wasteful!)            (better but still       
                        half-duplex)
```

### When to Use What
- **Short Polling**: Weather updates, stock tickers (where slight delay is OK)
- **Long Polling**: Notifications when WebSocket not available
- **SSE**: Live scores, news feed updates, log streaming
- **WebSocket**: Chat, multiplayer games, collaborative editing, trading platforms

---

## 5. Key Takeaways for Interviews

1. **Always mention DNS** when discussing how a user request reaches your system
2. **Choose the right protocol**: REST for CRUD, WebSocket for real-time, gRPC for microservices
3. **HTTP/2** is preferred for modern APIs (multiplexing, header compression)
4. **TCP vs UDP** choice depends on reliability needs vs latency requirements
5. **WebSockets** are the go-to for any bidirectional real-time feature

---

## Interview Questions to Practice

- "How does a request reach our server from a user's browser?" (DNS → TCP → HTTP)
- "How would you implement real-time notifications?" (WebSocket vs SSE vs Long Polling)
- "Why does YouTube use UDP for streaming?" (Low latency, packet loss is OK)
- "What happens when you type google.com in browser?" (Classic networking question)
