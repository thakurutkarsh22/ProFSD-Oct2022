package com.demo.sqs;

/**
 * Prints educational concept explanations before each SQS operation.
 * Maps directly to the concepts in the SQS deep-dive documentation.
 */
public class ConceptExplainer {

    public static void sendSingleMessage() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: SQS Message Lifecycle                                     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Producer ──SendMessage──► [SQS Queue] ──ReceiveMessage──► Consumer │
        │                                │                                    │
        │                                ▼                                    │
        │                          DeleteMessage                              │
        │                       (after processing)                            │
        │                                                                     │
        │  A message in SQS goes through these states:                        │
        │                                                                     │
        │  1. SENT     → message lands in queue, gets a unique MessageId      │
        │  2. AVAILABLE → visible to consumers (ready for ReceiveMessage)     │
        │  3. IN-FLIGHT → received by a consumer, invisible to others         │
        │                 (visibility timeout running)                         │
        │  4. DELETED  → consumer calls DeleteMessage after processing        │
        │                                                                     │
        │  KEY POINT: SQS is a TRANSIENT queue. Messages are consumed and     │
        │  deleted. This is NOT an event log (unlike Kafka where messages      │
        │  are retained). Once deleted, the message is gone forever.          │
        │                                                                     │
        │  MessageId: UUID assigned by SQS. Used for tracking/logging only.  │
        │  MD5: Hash of the body — you can verify integrity on receive.       │
        │                                                                     │
        │  Max message size: 256 KB (use S3 for larger payloads via           │
        │  Extended Client Library)                                           │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void messageAttributes() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Message Attributes (Metadata)                             │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SQS messages have TWO places to put data:                          │
        │                                                                     │
        │  1. MESSAGE BODY (required):                                        │
        │     The actual payload. Up to 256 KB. Usually JSON.                │
        │     → Your business data goes here                                  │
        │                                                                     │
        │  2. MESSAGE ATTRIBUTES (optional, up to 10):                        │
        │     Key-value metadata. Separate from body.                         │
        │     Types: String, Number, Binary                                   │
        │     → Routing hints, source info, priority, tracing IDs            │
        │                                                                     │
        │  WHY separate body from attributes?                                 │
        │  • Consumers can filter/route based on attributes WITHOUT           │
        │    parsing the body (cheaper, faster)                               │
        │  • SNS filter policies work on attributes, not body                │
        │  • Attributes survive the same lifecycle as the message             │
        │                                                                     │
        │  SYSTEM ATTRIBUTES (read-only, set by SQS):                         │
        │  • ApproximateReceiveCount — how many times this msg was received   │
        │  • SentTimestamp — epoch millis when SendMessage was called          │
        │  • ApproximateFirstReceiveTimestamp                                  │
        │  • MessageGroupId, SequenceNumber (FIFO only)                       │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void batchOperations() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Batch Operations                                          │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SQS charges PER REQUEST, not per message. Batching = cost savings. │
        │                                                                     │
        │  WITHOUT batching:           WITH batching (10 messages):           │
        │  10 × SendMessage = 10 req   1 × SendMessageBatch = 1 req          │
        │  10 × $0.40/1M = $4/10M      1 × $0.40/1M = $0.40/10M             │
        │                                                                     │
        │  Batch limits:                                                      │
        │  • Max 10 messages per batch                                        │
        │  • Total batch size ≤ 256 KB                                        │
        │  • Each entry needs a unique ID (within the batch)                  │
        │                                                                     │
        │  Batch operations available:                                        │
        │  • SendMessageBatch     — send up to 10 messages in one call       │
        │  • DeleteMessageBatch   — delete up to 10 messages in one call     │
        │  • ChangeMessageVisibilityBatch — extend/change visibility          │
        │                                                                     │
        │  PARTIAL FAILURE: A batch can partially succeed. Always check       │
        │  both resp.successful() and resp.failed(). A batch is NOT atomic.  │
        │                                                                     │
        │  COST OPTIMIZATION at scale:                                        │
        │  1B msgs/month → batched (10/batch) = 100M requests = $40          │
        │  1B msgs/month → individual          = 1B requests   = $400        │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void delayedMessages() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Message Delay (DelaySeconds)                              │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Timeline:                                                          │
        │                                                                     │
        │  t=0s         t=30s (DelaySeconds=30)        t=60s                  │
        │  ──┬──────────────┬──────────────────────────┬──                    │
        │    │   DELAYED    │       AVAILABLE           │                      │
        │    │  (invisible) │    (consumers can see)     │                      │
        │    SendMessage    Message becomes visible      Received              │
        │                                                                     │
        │  TWO ways to delay:                                                 │
        │                                                                     │
        │  1. Per-message delay: DelaySeconds (0-900) on SendMessage          │
        │     → Each message can have different delay                         │
        │                                                                     │
        │  2. Queue-level delay: Set on the queue itself (default=0)          │
        │     → ALL messages delayed by this amount                           │
        │     → Per-message delay OVERRIDES queue delay (Standard only)       │
        │     → FIFO: per-message delay NOT supported, only queue-level       │
        │                                                                     │
        │  Use cases:                                                         │
        │  • Retry with backoff: send failed item back with increasing delay  │
        │  • Scheduled tasks: "send email in 5 minutes"                       │
        │  • Rate limiting: slow down downstream processing                   │
        │  • Eventual consistency windows: delay replication checks           │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void deduplication() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: MessageDeduplicationId — Exactly-Once in FIFO Queues      │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  ═══ The Problem: Why Do We Need Deduplication? ═══                 │
        │                                                                     │
        │  In distributed systems, producers RETRY on timeout/failure:        │
        │                                                                     │
        │  Producer ──send──► SQS ──ack──► (network drops ack)                │
        │  Producer thinks it failed, so it RETRIES:                          │
        │  Producer ──send──► SQS     ← now SQS has the message TWICE!       │
        │                                                                     │
        │  Without dedup: consumer processes the same order/payment twice.    │
        │  With dedup:    SQS detects the retry and silently drops it.        │
        │                                                                     │
        │  ═══ Two Deduplication Modes (FIFO only) ═══                        │
        │                                                                     │
        │  MODE 1: Content-Based Deduplication                                │
        │  ─────────────────────────────────────                              │
        │  Queue setting: ContentBasedDeduplication=true                       │
        │  SQS computes SHA-256 hash of the message body.                     │
        │  Same body within 5 minutes → duplicate → silently ignored.         │
        │                                                                     │
        │    msg1: body="charge $50"  → hash=abc123 → ACCEPTED               │
        │    msg2: body="charge $50"  → hash=abc123 → DUPLICATE (same hash)  │
        │    msg3: body="charge $60"  → hash=def456 → ACCEPTED (diff hash)   │
        │                                                                     │
        │  Pros: Zero code changes. Just enable it on the queue.              │
        │  Cons: If you WANT to send the same body twice (legit), you can't.  │
        │                                                                     │
        │                                                                     │
        │  MODE 2: Explicit MessageDeduplicationId                            │
        │  ─────────────────────────────────────────                          │
        │  Queue setting: ContentBasedDeduplication=false                      │
        │  You provide a dedup ID on each SendMessage call.                   │
        │  Same dedup ID within 5 minutes → duplicate → silently ignored.     │
        │  Body is IRRELEVANT — only the dedup ID matters.                    │
        │                                                                     │
        │    msg1: dedupId="pay-ORD-1-v1", body="$50"  → ACCEPTED            │
        │    msg2: dedupId="pay-ORD-1-v1", body="$99"  → DUPLICATE (same ID) │
        │    msg3: dedupId="pay-ORD-1-v2", body="$50"  → ACCEPTED (diff ID)  │
        │                                                                     │
        │  Pros: Full control. Same body with different IDs = allowed.        │
        │  Cons: You must generate and manage dedup IDs yourself.             │
        │                                                                     │
        │  Common patterns for dedup IDs:                                     │
        │    • "order-{orderId}-payment-{attempt}"                            │
        │    • "{requestId}" (from API gateway)                               │
        │    • "{idempotencyKey}" (from client)                               │
        │                                                                     │
        │                                                                     │
        │  ═══ The 5-Minute Window ═══                                        │
        │                                                                     │
        │  SQS only remembers dedup IDs for 5 minutes. After that:           │
        │  • Same dedup ID / same body → accepted as NEW message              │
        │  • This is a hard limit — not configurable                          │
        │  • For longer dedup windows, add application-level idempotency:     │
        │                                                                     │
        │    Layer 1: FIFO dedup handles retries within 5 min (SQS-level)     │
        │    Layer 2: DB idempotency key for retries beyond 5 min (app-level) │
        │                                                                     │
        │                                                                     │
        │  ═══ How SQS Signals a Duplicate ═══                                │
        │                                                                     │
        │  SQS does NOT return an error for duplicates!                       │
        │  It returns HTTP 200 with the SAME SequenceNumber as the original. │
        │  This is how you can detect dedup happened:                         │
        │    msg1.sequenceNumber == msg2.sequenceNumber → duplicate           │
        │                                                                     │
        │                                                                     │
        │  ═══ Standard Queues: No Dedup ═══                                  │
        │                                                                     │
        │  Standard queues have NO built-in deduplication.                    │
        │  At-least-once delivery → consumer must be idempotent.              │
        │  Compare to Kafka: idempotent producer (PID + sequence number)      │
        │  handles dedup per partition, but requires enable.idempotence=true.  │
        │                                                                     │
        │                                                                     │
        │  ═══ What We'll Demonstrate ═══                                     │
        │                                                                     │
        │  Part A: Send same body twice → content-based dedup catches it      │
        │  Part B: Send different bodies with same dedup ID → caught!         │
        │  Part C: Send same body with different dedup ID → both accepted     │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoQueue() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO Queues, MessageGroupId & Deduplication              │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  FIFO = First-In-First-Out. Guarantees:                             │
        │  ✓ Exactly-once processing (within 5-min dedup window)              │
        │  ✓ Strict ordering within a MessageGroupId                          │
        │                                                                     │
        │  ═══ MessageGroupId (the KEY concept) ═══                           │
        │                                                                     │
        │  Think of it like Kafka's partition key:                             │
        │                                                                     │
        │    Kafka:  hash(key) → partition → ordered within partition          │
        │    SQS:    MessageGroupId → message group → ordered within group    │
        │                                                                     │
        │  customer-A: [place-order] → [pay] → [ship]     ← ORDERED          │
        │  customer-B: [place-order] → [pay]               ← ORDERED          │
        │  But customer-A and customer-B can be processed IN PARALLEL!        │
        │                                                                     │
        │  ═══ Deduplication (exactly-once) ═══                               │
        │                                                                     │
        │  Two modes:                                                         │
        │  1. Content-based: SQS hashes body → rejects duplicates (5 min)    │
        │  2. Explicit: You provide MessageDeduplicationId                    │
        │     (we use content-based in this demo)                             │
        │                                                                     │
        │  ═══ FIFO vs Standard ═══                                           │
        │                                                                     │
        │  Standard:  ~unlimited throughput, at-least-once, best-effort order │
        │  FIFO:      3,000 msg/s (batching) or 300 msg/s (individual),      │
        │             exactly-once, strict order per MessageGroupId           │
        │             High-throughput mode: 30,000 msg/s per group            │
        │                                                                     │
        │  ═══ SequenceNumber ═══                                             │
        │                                                                     │
        │  SQS assigns a monotonically increasing SequenceNumber per group.  │
        │  You'll see this in the receive output — proof of ordering!        │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void receiveAndDelete() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: ReceiveMessage, ReceiptHandle & Long Polling              │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  ═══ Long Polling vs Short Polling ═══                              │
        │                                                                     │
        │  Short polling (WaitTimeSeconds=0):                                 │
        │    → SQS queries a SUBSET of servers, returns immediately           │
        │    → May return empty even if messages exist (eventual consistency) │
        │    → Wastes money: empty responses still cost                       │
        │                                                                     │
        │  Long polling (WaitTimeSeconds=1-20):                               │
        │    → SQS queries ALL servers, waits up to N seconds for a message   │
        │    → Reduces empty responses by 90%+                                │
        │    → Reduces cost, increases message discovery rate                  │
        │    → ALWAYS USE LONG POLLING (we use WaitTimeSeconds=5 here)        │
        │                                                                     │
        │  ═══ ReceiptHandle (critical concept) ═══                           │
        │                                                                     │
        │  When you receive a message, SQS returns a ReceiptHandle:          │
        │  • It's a TEMPORARY token, NOT the same as MessageId               │
        │  • Changes every time the same message is received                  │
        │  • Required to: DeleteMessage or ChangeMessageVisibility            │
        │  • Proof-of-receipt: only the consumer who received it can delete   │
        │  • Anti-pattern: caching ReceiptHandles across poll cycles          │
        │                                                                     │
        │  ═══ ApproximateReceiveCount ═══                                    │
        │                                                                     │
        │  How many times this message has been received. Key for DLQ:       │
        │  when this exceeds maxReceiveCount → message moves to DLQ.         │
        │                                                                     │
        │  ═══ MaxNumberOfMessages ═══                                        │
        │                                                                     │
        │  You can request 1-10 messages per ReceiveMessage call.             │
        │  SQS may return FEWER than requested (it's distributed).           │
        │  To drain a queue fast: use multiple threads polling in parallel.  │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void visibilityTimeout() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Visibility Timeout                                        │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  When a consumer receives a message, it becomes INVISIBLE to        │
        │  other consumers for the duration of the visibility timeout:        │
        │                                                                     │
        │  t=0           t=30s (VisibilityTimeout)       t=60s                │
        │  ──┬────────────────┬────────────────────────────┬──                │
        │    │   IN-FLIGHT    │        AVAILABLE AGAIN      │                  │
        │    │  (invisible)   │   (another consumer can     │                  │
        │    Received         │    receive it)               │                  │
        │                     │                              │                  │
        │               If NOT deleted by here,              │                  │
        │               message reappears!                   │                  │
        │                                                                     │
        │  Default: 30 seconds. Range: 0 seconds to 12 hours.               │
        │                                                                     │
        │  ═══ Why it matters ═══                                             │
        │                                                                     │
        │  • Too SHORT: message reappears while still being processed         │
        │    → duplicate processing!                                          │
        │  • Too LONG: if consumer crashes, message stuck invisible           │
        │    → long delay before retry                                        │
        │                                                                     │
        │  ═══ Peek Pattern (what we're doing here) ═══                       │
        │                                                                     │
        │  We receive with a SHORT visibility timeout (5s) and DON'T delete.  │
        │  Result: message goes in-flight briefly, then comes back.           │
        │  This lets us "peek" at messages without consuming them.            │
        │                                                                     │
        │  ═══ ChangeMessageVisibility ═══                                    │
        │                                                                     │
        │  If processing takes longer than expected, extend the timeout:      │
        │  ChangeMessageVisibility(receiptHandle, newTimeout)                 │
        │  Best practice: set a heartbeat that extends visibility every N/2s  │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void deadLetterQueue() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Dead Letter Queue (DLQ) & Redrive Policy                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  ═══ The Problem: Poison Pill Messages ═══                          │
        │                                                                     │
        │  Some messages can never be processed successfully:                 │
        │  • Malformed JSON                                                   │
        │  • References a deleted resource                                    │
        │  • Triggers a bug in consumer code                                  │
        │                                                                     │
        │  Without DLQ: message bounces forever (receive → timeout → repeat)  │
        │  With DLQ:    after N failed attempts, SQS moves it aside           │
        │                                                                     │
        │  ═══ How It Works ═══                                               │
        │                                                                     │
        │   Source Queue                              DLQ                     │
        │  ┌───────────┐   receive #1 → fail         ┌──────────┐            │
        │  │ poison    │   receive #2 → fail         │          │            │
        │  │ message   │   receive #3 → fail ──────► │ poison   │            │
        │  └───────────┘   (maxReceiveCount=3)        │ message  │            │
        │                                             └──────────┘            │
        │                                                                     │
        │  ═══ Redrive Policy (set on SOURCE queue) ═══                       │
        │                                                                     │
        │  {"deadLetterTargetArn": "arn:...:sqs-demo-dlq",                    │
        │   "maxReceiveCount": 3}                                             │
        │                                                                     │
        │  • maxReceiveCount: after this many receives → move to DLQ          │
        │  • DLQ must be same type (Standard→Standard, FIFO→FIFO)            │
        │  • DLQ should have LONGER retention than source queue               │
        │                                                                     │
        │  ═══ DLQ Redrive (Kafka comparison) ═══                             │
        │                                                                     │
        │  Kafka: No built-in DLQ. You implement it yourself                  │
        │         (publish to a "dead-letter" topic on failure)               │
        │  SQS:   Built-in. Just configure the redrive policy.               │
        │  SQS:   Also supports "redrive to source" — move DLQ msgs          │
        │         BACK to the original queue for reprocessing.                │
        │                                                                     │
        │  ═══ What we'll simulate ═══                                        │
        │                                                                     │
        │  1. Send a "poison pill" message                                    │
        │  2. Receive it 3 times without deleting (simulating failure)        │
        │  3. After 3rd receive, SQS moves it to the DLQ                      │
        │  4. We'll read it from the DLQ to confirm                           │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoReceive() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO Receive Ordering & Message Group Locking             │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  ═══ Per-Group Ordering Guarantee ═══                               │
        │                                                                     │
        │  FIFO guarantees order WITHIN a MessageGroupId:                     │
        │                                                                     │
        │  Sent:     [A:order] [B:order] [A:pay] [B:pay] [A:ship]            │
        │  Received: customer-A sees: order → pay → ship (in order)           │
        │            customer-B sees: order → pay         (in order)          │
        │  But A and B messages can INTERLEAVE with each other.              │
        │                                                                     │
        │  This is exactly like Kafka partitions:                             │
        │    Kafka partition key  =  SQS MessageGroupId                       │
        │    Ordering per partition = Ordering per message group              │
        │    No global ordering    = No global ordering                       │
        │                                                                     │
        │  ═══ Message Group Locking ═══                                      │
        │                                                                     │
        │  When a message from group "customer-A" is in-flight:               │
        │  → SQS will NOT deliver the NEXT message in that group              │
        │  → Other groups (customer-B) can still be delivered                 │
        │  → This prevents out-of-order processing within a group             │
        │                                                                     │
        │  This is why FIFO throughput is lower than Standard:                │
        │  each group is effectively single-threaded.                         │
        │                                                                     │
        │  ═══ SequenceNumber ═══                                             │
        │                                                                     │
        │  SQS assigns a large increasing number to each FIFO message.       │
        │  This is proof of ordering. Watch it in the output below.           │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void queueStats() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Queue Metrics (the 3 message counters)                    │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Every SQS queue has three key counters:                            │
        │                                                                     │
        │  ┌─────────────────────────────────────────────────────────────┐    │
        │  │                    SQS Queue                                │    │
        │  │                                                             │    │
        │  │  Available ──────► In-Flight ──────► Deleted                │    │
        │  │  (waiting)        (being processed)  (done)                 │    │
        │  │                                                             │    │
        │  │  + Delayed ─(after delay)──► Available                      │    │
        │  └─────────────────────────────────────────────────────────────┘    │
        │                                                                     │
        │  1. ApproximateNumberOfMessages         = Available (ready to recv) │
        │  2. ApproximateNumberOfMessagesNotVisible = In-Flight (being proc.) │
        │  3. ApproximateNumberOfMessagesDelayed   = Delayed (waiting)        │
        │                                                                     │
        │  "Approximate" because SQS is distributed across many servers.     │
        │  Exact counts would require expensive cross-server consensus.       │
        │                                                                     │
        │  Total messages ≈ Available + InFlight + Delayed                    │
        │                                                                     │
        │  Monitoring tip: If InFlight keeps growing but Available stays 0,   │
        │  your consumers are receiving but NOT deleting → likely a bug.      │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void competingConsumers() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Competing Consumers (Horizontal Scaling)                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  ═══ The Pattern ═══                                                │
        │                                                                     │
        │  Multiple consumers poll the SAME queue concurrently.               │
        │  SQS distributes messages so each goes to exactly ONE consumer.     │
        │                                                                     │
        │                      ┌──────────┐                                   │
        │                 ┌───►│Consumer-1│  receives msg A, C                │
        │  ┌──────────┐   │    └──────────┘                                   │
        │  │   SQS    │───┤    ┌──────────┐                                   │
        │  │  Queue   │───┼───►│Consumer-2│  receives msg B, E                │
        │  │[A,B,C,D,E│───┤    └──────────┘                                   │
        │  └──────────┘   │    ┌──────────┐                                   │
        │                 └───►│Consumer-3│  receives msg D                   │
        │                      └──────────┘                                   │
        │                                                                     │
        │  ═══ SQS vs Kafka: How Parallelism Works ═══                        │
        │                                                                     │
        │  Kafka:                                                             │
        │  • Parallelism = number of partitions (fixed at topic creation)     │
        │  • Each partition → exactly 1 consumer in a group                   │
        │  • More consumers than partitions → some sit idle                   │
        │  • Must plan partition count upfront                                │
        │                                                                     │
        │  SQS Standard:                                                      │
        │  • Parallelism = unlimited. Just add more consumers.                │
        │  • No partitions to worry about. No rebalancing.                    │
        │  • Each message → one consumer (via visibility timeout)             │
        │  • Scale from 1 to 1000 consumers with zero config changes          │
        │                                                                     │
        │  SQS FIFO:                                                          │
        │  • Parallelism = number of distinct MessageGroupIds                 │
        │  • Each group → only 1 consumer at a time (group locking)           │
        │  • More groups = more parallelism (like Kafka partitions)           │
        │  • Multiple consumers CAN process different groups in parallel      │
        │                                                                     │
        │  ═══ What We'll Simulate ═══                                        │
        │                                                                     │
        │  1. First, send a batch of messages to fill the queue               │
        │  2. Launch N consumer threads polling simultaneously                │
        │  3. Watch how SQS distributes messages across consumers             │
        │  4. Verify: no message processed by more than one consumer          │
        │                                                                     │
        │  ═══ Visibility Timeout Makes This Work ═══                         │
        │                                                                     │
        │  When Consumer-1 receives msg A:                                    │
        │  → msg A becomes invisible (in-flight)                              │
        │  → Consumer-2 and Consumer-3 will NOT see msg A                     │
        │  → Consumer-1 deletes it after processing                           │
        │  → No coordination needed between consumers!                        │
        │                                                                     │
        │  This is simpler than Kafka consumer groups which need:             │
        │  coordinator, heartbeats, rebalancing protocol, and offset mgmt.    │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoCompetingConsumers() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO Queue + Multiple Consumers (Group-Level Locking)     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  FIFO + multiple consumers = ordered processing WITH parallelism    │
        │                                                                     │
        │  MessageGroupId acts like a Kafka partition key:                     │
        │                                                                     │
        │  ┌──────────────────┐     ┌──────────┐                              │
        │  │ FIFO Queue       │     │Consumer-1│ ← processes customer-A msgs  │
        │  │                  │────►│          │    (in order: order→pay→ship) │
        │  │ customer-A: msg1 │     └──────────┘                              │
        │  │ customer-A: msg2 │                                               │
        │  │ customer-B: msg1 │────►┌──────────┐                              │
        │  │ customer-B: msg2 │     │Consumer-2│ ← processes customer-B msgs  │
        │  │ customer-C: msg1 │────►│          │    (in order: order→pay)     │
        │  └──────────────────┘     └──────────┘                              │
        │                           Consumer-2 also gets customer-C           │
        │                                                                     │
        │  ═══ Message Group Locking ═══                                      │
        │                                                                     │
        │  While a message from group "customer-A" is in-flight:              │
        │  → The ENTIRE group is locked                                       │
        │  → No consumer can receive the NEXT message in that group           │
        │  → Other groups (customer-B, customer-C) remain available           │
        │  → This guarantees per-group ordering without consumer coordination │
        │                                                                     │
        │  ═══ Throughput Implication ═══                                      │
        │                                                                     │
        │  • Max parallelism = number of unique MessageGroupIds               │
        │  • 1 group = effectively single-threaded (300 msg/s)                │
        │  • 100 groups + 100 consumers = 100× throughput                     │
        │  • Choose your MessageGroupId wisely (customer_id, order_id, etc.)  │
        │                                                                     │
        │  ═══ What We'll Simulate ═══                                        │
        │                                                                     │
        │  1. Send messages for multiple customer groups                       │
        │  2. Launch N consumer threads on the FIFO queue                     │
        │  3. Watch: same group always processed by same consumer, in order   │
        │  4. Different groups processed by different consumers in parallel   │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void purgeQueue() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: PurgeQueue                                                │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Deletes ALL messages in a queue (available + in-flight + delayed). │
        │                                                                     │
        │  • Takes up to 60 seconds to complete                               │
        │  • Can only purge once every 60 seconds                             │
        │  • Messages sent DURING purge might survive                         │
        │  • Does NOT delete the queue itself                                 │
        │                                                                     │
        │  Compare to Kafka: There's no "purge" in Kafka. You either          │
        │  wait for retention to expire, or delete and recreate the topic.    │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }
}
