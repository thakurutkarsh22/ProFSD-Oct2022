# AWS SQS Java Playground

Interactive CLI that lets you **send, receive, peek, batch, delay, FIFO-order, DLQ, and run competing consumers** against **real AWS SQS queues** — then verify everything in the AWS Console. Each operation prints a concept explanation before executing.

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
2. Name: `sqs-demo-user`
3. Attach policy: **AmazonSQSFullAccess**
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
# Check if there's an old session token
aws configure get aws_session_token

# If it prints anything, remove the aws_session_token line from ~/.aws/credentials
```

### 3. Verify access

```bash
aws sts get-caller-identity
# Should print your account ID + ARN
```

### 4. IAM permissions needed

Your IAM user/role needs **AmazonSQSFullAccess** (or these specific actions):
- `sqs:CreateQueue`, `sqs:GetQueueUrl`, `sqs:GetQueueAttributes`
- `sqs:SetQueueAttributes`, `sqs:SendMessage`, `sqs:SendMessageBatch`
- `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:PurgeQueue`

The app will **auto-create** these 3 queues on startup:
- `sqs-demo-standard` — Standard queue with DLQ redrive (maxReceiveCount=3)
- `sqs-demo-fifo.fifo` — FIFO queue with content-based deduplication
- `sqs-demo-dlq` — Dead Letter Queue (catches poison pills)

## How to Run

```bash
# From the repo root:
cd src/HLD/Components/examples/sqs-java-demo

# Compile and run (first time downloads dependencies, ~30s):
mvn compile exec:java

# Subsequent runs are fast (~1s compile):
mvn compile exec:java
```

The interactive menu will appear. Type a number and press Enter.

### Recommended walkthrough order

```
Step 1:  Press 1  → Send a single message        (learn: message lifecycle, MessageId)
Step 2:  Press 10 → Check queue stats             (learn: the 3 counters)
Step 3:  Press 6  → Receive & delete              (learn: long polling, ReceiptHandle)
Step 4:  Press 2  → Send with attributes          (learn: body vs metadata)
Step 5:  Press 7  → Peek (don't delete)           (learn: visibility timeout)
Step 6:  Press 3  → Send batch                    (learn: batching, cost savings)
Step 7:  Press 4  → Send delayed message          (learn: DelaySeconds)
Step 8:  Press 5  → Send FIFO messages            (learn: MessageGroupId, dedup, ordering)
Step 9:  Press 8  → Receive FIFO                  (learn: SequenceNumber, per-group order)
Step 10: Press 9  → DLQ simulation                (learn: poison pills, redrive policy)
Step 11: Press 10 → Competing consumers (Standard)(learn: horizontal scaling, no partitions)
Step 12: Press 11 → Competing consumers (FIFO)    (learn: message group locking, parallelism)
```

## What you can do

```
┌───────────────────────────────────────────────────────────┐
│  PRODUCE                                                  │
│    1. Send a single message (Standard)                    │
│    2. Send order event with attributes                    │
│    3. Send batch (up to 10 messages)                      │
│    4. Send delayed message                                │
│    5. Send FIFO messages (ordered per customer)           │
│                                                           │
│  CONSUME                                                  │
│    6. Receive & delete (Standard)                         │
│    7. Peek messages (receive, don't delete)               │
│    8. Receive FIFO messages (shows ordering)              │
│                                                           │
│  DLQ                                                      │
│    9. Simulate DLQ flow (poison pill)                     │
│                                                           │
│  COMPETING CONSUMERS (multi-threaded)                     │
│   10. Standard queue — N consumers competing              │
│   11. FIFO queue — N consumers with group locking         │
│                                                           │
│  ADMIN                                                    │
│   12. Show queue stats (all 3 queues)                     │
│   13. Purge a queue                                       │
│    0. Exit                                                │
└───────────────────────────────────────────────────────────┘
```

## What to observe in AWS Console

After running each operation, open **AWS Console → SQS** (in your region) and:

| Operation | What to look for in Console |
|-----------|---------------------------|
| Send single/batch | Messages Available count goes up |
| Send with attributes | Click a message → see custom attributes (Source, Priority) |
| Send delayed | Message appears in "Delayed" count, then moves to "Available" |
| FIFO send | Messages tab shows SequenceNumber and MessageGroupId |
| Receive & delete | Messages Available count goes down |
| Peek (no delete) | Messages go to "In-Flight", then return to "Available" |
| DLQ simulation | Watch `sqs-demo-dlq` count go up after 3 failed receives |
| Competing consumers | Watch Available count drop rapidly as multiple threads consume |
| FIFO competing | Same group processed in order; different groups in parallel |
| Queue stats | Compare with Console's "Monitoring" tab |

## Concepts demonstrated

Each menu option prints a detailed concept explanation before executing. Concepts covered:

| # | Concept | SQS vs Kafka comparison |
|---|---------|------------------------|
| 1 | **Message Lifecycle** — sent → available → in-flight → deleted | Kafka retains; SQS deletes after consume |
| 2 | **Message Attributes** — body vs metadata (up to 10 attrs) | Kafka has headers; SQS has attributes |
| 3 | **Batch Operations** — SendMessageBatch (10x cost savings) | Kafka batches via linger.ms + batch.size |
| 4 | **Delay Queues** — per-message DelaySeconds (0-900s) | Kafka has no built-in delay |
| 5 | **FIFO & MessageGroupId** — ordering per group | Like Kafka partition key |
| 6 | **Long Polling & ReceiptHandle** — efficient polling + proof-of-receipt | Kafka uses consumer offsets |
| 7 | **Visibility Timeout** — in-flight window, peek pattern | Kafka has no equivalent (offset-based) |
| 8 | **FIFO Receive & SequenceNumber** — proof of ordering | Like Kafka partition offsets |
| 9 | **Dead Letter Queue** — automatic redrive after maxReceiveCount | Kafka: you implement DLQ yourself |
| 10 | **Competing Consumers** — horizontal scaling, no partitions | Kafka: limited by partition count |
| 11 | **FIFO Group Locking** — per-group single-consumer guarantee | Kafka: 1 consumer per partition |

## Project structure

```
sqs-java-demo/
├── pom.xml                          # AWS SDK v2 + Gson dependencies
└── src/main/java/com/demo/sqs/
    ├── SqsPlayground.java           # Interactive CLI (main class)
    ├── SqsConfig.java               # Client bootstrap, auto-creates 3 queues
    ├── SqsProducer.java             # Send: single, batch, attributes, delay, FIFO
    ├── SqsConsumer.java             # Receive, peek, DLQ sim, competing consumers
    └── ConceptExplainer.java        # Concept explanations printed before each op
```

## Changing the region

Edit `SqsConfig.java`:
```java
private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai (current)
```

## Cleanup

Delete the queues when done:
```bash
aws sqs delete-queue --queue-url $(aws sqs get-queue-url --queue-name sqs-demo-standard --query QueueUrl --output text)
aws sqs delete-queue --queue-url $(aws sqs get-queue-url --queue-name sqs-demo-fifo.fifo --query QueueUrl --output text)
aws sqs delete-queue --queue-url $(aws sqs get-queue-url --queue-name sqs-demo-dlq --query QueueUrl --output text)
```

Also delete the IAM access key from **AWS Console → IAM → Users → sqs-demo-user → Security credentials → Delete access key**.

## Cost

SQS has a **free tier of 1 million requests/month**. This demo uses ~200-500 requests per session — effectively **free**.
