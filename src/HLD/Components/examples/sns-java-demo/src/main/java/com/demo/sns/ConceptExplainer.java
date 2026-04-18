package com.demo.sns;

/**
 * Prints educational concept explanations before each SNS operation.
 * Maps directly to sections in 03-SNS.md deep-dive documentation.
 */
public class ConceptExplainer {

    public static void publishSingle() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: SNS Publish & Fan-Out                                     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Publisher ──Publish──► [SNS Topic] ──Push──► Subscriber A          │
        │                                     ──Push──► Subscriber B          │
        │                                     ──Push──► Subscriber C          │
        │                                                                     │
        │  SNS is a PUSH model. Unlike SQS where consumers poll:              │
        │  • Publisher calls Publish API with TopicArn + Message              │
        │  • SNS stores message in ≥3 AZs, returns MessageId (synchronous)   │
        │  • SNS evaluates ALL subscriptions in PARALLEL                      │
        │  • Each matching subscriber gets the message pushed to them         │
        │  • Publisher does NOT know who the subscribers are (decoupled!)     │
        │                                                                     │
        │  KEY DIFFERENCE FROM SQS:                                           │
        │  • SQS: 1 message → 1 consumer (point-to-point)                    │
        │  • SNS: 1 message → N consumers (fan-out broadcast)                │
        │  • SQS: message persists until deleted (up to 14 days)             │
        │  • SNS: fire-and-forget (no persistence after delivery)            │
        │                                                                     │
        │  Max message size: 256 KB (same as SQS)                            │
        │  Cost: $0.50 per 1M publishes. Delivery to SQS/Lambda is FREE.    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void messageAttributes() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Message Attributes & Filter-Based Routing                 │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS messages have TWO parts:                                       │
        │                                                                     │
        │  1. MESSAGE BODY: The payload (JSON string, up to 256 KB)          │
        │  2. MESSAGE ATTRIBUTES: Key-value metadata (up to 10)              │
        │     Types: String, Number, Binary                                   │
        │                                                                     │
        │  WHY attributes matter? → They power FILTER POLICIES!               │
        │                                                                     │
        │  Without filtering:                                                 │
        │    Topic → ALL subscribers get ALL messages (wasteful!)             │
        │                                                                     │
        │  With filtering:                                                    │
        │    Topic → Subscriber A (filter: event_type=order_placed)          │
        │         → Subscriber B (filter: event_type=payment_due)            │
        │         → Subscriber C (no filter — gets everything)               │
        │                                                                     │
        │  This demo publishes event_type="order_placed":                     │
        │    ✓ inventory-queue → receives it (filter matches)                │
        │    ✗ billing-queue   → skips it (filter wants "payment_due")       │
        │    ✓ analytics-queue → receives it (no filter)                     │
        │                                                                     │
        │  Filter operators: exact, anything-but, numeric range,              │
        │                    prefix, suffix, exists                           │
        │  Cross-key logic: AND between keys, OR within a key                │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void paymentDue() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Filter Routing — Different Event Types                    │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Same topic, different event types, different subscribers react:    │
        │                                                                     │
        │  [order-events topic]                                               │
        │       │                                                             │
        │       ├── event_type=order_placed  → Inventory Queue ✓             │
        │       ├── event_type=payment_due   → Billing Queue ✓               │
        │       ├── (any event)              → Analytics Queue ✓             │
        │       └── amount >= 500            → HighValue Queue ✓             │
        │                                                                     │
        │  This is the E-COMMERCE FAN-OUT pattern from the SNS doc:           │
        │  One order event triggers 4 independent workflows.                  │
        │                                                                     │
        │  WHY this matters in interviews:                                    │
        │  "How would you decouple an order service from downstream           │
        │   services?" → SNS topic with filter policies per subscriber.      │
        │  Adding a new service = adding a subscription. ZERO code change    │
        │  in the publisher.                                                  │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void highValueOrder() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Numeric Filter — Amount-Based Routing                     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Filter policy on highvalue-queue:                                  │
        │    {"amount": [{"numeric": [">=", 500]}]}                          │
        │                                                                     │
        │  This routes ONLY messages where the "amount" attribute >= 500.    │
        │                                                                     │
        │  Numeric operators available:                                       │
        │    =, >, >=, <, <=                                                 │
        │    Range: {"numeric": [">=", 100, "<=", 500]}  (100-500)           │
        │                                                                     │
        │  Real-world use cases:                                              │
        │    • Fraud detection: amount >= $1000 → fraud review queue         │
        │    • Alerting: cpu_percent >= 90 → critical alerts                 │
        │    • Tiered processing: priority >= 5 → fast-lane queue            │
        │                                                                     │
        │  This publish sends a $1500+ order → reaches highvalue-queue!      │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void batchPublish() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: PublishBatch — Cost Optimization                          │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Single Publish:                                                    │
        │    10 messages = 10 API calls = 10x cost                           │
        │                                                                     │
        │  PublishBatch:                                                      │
        │    10 messages = 1 API call = 1x cost (10x savings!)               │
        │                                                                     │
        │  Rules:                                                             │
        │  • Max 10 messages per batch                                        │
        │  • Total batch size ≤ 256 KB                                        │
        │  • Each message can have its own attributes                         │
        │  • Each message independently evaluated against filter policies     │
        │  • Partial failures possible (some succeed, some fail)             │
        │                                                                     │
        │  Compare with SQS SendMessageBatch: same concept, same limits.     │
        │  Compare with Kafka: Kafka batches automatically via               │
        │  linger.ms + batch.size (client-side batching).                    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoOrdering() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO Topics — Strict Ordering & Exactly-Once             │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Standard Topic:          FIFO Topic:                               │
        │  • Best-effort ordering   • Strict per message group               │
        │  • At-least-once          • Exactly-once (with conditions)         │
        │  • 30K msg/s              • 300 msg/s per group                    │
        │  • All subscriber types   • SQS FIFO queues ONLY                  │
        │                                                                     │
        │  MessageGroupId is the KEY concept:                                 │
        │                                                                     │
        │  ┌─────────────────────────────────────────┐                       │
        │  │  Group "customer-A": msg1→msg2→msg3      │ ← Ordered            │
        │  │  Group "customer-B": msg1→msg2→msg3      │ ← Ordered            │
        │  │  A and B: processed in PARALLEL           │ ← Independent       │
        │  └─────────────────────────────────────────┘                       │
        │                                                                     │
        │  More groups = more parallelism = higher throughput.               │
        │  Single group = serialization bottleneck (300 msg/s cap).          │
        │                                                                     │
        │  INTERVIEW KEY: Use entity IDs as MessageGroupId                   │
        │  (user_id, order_id) for natural sharding.                         │
        │                                                                     │
        │  SequenceNumber: 128-bit number assigned by SNS to prove order.    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoDedup() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO Deduplication — Preventing Double Processing        │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Two dedup methods:                                                 │
        │                                                                     │
        │  1. CONTENT-BASED (enabled on our topic):                          │
        │     SNS hashes the message BODY → uses as dedup ID                 │
        │     ⚠ GOTCHA: Attributes are NOT in the hash!                     │
        │     Same body + different attributes = DEDUPLICATED (surprise!)     │
        │                                                                     │
        │  2. EXPLICIT DEDUP ID:                                              │
        │     Publisher sets MessageDeduplicationId manually                  │
        │     More control, recommended for critical flows                   │
        │                                                                     │
        │  Dedup window: 5 MINUTES                                            │
        │  • Same dedup ID within 5 min → accepted but NOT delivered         │
        │  • After 5 min → treated as new message                            │
        │                                                                     │
        │  This demo sends the SAME message TWICE with same dedup ID.        │
        │  Expected: second publish succeeds (no error) but only 1 delivery. │
        │                                                                     │
        │  INTERVIEW TRAP:                                                    │
        │  "Does SNS guarantee exactly-once?"                                 │
        │  → Only FIFO topics, only to SQS FIFO, only without filtering,    │
        │    and consumer must delete before visibility timeout.              │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fanoutBurst() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Fan-Out in Action — One Topic, Many Queues               │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  This sends 6 mixed events to the orders topic.                    │
        │  Watch how filters route each event to different queues:           │
        │                                                                     │
        │  [SNS: order-events]                                                │
        │       │                                                             │
        │       ├──→ inventory-queue  (filter: event_type = order_placed)    │
        │       ├──→ billing-queue    (filter: event_type = payment_due)     │
        │       ├──→ analytics-queue  (no filter → gets ALL 6)              │
        │       └──→ highvalue-queue  (filter: amount >= 500)               │
        │                                                                     │
        │  After publishing, use option 10 (drain) or 12 (stats) to see     │
        │  how many messages landed in each queue.                           │
        │                                                                     │
        │  THIS is the core SNS value prop:                                   │
        │  Publisher sends ONE event. Four services react independently.     │
        │  Adding a 5th service = add a subscription. Zero publisher change. │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void claimCheck() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Claim-Check Pattern (Large Payloads)                      │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Problem: SNS max message size = 256 KB. What if payload is 5 MB?  │
        │                                                                     │
        │  Solution: CLAIM-CHECK PATTERN                                      │
        │                                                                     │
        │  Publisher:                                                         │
        │    1. Upload large payload to S3                                    │
        │    2. Publish S3 reference to SNS (tiny message)                   │
        │       {"s3_bucket":"...", "s3_key":"orders/ORD-123.json"}          │
        │                                                                     │
        │  Consumer:                                                          │
        │    1. Receive SNS message with S3 reference                        │
        │    2. Fetch actual payload from S3                                  │
        │    3. Process                                                       │
        │                                                                     │
        │  ┌──────────┐    tiny ref    ┌──────┐    fetch    ┌──────────┐    │
        │  │ Publisher │───────────────→│ SNS  │───────────→│ Consumer │    │
        │  │          │               │Topic │             │          │    │
        │  │  ┌───┐   │               └──────┘             │  ┌───┐   │    │
        │  │  │S3 │◄──┘ upload big                   fetch │  │S3 │───┘    │
        │  │  └───┘                                        │  └───┘        │
        │  └──────────┘                                    └──────────┘    │
        │                                                                     │
        │  Compare: Kafka default 1 MB (configurable to higher).             │
        │  This demo simulates the pattern (no actual S3 call).              │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void receiveFromQueue() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: SNS → SQS — The Durable Fan-Out Pattern                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS pushes messages to SQS queues. To READ them, you poll SQS.   │
        │                                                                     │
        │  Publisher → [SNS Topic] → [SQS Queue] → Consumer polls SQS       │
        │                                                                     │
        │  Why SNS → SQS instead of SNS → Lambda directly?                   │
        │  • SQS provides DURABILITY (14-day retention)                      │
        │  • SQS provides BACKPRESSURE (consumer polls at own speed)         │
        │  • If consumer crashes → message stays in queue                    │
        │  • If Lambda throttled → SQS retries (not lost like direct SNS)   │
        │                                                                     │
        │  This is THE #1 production pattern for SNS.                        │
        │  ALWAYS use SNS → SQS → Lambda, never SNS → Lambda directly.      │
        │                                                                     │
        │  Raw Message Delivery is ENABLED on our subscriptions:              │
        │  → You receive the MESSAGE BODY directly (not wrapped in SNS JSON) │
        │  → Reduces payload size, avoids double-parsing                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void drainQueues() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Verifying Fan-Out Routing                                 │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  This drains ALL 4 subscriber queues and counts messages.          │
        │  Proves that filter policies correctly routed events:              │
        │                                                                     │
        │  Event published:       inventory  billing  analytics  highvalue   │
        │  ─────────────────────  ─────────  ───────  ─────────  ─────────  │
        │  order_placed $129       ✓          ✗         ✓          ✗        │
        │  payment_due  $299       ✗          ✓         ✓          ✗        │
        │  order_placed $750       ✓          ✗         ✓          ✓        │
        │  order_placed $2500      ✓          ✗         ✓          ✓        │
        │                                                                     │
        │  analytics always has the MOST messages (no filter = gets all).    │
        │  highvalue only has messages where amount >= 500.                  │
        │                                                                     │
        │  ⚠ This DELETES messages from queues (consume & delete).          │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void listSubscriptions() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: SNS Subscriptions — The Binding Layer                     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  A subscription connects a TOPIC to an ENDPOINT:                   │
        │                                                                     │
        │  Topic ──subscription──► Endpoint (SQS, Lambda, HTTP, Email, SMS) │
        │                                                                     │
        │  Each subscription has:                                             │
        │  • Protocol: sqs, lambda, http, email, sms                         │
        │  • Endpoint: queue ARN, function ARN, URL, email address           │
        │  • FilterPolicy: which messages this subscriber wants              │
        │  • RawMessageDelivery: skip SNS JSON wrapper (true/false)          │
        │  • RedrivePolicy: DLQ for failed deliveries                        │
        │                                                                     │
        │  Subscriptions require CONFIRMATION (except SQS, Lambda, Firehose  │
        │  which are auto-confirmed when in the same AWS account).           │
        │                                                                     │
        │  Up to 12.5 MILLION subscriptions per topic.                       │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void competingConsumers() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Fan-Out + Competing Consumers (SNS + SQS combo)          │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS handles the BROADCAST (1 → many queues).                      │
        │  SQS handles the WORK DISTRIBUTION (many consumers per queue).     │
        │                                                                     │
        │  Publisher → [SNS Topic]                                            │
        │                  │                                                  │
        │                  ├── [SQS: inventory] ─┬─ Consumer A               │
        │                  │                     └─ Consumer B               │
        │                  │                                                  │
        │                  ├── [SQS: billing]  ─┬─ Consumer C                │
        │                  │                    └─ Consumer D                │
        │                  │                                                  │
        │                  └── [SQS: analytics] ── Consumer E                │
        │                                                                     │
        │  Each SQS queue can have MULTIPLE consumers (competing consumer    │
        │  pattern). Each message processed by exactly ONE consumer.          │
        │                                                                     │
        │  This combines the best of both:                                    │
        │  • SNS: decouple publisher from N services (fan-out)               │
        │  • SQS: scale each service independently (competing consumers)     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void queueStats() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Monitoring SNS via SQS Queue Depths                       │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS itself has no "inbox" to check. Messages are fire-and-forget. │
        │  But when subscribers are SQS queues, you monitor the QUEUES:      │
        │                                                                     │
        │  Key CloudWatch metrics:                                            │
        │  • NumberOfMessagesPublished (SNS-level)                           │
        │  • NumberOfNotificationsDelivered (SNS-level, per subscription)    │
        │  • NumberOfNotificationsFailed (SNS-level — ALERT ON THIS!)        │
        │  • ApproximateNumberOfMessagesVisible (SQS — queue depth)          │
        │  • ApproximateNumberOfMessagesNotVisible (SQS — in-flight)         │
        │                                                                     │
        │  Production rule: alarm on queue depth > threshold.                 │
        │  If analytics-queue is growing → consumers are falling behind.     │
        │                                                                     │
        │  Also alarm on DLQ depth > 0 → something is failing repeatedly.   │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    // ─── NEW CONCEPT EXPLANATIONS ────────────────────

    public static void dlqPublish() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Dead-Letter Queues — The Two-Layer DLQ Pattern           │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS has TWO different DLQ mechanisms (most people only know one): │
        │                                                                     │
        │  LAYER 1: SNS Subscription DLQ                                     │
        │  ┌───────────────────────────────────────────────────────────┐     │
        │  │  SNS → [delivery fails] → SNS subscription DLQ (SQS)     │     │
        │  │                                                           │     │
        │  │  Triggers when: SNS cannot DELIVER to the subscriber     │     │
        │  │  • Lambda invocation fails after 3 retries               │     │
        │  │  • HTTP endpoint returns 5xx after all retry phases      │     │
        │  │  • SQS permission denied (IAM misconfigured)             │     │
        │  │                                                           │     │
        │  │  Configured via: RedrivePolicy on the SNS subscription   │     │
        │  └───────────────────────────────────────────────────────────┘     │
        │                                                                     │
        │  LAYER 2: SQS Redrive DLQ                                          │
        │  ┌───────────────────────────────────────────────────────────┐     │
        │  │  SQS → [consumer fails] → SQS redrive DLQ               │     │
        │  │                                                           │     │
        │  │  Triggers when: Consumer RECEIVES but fails to PROCESS   │     │
        │  │  • Consumer crashes before deleting message              │     │
        │  │  • Business logic throws exception                       │     │
        │  │  • Visibility timeout expires without delete             │     │
        │  │  • After maxReceiveCount attempts → message sent to DLQ │     │
        │  │                                                           │     │
        │  │  Configured via: RedrivePolicy on the SQS source queue  │     │
        │  └───────────────────────────────────────────────────────────┘     │
        │                                                                     │
        │  Production needs BOTH layers. This demo has:                      │
        │  • sns-demo-dlq         → SNS subscription DLQ (Layer 1)          │
        │  • sns-demo-sqs-dlq     → SQS redrive DLQ (Layer 2)              │
        │  • sns-demo-dlq-source  → source queue, maxReceiveCount=2         │
        │                                                                     │
        │  After publishing, use option 19 to simulate consumer failures.    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void dlqSimulate() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Simulating Consumer Failures → SQS Redrive DLQ          │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Normal flow:                                                       │
        │    Consumer receives message → processes → deletes → done          │
        │                                                                     │
        │  Failure flow:                                                      │
        │    Consumer receives → fails → does NOT delete                     │
        │    After visibilityTimeout → message becomes visible again         │
        │    Consumer receives AGAIN → fails AGAIN → does NOT delete         │
        │    After maxReceiveCount exceeded → SQS moves to DLQ              │
        │                                                                     │
        │  Our setup:                                                         │
        │    dlq-source-queue: visibilityTimeout=5s, maxReceiveCount=2       │
        │    After 2 failed receives → message goes to sqs-dlq              │
        │                                                                     │
        │  This demo:                                                         │
        │    1. Receives messages from dlq-source-queue                      │
        │    2. Does NOT delete them (simulates crash/failure)               │
        │    3. Waits for visibility timeout (5s)                            │
        │    4. Receives again (ApproximateReceiveCount goes up)             │
        │    5. After count > 2, SQS moves message to sqs-dlq              │
        │                                                                     │
        │  Watch: ApproximateReceiveCount in the message attributes.         │
        │  When it exceeds maxReceiveCount → next poll finds nothing.       │
        │  The message is now in the SQS DLQ.                               │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void dlqInspect() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Inspecting DLQ Messages — Forensics                      │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  When a message lands in a DLQ, you need to investigate:           │
        │                                                                     │
        │  Key metadata to check:                                            │
        │  • ApproximateReceiveCount: how many times consumer tried          │
        │  • SentTimestamp: when was the original message sent               │
        │  • ApproximateFirstReceiveTimestamp: first delivery attempt        │
        │  • Message body: the actual payload that failed                    │
        │  • Message attributes: event_type, amount, etc.                   │
        │                                                                     │
        │  Common investigation steps:                                        │
        │  1. Read DLQ message → understand what failed                     │
        │  2. Check consumer logs for that message ID                        │
        │  3. Fix the bug (schema change? null field? timeout?)             │
        │  4. Replay DLQ messages back to source queue (option 21)          │
        │                                                                     │
        │  Production monitoring:                                             │
        │  • CloudWatch alarm: ApproximateNumberOfMessagesVisible > 0       │
        │  • Payment DLQ → PagerDuty P1 (1 minute)                         │
        │  • Analytics DLQ → Slack P4 (replay on Monday)                    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void dlqReplay() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: DLQ Replay — Reprocessing Failed Messages                │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  After fixing the consumer bug, you need to reprocess DLQ msgs:   │
        │                                                                     │
        │  Method 1: Manual replay (what this demo does)                     │
        │    Read from DLQ → send to source queue → delete from DLQ         │
        │    Simple, but loses original message attributes/metadata          │
        │                                                                     │
        │  Method 2: SQS DLQ Redrive (AWS built-in, since 2021)             │
        │    AWS Console → SQS → DLQ → "Start DLQ redrive"                 │
        │    Moves messages back to source queue automatically              │
        │    Preserves metadata, handles batching                           │
        │    Also available via StartMessageMoveTask API                    │
        │                                                                     │
        │  Method 3: Re-publish to SNS topic (replay with fan-out)          │
        │    Read from DLQ → re-publish to original SNS topic               │
        │    All subscribers get the message again (not just one queue)     │
        │    Use when: the event should trigger ALL downstream services     │
        │                                                                     │
        │  CRITICAL: Consumer MUST be idempotent!                            │
        │  Replayed messages may have been partially processed before.      │
        │  Use idempotency keys (event_id + DynamoDB conditional write)     │
        │  to prevent double-processing.                                    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void payloadFilter() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Payload-Based Filtering (FilterPolicyScope=MessageBody)  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  SNS has TWO filter scopes:                                        │
        │                                                                     │
        │  1. ATTRIBUTE-BASED (default — FilterPolicyScope=MessageAttributes)│
        │     Matches against MessageAttributes (key-value metadata)        │
        │     Filter: {"event_type": ["order_placed"]}                      │
        │     Checks: msg.messageAttributes.event_type == "order_placed"    │
        │                                                                     │
        │  2. PAYLOAD-BASED (FilterPolicyScope=MessageBody)                  │
        │     Matches against fields INSIDE the JSON message body           │
        │     Filter: {"source": ["mobile-app"]}                            │
        │     Checks: JSON.parse(msg.body).source == "mobile-app"           │
        │                                                                     │
        │  WHEN TO USE PAYLOAD-BASED:                                        │
        │  • Publisher doesn't set message attributes                        │
        │  • You need to filter on nested JSON fields                       │
        │  • Legacy systems that embed metadata in the body                 │
        │                                                                     │
        │  GOTCHAS:                                                          │
        │  • Message body MUST be valid JSON                                │
        │  • Slightly higher latency (SNS must parse the body)              │
        │  • Same operators: exact, prefix, numeric, exists, anything-but   │
        │  • Can't mix scopes on the same subscription                     │
        │                                                                     │
        │  This demo: payload-queue has FilterPolicyScope=MessageBody        │
        │  with filter {"source": ["mobile-app"]}                           │
        │  Only messages where body JSON has source="mobile-app" arrive.    │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void rawVsWrapped() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Raw Message Delivery vs SNS JSON Envelope                │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  When SNS delivers to SQS, the message body can be:               │
        │                                                                     │
        │  RAW (RawMessageDelivery = true):                                  │
        │    SQS body = your original message                               │
        │    {"orderId":"ORD-123","amount":250}                             │
        │                                                                     │
        │  WRAPPED (RawMessageDelivery = false — the DEFAULT):               │
        │    SQS body = SNS JSON envelope wrapping your message             │
        │    {                                                               │
        │      "Type": "Notification",                                       │
        │      "MessageId": "abc-123",                                       │
        │      "TopicArn": "arn:aws:sns:...:orders",                        │
        │      "Message": "{\"orderId\":\"ORD-123\",\"amount\":250}",       │
        │      "Timestamp": "2026-04-15T...",                               │
        │      "SignatureVersion": "1",                                      │
        │      "Signature": "base64...",                                     │
        │      "SigningCertURL": "https://...",                              │
        │      "UnsubscribeURL": "https://...",                             │
        │      "MessageAttributes": { ... }                                 │
        │    }                                                               │
        │                                                                     │
        │  Note: your original JSON is ESCAPED inside "Message" field!      │
        │  Consumer must: parse envelope → extract Message → parse again.   │
        │                                                                     │
        │  RECOMMENDATION: Always set RawMessageDelivery=true for SQS/Lambda│
        │  Only use wrapped for HTTP endpoints that need signature verify.  │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void fifoGroupRouting() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: FIFO MessageGroupId — The Lane Divider                   │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  When FIFO SQS triggers multiple Lambda/consumer instances:        │
        │                                                                     │
        │  ┌──────────────────────────────────────────────────────────┐     │
        │  │  SQS FIFO queue routes by MessageGroupId:                │     │
        │  │                                                          │     │
        │  │  Consumer 1:                Consumer 2:                  │     │
        │  │  ┌──────────────┐           ┌──────────────┐            │     │
        │  │  │ ride-A: 1→2→3│           │ ride-B: 1→2→3│            │     │
        │  │  │ ride-C: 1→2→3│           │ ride-D: 1→2→3│            │     │
        │  │  └──────────────┘           └──────────────┘            │     │
        │  │                                                          │     │
        │  │  Same group  → SAME consumer, IN ORDER                  │     │
        │  │  Diff groups → DIFFERENT consumers, IN PARALLEL          │     │
        │  └──────────────────────────────────────────────────────────┘     │
        │                                                                     │
        │  WRONG: GroupId = "payments" (static)                              │
        │    → ALL messages go to ONE consumer → zero parallelism           │
        │    → Throughput capped at 300 msg/s                               │
        │                                                                     │
        │  RIGHT: GroupId = ride_id (per-entity)                             │
        │    → Each ride's events are ordered                               │
        │    → Different rides processed in parallel                        │
        │    → Throughput: 300 msg/s × number_of_groups                    │
        │                                                                     │
        │  This demo publishes 4 rides × 3 events each, then runs          │
        │  2 competing consumers to show the routing in action.             │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void snsDlqTrigger() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Triggering the SNS Subscription DLQ (Layer 1)            │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  The SNS subscription DLQ catches DELIVERY failures — when SNS    │
        │  itself cannot push the message to the subscriber endpoint.        │
        │                                                                     │
        │  WHEN DOES THIS HAPPEN IN PRODUCTION?                              │
        │  • SQS queue policy denies SNS (IAM misconfiguration)             │
        │  • SQS queue has been deleted but subscription still exists       │
        │  • Lambda function doesn't exist or has wrong permissions         │
        │  • HTTP endpoint is down and all retries exhausted (~23 days)     │
        │  • KMS key for encrypted SQS queue is inaccessible               │
        │                                                                     │
        │  HOW THIS DEMO WORKS:                                              │
        │  1. Set a DENY policy on dlq-source-queue (blocks SNS)            │
        │  2. Publish messages that route to that queue                     │
        │  3. SNS tries sqs:SendMessage → gets AccessDenied                 │
        │  4. SNS retries 3 times (immediate, no backoff for SQS)           │
        │  5. All retries exhausted → message routed to SNS DLQ            │
        │  6. Restore the original ALLOW policy                             │
        │                                                                     │
        │  This is DIFFERENT from option 18 (SQS DLQ):                      │
        │                                                                     │
        │  Option 18 (Layer 2): SNS delivers OK → SQS has the message     │
        │    → consumer receives but FAILS to process → SQS redrive DLQ   │
        │                                                                     │
        │  Option 26 (Layer 1): SNS CANNOT deliver → message never reaches │
        │    SQS → SNS routes to subscription DLQ                          │
        │                                                                     │
        │  Both DLQs are SQS queues. The difference is WHO sends to them:  │
        │  • Layer 1: SNS sends (delivery failure)                          │
        │  • Layer 2: SQS sends (consumer processing failure)              │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }
}
