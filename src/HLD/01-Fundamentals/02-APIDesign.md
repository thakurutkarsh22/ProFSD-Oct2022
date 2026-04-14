# API Design for System Design

> **Difficulty:** Easy | **Time:** 2 hours | **Priority:** Must Know

---

## Why API Design Matters

In every system design interview, you'll need to define APIs. The interviewer evaluates:
- Can you define clean, clear interfaces?
- Do you understand different API paradigms and their trade-offs?
- Can you version, paginate, and handle errors properly?

---

## 1. REST (Representational State Transfer)

The most common API style. Resource-based, uses HTTP methods.

```
REST API Design:

  Client                           Server
    │                                │
    │  GET /api/v1/users             │  → List users
    │  GET /api/v1/users/123         │  → Get user 123
    │  POST /api/v1/users            │  → Create user
    │  PUT /api/v1/users/123         │  → Replace user 123
    │  PATCH /api/v1/users/123       │  → Update user 123
    │  DELETE /api/v1/users/123      │  → Delete user 123
    │                                │
    │  GET /api/v1/users/123/orders  │  → User's orders (nested)
    │                                │
```

### REST Best Practices
```
Good URL Design:                      Bad URL Design:
─────────────────                     ─────────────────
GET  /users                           GET  /getUsers
GET  /users/123                       GET  /getUserById?id=123
POST /users                           POST /createUser
GET  /users/123/orders                GET  /getUserOrders?userId=123
GET  /users?status=active&page=2      GET  /searchActiveUsers?page=2

Rules:
✓ Use nouns, not verbs (HTTP method IS the verb)
✓ Use plural nouns (/users not /user)
✓ Use kebab-case for multi-word (/user-profiles)
✓ Nest related resources (/users/123/orders)
✓ Version your API (/v1/, /v2/)
```

### Pagination
```
Offset-Based:                    Cursor-Based:
GET /users?page=2&limit=20       GET /users?cursor=abc123&limit=20

Response:                         Response:
{                                 {
  "data": [...],                    "data": [...],
  "page": 2,                       "next_cursor": "def456",
  "total": 100,                    "has_more": true
  "total_pages": 5                }
}

Pros: Simple, jump to any page    Pros: Consistent with real-time data
Cons: Slow for large offsets      Cons: Can't jump to page N
      Inconsistent if data changes       More complex
```

---

## 2. GraphQL

Client specifies exactly what data it needs. Solves over-fetching/under-fetching.

```
REST Problem (Multiple Requests):     GraphQL Solution (Single Request):

GET /users/123         → user data     POST /graphql
GET /users/123/posts   → posts         {
GET /users/123/friends → friends         user(id: 123) {
                                           name
3 round trips!                             posts { title }
Over-fetching fields                       friends { name }
you don't need                           }
                                        }

                                        1 round trip!
                                        Only requested fields returned
```

### When to Use GraphQL
- **Mobile apps** (minimize bandwidth, client picks fields)
- **Complex data relationships** (social graph, nested resources)
- **Multiple frontend clients** needing different data shapes

### When NOT to Use GraphQL
- Simple CRUD APIs
- File uploads
- Real-time streaming (use WebSockets)
- Caching is more complex than REST

---

## 3. gRPC (Google Remote Procedure Call)

Binary protocol using Protocol Buffers. Designed for microservice-to-microservice communication.

```
REST (JSON over HTTP/1.1):              gRPC (Protobuf over HTTP/2):

┌────────┐  JSON    ┌────────┐         ┌────────┐ Protobuf ┌────────┐
│Service │ ──────► │Service │         │Service │ ────────►│Service │
│   A    │  ~1KB   │   B    │         │   A    │  ~200B   │   B    │
└────────┘         └────────┘         └────────┘          └────────┘
                                      
  - Text-based (verbose)               - Binary (compact, 60-80% smaller)
  - HTTP/1.1 (one req/conn)            - HTTP/2 (multiplexed streams)
  - Schema-less                         - Strict schema (.proto files)
  - Easy to debug (curl)               - Needs special tools to debug
  - Unary only                          - Supports 4 streaming modes
```

### gRPC Streaming Modes
```
1. Unary (like REST):        Client ──req──► Server
                              Client ◄──res── Server

2. Server Streaming:          Client ──req──► Server
                              Client ◄──res── Server
                              Client ◄──res── Server
                              Client ◄──res── Server

3. Client Streaming:          Client ──req──► Server
                              Client ──req──► Server
                              Client ──req──► Server
                              Client ◄──res── Server

4. Bidirectional:             Client ──req──► Server
                              Client ◄──res── Server
                              Client ──req──► Server
                              Client ◄──res── Server
```

---

## 4. Comparison Table

| Feature | REST | GraphQL | gRPC |
|---------|------|---------|------|
| Protocol | HTTP/1.1+ | HTTP | HTTP/2 |
| Data Format | JSON | JSON | Protobuf (binary) |
| Schema | Optional (OpenAPI) | Required (SDL) | Required (.proto) |
| Performance | Good | Good | Excellent |
| Browser Support | Excellent | Good | Limited (needs proxy) |
| Best For | Public APIs | Flexible frontends | Microservices |
| Caching | Easy (HTTP caching) | Complex | Hard |
| Learning Curve | Low | Medium | Medium-High |
| Streaming | No (use WebSocket) | Subscriptions | Built-in (4 modes) |

---

## 5. API Design Patterns for Interviews

### Rate Limiting Headers
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1623456789
```

### Idempotency Keys (for Payment/Critical APIs)
```
POST /api/v1/payments
Idempotency-Key: unique-uuid-here    ← Prevents duplicate payments
{
  "amount": 100,
  "currency": "USD"
}
```

### API Versioning Strategies
```
1. URL Path:     /api/v1/users    (Most common, recommended)
2. Header:       X-API-Version: 1
3. Query Param:  /api/users?version=1
4. Content Type: Accept: application/vnd.api.v1+json
```

### Error Response Format
```json
{
  "error": {
    "code": "INVALID_INPUT",
    "message": "Email format is invalid",
    "details": [
      {
        "field": "email",
        "reason": "Must be a valid email address"
      }
    ],
    "request_id": "req_abc123"
  }
}
```

---

## 6. Key Takeaways for Interviews

1. **Default to REST** for public APIs and most interview questions
2. **Use gRPC** when designing internal microservice communication
3. **Use GraphQL** when asked about mobile apps or complex frontend data needs
4. **Always define your API** early in the interview (endpoints, methods, params)
5. **Mention pagination** for any list endpoint
6. **Mention idempotency** for write operations (especially payments)
7. **Version your APIs** from the start
