# AWS SNS Java Playground

Interactive CLI that lets you **publish, fan-out, filter, FIFO-order, deduplicate, claim-check, DLQ simulate/inspect/replay, payload-filter, raw-vs-wrapped compare, and MessageGroupId routing** against **real AWS SNS topics + SQS subscriber queues** — then verify everything in the AWS Console. Each operation prints a concept explanation before executing.

## Prerequisites

| What | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| AWS CLI | v1 or v2 | `aws --version` |
| AWS Credentials configured | — | `aws sts get-caller-identity` |

## AWS Setup (one-time, ~3 minutes)

### 1. Create an IAM User

1. Go to **AWS Console → IAM → Users → Create user**
2. Name: `sns-demo-user`
3. Attach **TWO** policies:
   - **AmazonSNSFullAccess**
   - **AmazonSQSFullAccess** (SNS subscribers are SQS queues)
4. Go to **Security credentials → Create access key → CLI**
5. Copy the **Access Key ID** and **Secret Access Key**

### 2. Configure AWS CLI

```bash
aws configure
```

Enter when prompted:
```
AWS Access Key ID:      <your-access-key>
AWS Secret Access Key:  <your-secret-key>
Default region name:    ap-south-1       # or your preferred region
Default output format:  json
```

If you have a stale session token from a previous setup, remove it:
```bash
aws configure get aws_session_token
# If it prints anything, remove the aws_session_token line from ~/.aws/credentials
```

### 3. Verify access

```bash
aws sts get-caller-identity
# Should print your account ID + ARN
```

### 4. IAM permissions needed

Your IAM user/role needs **AmazonSNSFullAccess + AmazonSQSFullAccess** (or these specific actions):
- SNS: `CreateTopic`, `ListTopics`, `Publish`, `PublishBatch`, `Subscribe`, `ListSubscriptionsByTopic`, `GetSubscriptionAttributes`, `SetSubscriptionAttributes`
- SQS: `CreateQueue`, `GetQueueUrl`, `GetQueueAttributes`, `SetQueueAttributes`, `SendMessage`, `ReceiveMessage`, `DeleteMessage`, `PurgeQueue`

The app will **auto-create** all of these on startup:

**SNS Topics:**
- `sns-demo-orders` — Standard topic (fan-out + filtering demos)
- `sns-demo-fifo.fifo` — FIFO topic (ordering + exactly-once demos)

**SQS Subscriber Queues (attribute-based filters):**
- `sns-demo-inventory-queue` — Filter: `event_type = order_placed`
- `sns-demo-billing-queue` — Filter: `event_type = payment_due`
- `sns-demo-analytics-queue` — No filter (receives ALL messages)
- `sns-demo-highvalue-queue` — Filter: `amount >= 500`
- `sns-demo-fifo-queue.fifo` — FIFO queue subscribed to FIFO topic

**Advanced queues:**
- `sns-demo-payload-queue` — Payload-based filter: body `source = mobile-app`
- `sns-demo-wrapped-queue` — RawMessageDelivery=false (SNS JSON envelope)
- `sns-demo-dlq-source-queue` — Source queue with SQS redrive → sqs-dlq after 2 failures

**Dead-Letter Queues (two-layer pattern):**
- `sns-demo-dlq` — SNS subscription DLQ (catches delivery failures)
- `sns-demo-sqs-dlq` — SQS redrive DLQ (catches consumer processing failures)

## How to Run

```bash
# From the repo root:
cd src/HLD/Components/examples/sns-java-demo

# Compile and run (first time downloads dependencies, ~30s):
mvn compile exec:java

# Subsequent runs are fast (~1s compile):
mvn compile exec:java
```

The interactive menu will appear. Type a number and press Enter.

### Recommended walkthrough order

```
Step 1:  Press 1   → Publish single message        (learn: publish lifecycle, MessageId)
Step 2:  Press 12  → Show queue stats               (learn: where did the message go?)
Step 3:  Press 2   → Publish order_placed event      (learn: attributes, filter routing)
Step 4:  Press 3   → Publish payment_due event       (learn: different filter match)
Step 5:  Press 4   → Publish high-value order        (learn: numeric filter >= 500)
Step 6:  Press 12  → Show queue stats               (learn: verify filter routing counts)
Step 7:  Press 11  → Receive from specific queue    (learn: SNS→SQS durable pattern)
Step 8:  Press 8   → Fan-out burst (6 events)        (learn: mixed routing in action)
Step 9:  Press 10  → Drain ALL queues                (learn: verify fan-out counts)
Step 10: Press 5   → Publish batch (10 messages)     (learn: PublishBatch, cost savings)
Step 11: Press 6   → Publish FIFO ordered            (learn: MessageGroupId, ordering)
Step 12: Press 13  → Receive FIFO                    (learn: SequenceNumber, proof of order)
Step 13: Press 7   → FIFO dedup demo                 (learn: deduplication window)
Step 14: Press 9   → Claim-check pattern             (learn: large payload handling)
Step 15: Press 14  → List subscriptions              (learn: filter policies, sub attributes)
Step 16: Press 15  → Competing consumers              (learn: SNS fan-out + SQS parallelism)
Step 17: Press 16  → Purge all queues                (clean slate)

DLQ walkthrough (do these in order):
Step 18: Press 17  → Publish DLQ test messages       (learn: two-layer DLQ pattern)
Step 19: Press 18  → Simulate consumer failures       (learn: SQS redrive, maxReceiveCount)
Step 20: Press 19  → Inspect BOTH DLQs                (learn: SNS DLQ vs SQS DLQ forensics)
Step 21: Press 20  → Replay DLQ back to source        (learn: DLQ replay strategies)
Step 22: Press 26  → Trigger SNS subscription DLQ     (learn: Layer 1 delivery failure)

Advanced walkthrough:
Step 23: Press 21  → Payload-based filtering           (learn: FilterPolicyScope=MessageBody)
Step 24: Press 22  → Publish for raw vs wrapped        (learn: RawMessageDelivery setting)
Step 25: Press 23  → Show raw vs wrapped side-by-side  (learn: SNS JSON envelope structure)
Step 26: Press 24  → FIFO MessageGroupId routing       (learn: lane divider, parallel groups)
Step 27: Press 25  → Extended queue stats              (learn: monitor all queues + DLQs)
```

## What you can do

```
┌───────────────────────────────────────────────────────────────┐
│  PUBLISH (to SNS topics)                                      │
│    1.  Publish a single message (Standard)                    │
│    2.  Publish order_placed event (→ inventory queue)         │
│    3.  Publish payment_due event (→ billing queue)            │
│    4.  Publish high-value order $1500+ (→ highvalue queue)    │
│    5.  Publish batch (up to 10 messages)                      │
│    6.  Publish FIFO ordered events (2 customers × 5 steps)   │
│    7.  FIFO deduplication demo (same message twice)           │
│    8.  Fan-out burst (6 mixed events, watch routing)          │
│    9.  Claim-check pattern (large payload simulation)         │
│                                                               │
│  CONSUME / VERIFY (from SQS subscriber queues)               │
│   10.  Drain ALL queues (show fan-out counts)                 │
│   11.  Receive from a specific queue                          │
│   12.  Show queue stats (all queues)                          │
│   13.  Receive FIFO messages (show ordering proof)            │
│                                                               │
│  ADMIN                                                        │
│   14.  List all subscriptions (show filter policies)          │
│   15.  Competing consumers demo (multi-threaded)              │
│   16.  Purge all queues                                       │
│                                                               │
│  DLQ (Dead-Letter Queue demos)                                │
│   17.  Publish DLQ test messages (→ dlq-source-queue)        │
│   18.  Simulate consumer failures (→ triggers SQS DLQ)       │
│   19.  Inspect BOTH DLQs (SNS sub DLQ + SQS redrive DLQ)    │
│   20.  Replay DLQ messages back to source queue              │
│                                                               │
│  ADVANCED                                                     │
│   21.  Payload-based filtering demo (body JSON matching)     │
│   22.  Publish for raw vs wrapped comparison                  │
│   23.  Show raw vs wrapped message side-by-side              │
│   24.  FIFO MessageGroupId routing (lane divider demo)       │
│   25.  Extended queue stats (all queues + both DLQs)         │
│   26.  Trigger SNS subscription DLQ (block permission demo)  │
│    0.  Exit                                                   │
└───────────────────────────────────────────────────────────────┘
```

## What to observe in AWS Console

After running each operation, open **AWS Console → SNS** and **AWS Console → SQS** (in your region):

| Operation | What to look for in Console |
|-----------|---------------------------|
| Publish single/batch | SQS: analytics-queue "Messages Available" goes up |
| Publish order_placed | SQS: inventory-queue count up, billing-queue unchanged |
| Publish payment_due | SQS: billing-queue count up, inventory-queue unchanged |
| Publish high-value | SQS: highvalue-queue count up (amount >= 500) |
| Fan-out burst | SQS: each queue has DIFFERENT counts (filter routing!) |
| FIFO publish | SQS: fifo-queue shows messages with MessageGroupId |
| FIFO dedup | SQS: only 1 message despite 2 publishes (deduplication!) |
| Receive from queue | SQS: "Messages Available" count drops |
| Drain all queues | SQS: all queues go to 0 |
| List subscriptions | SNS: Topic → Subscriptions tab shows filter policies |
| Competing consumers | SQS: watch analytics-queue drain rapidly |
| Queue stats | Compare with Console's SQS "Monitoring" tab |
| **DLQ publish** | SQS: dlq-source-queue count goes up |
| **DLQ simulate** | SQS: dlq-source drops to 0, sqs-dlq goes up |
| **DLQ inspect** | See message metadata (ReceiveCount, timestamps) |
| **DLQ replay** | SQS: sqs-dlq drops, dlq-source-queue goes back up |
| **Payload filter** | SQS: payload-queue gets ONLY mobile-app messages |
| **Raw vs wrapped** | SQS: compare message body in inventory vs wrapped queue |
| **FIFO group routing** | SQS: watch 2 consumers process different groups |
| **SNS DLQ trigger** | SQS: sns-demo-dlq gets messages (delivery failure) |

## Concepts demonstrated

Each menu option prints a concept explanation before executing. Concepts covered:

| # | Concept | SNS Doc Section |
|---|---------|----------------|
| 1 | **Publish & Fan-Out** — one message, N subscribers | §1, §4 |
| 2 | **Message Attributes** — metadata for filter routing | §5 |
| 3 | **Filter Policies** — attribute-based routing (exact match) | §5 |
| 4 | **Numeric Filters** — amount >= 500 routing | §5 |
| 5 | **PublishBatch** — 10 messages in 1 API call (cost savings) | §15 |
| 6 | **FIFO Ordering** — MessageGroupId, SequenceNumber | §6 |
| 7 | **FIFO Deduplication** — 5-minute dedup window | §6 |
| 8 | **Fan-Out Burst** — mixed events routed to different queues | §4 |
| 9 | **Claim-Check Pattern** — S3 reference for large payloads | §14 |
| 10 | **Drain & Verify** — prove fan-out routing worked | §4 |
| 11 | **SNS → SQS Pattern** — the durable fan-out combo | §9 |
| 12 | **Queue Monitoring** — CloudWatch metrics via SQS stats | §15 |
| 13 | **FIFO Receive** — prove ordering via SequenceNumber | §6 |
| 14 | **Subscription Listing** — filter policies, raw delivery, DLQ | §2 |
| 15 | **Competing Consumers** — SNS fan-out + SQS parallel consume | §9 |
| 17 | **Two-Layer DLQ Pattern** — SNS sub DLQ vs SQS redrive DLQ | §7 |
| 18 | **DLQ Simulation** — maxReceiveCount, visibility timeout, redrive | §7 |
| 19 | **DLQ Inspection** — forensic metadata (ReceiveCount, timestamps) | §7 |
| 20 | **DLQ Replay** — reprocess failed messages, idempotency | §7 |
| 21 | **Payload-Based Filtering** — FilterPolicyScope=MessageBody | §5 |
| 22-23 | **Raw vs Wrapped Delivery** — RawMessageDelivery, SNS envelope | §2, §9 |
| 24 | **FIFO GroupId Routing** — lane divider, parallel consumers | §6 |
| 26 | **SNS Subscription DLQ Trigger** — block permission, delivery fails → DLQ | §7 |

## Architecture created by the app

### Full wiring diagram — with exact AWS Console queue names

```
                         ┌─────────────────────────────┐
                         │     YOUR CODE (Publisher)     │
                         └──────────────┬──────────────┘
                                        │ Publish API
                                        ▼
                ┌──────────────────────────────────────────────────────────┐
                │  SNS Topic: sns-demo-orders (Standard)                   │
                └──┬───────┬────────┬───────────┬────────┬────────┬──────┘
                   │       │        │           │        │        │
          Filter:  │       │        │           │        │        │
          order_   │  payment_  (none)      amount   body:    dlq_
          placed   │  due       (all)       >= 500  source=  test
                   │       │        │           │   mobile    │
                   ▼       ▼        ▼           ▼        ▼    ▼
 ┌──────────────┐┌──────────────┐┌──────────────┐┌──────────────┐┌──────────────┐┌──────────────────┐
 │ sns-demo-    ││ sns-demo-    ││ sns-demo-    ││ sns-demo-    ││ sns-demo-    ││ sns-demo-        │
 │ inventory-   ││ billing-     ││ analytics-   ││ highvalue-   ││ payload-     ││ dlq-source-      │
 │ queue        ││ queue        ││ queue        ││ queue        ││ queue        ││ queue            │
 │              ││              ││              ││              ││              ││                  │
 │ raw=true     ││ raw=true     ││ raw=true     ││ raw=true     ││ raw=true     ││ raw=true         │
 │ DLQ: -       ││ DLQ: -       ││ SNS DLQ: ────┼┼──────────────┼┼──────────────┼┼──┐              │
 │              ││              ││ sns-demo-dlq ││              ││              ││  │ SNS DLQ:      │
 └──────────────┘└──────────────┘└──────────────┘└──────────────┘└──────────────┘│  │ sns-demo-dlq  │
                                                                                 │  │              │
              ┌──────────────┐    (also subscribed to sns-demo-orders, no filter)│  │ SQS redrive: │
              │ sns-demo-    │                                                   │  │ max=2 → to   │
              │ wrapped-     │    RawMessageDelivery = FALSE                     │  │ sns-demo-    │
              │ queue        │    (SNS JSON envelope)                            │  │ sqs-dlq      │
              │              │                                                   │  │     │        │
              │ raw=FALSE    │                                                   └──┼─────┼────────┘
              │ DLQ: -       │                                                     │     │
              └──────────────┘                                                     │     │
                                                                                   │     │
                                                                                   │     │
              ┌──────────────────────────────────────┐                             │     │
              │  SNS FIFO: sns-demo-fifo.fifo          │                             │     │
              │  ContentBasedDeduplication: true        │                             │     │
              └──────────────┬───────────────────────┘                             │     │
                             │                                                     │     │
                             ▼                                                     │     │
                   ┌──────────────────┐                                            │     │
                   │ sns-demo-fifo-   │                                            │     │
                   │ queue.fifo       │                                            │     │
                   │                  │                                            │     │
                   │ raw=true         │                                            │     │
                   │ DLQ: -           │                                            │     │
                   └──────────────────┘                                            │     │
                                                                                   │     │
                                                                                   ▼     ▼
                                  ┌───────────────────┐          ┌───────────────────┐
                                  │ sns-demo-dlq      │          │ sns-demo-sqs-dlq  │
                                  │                   │          │                   │
                                  │ SNS SUBSCRIPTION  │          │ SQS REDRIVE DLQ   │
                                  │ DLQ (LAYER 1)     │          │ (LAYER 2)         │
                                  │                   │          │                   │
                                  │ Msgs arrive when  │          │ Msgs arrive when  │
                                  │ SNS CANNOT deliver│          │ CONSUMER fails to │
                                  │ (IAM deny, queue  │          │ process & delete  │
                                  │  deleted, KMS err) │          │ after 2 receives  │
                                  │                   │          │                   │
                                  │ Demo: option 26   │          │ Demo: opt 17→18   │
                                  │ Inspect: option 19│          │ Inspect: option 19│
                                  │ Replay: option 20 │          │ Replay: option 20 │
                                  └───────────────────┘          └───────────────────┘
```

### AWS Console name reference (search for these in SQS / SNS console)

```
┌──────────────────────────────┬──────────┬──────────────────────────────────┐
│  AWS Console Name            │ Type     │ Role                             │
├──────────────────────────────┼──────────┼──────────────────────────────────┤
│  sns-demo-orders             │ SNS Std  │ Standard topic (options 1-5,8-9) │
│  sns-demo-fifo.fifo          │ SNS FIFO │ FIFO topic (options 6,7,24)      │
├──────────────────────────────┼──────────┼──────────────────────────────────┤
│  sns-demo-inventory-queue    │ SQS Std  │ filter: order_placed             │
│  sns-demo-billing-queue      │ SQS Std  │ filter: payment_due              │
│  sns-demo-analytics-queue    │ SQS Std  │ no filter (gets ALL)             │
│  sns-demo-highvalue-queue    │ SQS Std  │ filter: amount >= 500            │
│  sns-demo-payload-queue      │ SQS Std  │ body filter: source=mobile-app   │
│  sns-demo-wrapped-queue      │ SQS Std  │ raw=false (SNS JSON envelope)    │
│  sns-demo-fifo-queue.fifo    │ SQS FIFO │ FIFO topic subscriber            │
│  sns-demo-dlq-source-queue   │ SQS Std  │ redrive→sqs-dlq after 2 fails   │
├──────────────────────────────┼──────────┼──────────────────────────────────┤
│  sns-demo-dlq                │ SQS Std  │ SNS subscription DLQ (Layer 1)   │
│  sns-demo-sqs-dlq            │ SQS Std  │ SQS redrive DLQ (Layer 2)       │
└──────────────────────────────┴──────────┴──────────────────────────────────┘
```

### Message flow examples — which queues get which messages?

```
  Publish: event_type="order_placed", amount=$129

    sns-demo-orders
        ├──→ sns-demo-inventory-queue   ✓ (event_type match)
        ├──→ sns-demo-billing-queue     ✗ (wants payment_due)
        ├──→ sns-demo-analytics-queue   ✓ (no filter)
        ├──→ sns-demo-highvalue-queue   ✗ ($129 < $500)
        ├──→ sns-demo-payload-queue     ✗ (body has no source=mobile-app)
        ├──→ sns-demo-wrapped-queue     ✓ (no filter)
        └──→ sns-demo-dlq-source-queue  ✗ (wants dlq_test)

  Publish: event_type="order_placed", amount=$2500

    sns-demo-orders
        ├──→ sns-demo-inventory-queue   ✓
        ├──→ sns-demo-analytics-queue   ✓
        ├──→ sns-demo-highvalue-queue   ✓ ($2500 >= $500)
        └──→ sns-demo-wrapped-queue     ✓

  Publish: event_type="payment_due", amount=$299

    sns-demo-orders
        ├──→ sns-demo-billing-queue     ✓ (event_type match)
        ├──→ sns-demo-analytics-queue   ✓
        └──→ sns-demo-wrapped-queue     ✓

  Publish: body={"source":"mobile-app",...}, event_type="order_placed"

    sns-demo-orders
        ├──→ sns-demo-inventory-queue   ✓ (attr: order_placed)
        ├──→ sns-demo-analytics-queue   ✓
        ├──→ sns-demo-payload-queue     ✓ (body: source=mobile-app)
        └──→ sns-demo-wrapped-queue     ✓

  Publish: event_type="dlq_test"

    sns-demo-orders
        ├──→ sns-demo-dlq-source-queue  ✓ (event_type match)
        ├──→ sns-demo-analytics-queue   ✓
        └──→ sns-demo-wrapped-queue     ✓
```

### DLQ flow — Layer 1 vs Layer 2

```
  LAYER 1 — SNS delivery failure (option 26):

    Publisher ──→ sns-demo-orders ──→ sns-demo-dlq-source-queue
                                            │
                                     SQS policy = DENY
                                     SNS gets AccessDenied
                                     3 retries exhausted
                                            │
                                            ▼
                                     sns-demo-dlq
                                     (check in AWS Console → SQS)

  LAYER 2 — Consumer processing failure (options 17 → 18):

    Publisher ──→ sns-demo-orders ──→ sns-demo-dlq-source-queue ✓ delivered
                                            │
                                     Consumer receives msg
                                     but CRASHES / doesn't delete
                                            │
                                     visibilityTimeout (5s) expires
                                     message becomes visible again
                                            │
                                     Consumer receives AGAIN, fails AGAIN
                                     ApproximateReceiveCount = 2
                                            │
                                     maxReceiveCount (2) exceeded
                                            │
                                            ▼
                                     sns-demo-sqs-dlq
                                     (check in AWS Console → SQS)
```

## Project structure

```
sns-java-demo/
├── pom.xml                              # AWS SDK v2 (SNS + SQS) + Gson
└── src/main/java/com/demo/sns/
    ├── SnsPlayground.java               # Interactive CLI (main class)
    ├── SnsConfig.java                   # Bootstrap: creates topics, queues, subscriptions
    ├── SnsPublisher.java                # Publish: single, attrs, batch, FIFO, dedup, claim-check
    ├── SnsSubscriber.java               # Receive from SQS, drain, stats, competing consumers
    └── ConceptExplainer.java            # Concept explanations printed before each operation
```

## Changing the region

Edit `SnsConfig.java`:
```java
private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai (current)
```

## Cleanup

Delete everything when done:
```bash
# Delete SNS subscriptions and topics
aws sns list-subscriptions-by-topic --topic-arn $(aws sns list-topics --query 'Topics[?ends_with(TopicArn,`:sns-demo-orders`)].TopicArn' --output text) --query 'Subscriptions[].SubscriptionArn' --output text | tr '\t' '\n' | xargs -I {} aws sns unsubscribe --subscription-arn {}
aws sns list-subscriptions-by-topic --topic-arn $(aws sns list-topics --query 'Topics[?ends_with(TopicArn,`:sns-demo-fifo.fifo`)].TopicArn' --output text) --query 'Subscriptions[].SubscriptionArn' --output text | tr '\t' '\n' | xargs -I {} aws sns unsubscribe --subscription-arn {}
aws sns delete-topic --topic-arn $(aws sns list-topics --query 'Topics[?ends_with(TopicArn,`:sns-demo-orders`)].TopicArn' --output text)
aws sns delete-topic --topic-arn $(aws sns list-topics --query 'Topics[?ends_with(TopicArn,`:sns-demo-fifo.fifo`)].TopicArn' --output text)

# Delete SQS queues
for q in sns-demo-inventory-queue sns-demo-billing-queue sns-demo-analytics-queue sns-demo-highvalue-queue sns-demo-dlq sns-demo-payload-queue sns-demo-wrapped-queue sns-demo-sqs-dlq sns-demo-dlq-source-queue; do
  aws sqs delete-queue --queue-url $(aws sqs get-queue-url --queue-name $q --query QueueUrl --output text) 2>/dev/null
done
aws sqs delete-queue --queue-url $(aws sqs get-queue-url --queue-name sns-demo-fifo-queue.fifo --query QueueUrl --output text) 2>/dev/null

# Delete IAM access key
# Go to AWS Console → IAM → Users → sns-demo-user → Security credentials → Delete access key
```

## Cost

- SNS: First **1 million requests/month free**. This demo uses ~100-300 publishes per session.
- SQS: First **1 million requests/month free**. This demo uses ~500-1000 SQS operations per session.
- SNS → SQS delivery is **FREE** (no per-delivery charge).
- Effectively **$0.00** for this demo.
