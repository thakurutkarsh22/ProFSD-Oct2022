# AWS Kinesis Java Playground

Interactive CLI that lets you **send records, read them back, simulate hot shards, replay from a timestamp, split/merge shards, and watch CloudWatch metrics** against **real AWS Kinesis Data Streams** — then verify everything in the AWS Console. Each operation prints a concept explanation before executing.

Paired deep-dive: [`../../08-Kinesis.md`](../../08-Kinesis.md)

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
2. Name: `kinesis-demo-user`
3. Attach policy: **AmazonKinesisFullAccess**
   - *(If you also want to see CloudWatch metrics via the menu, attach **CloudWatchReadOnlyAccess** as well.)*
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

aws kinesis list-streams
# Should print {"StreamNames": []} on a fresh account
```

### 4. IAM permissions needed

`AmazonKinesisFullAccess` (or at minimum):
- `kinesis:CreateStream`, `DeleteStream`, `DescribeStreamSummary`, `ListShards`
- `kinesis:PutRecord`, `PutRecords`
- `kinesis:GetShardIterator`, `GetRecords`
- `kinesis:SplitShard`, `MergeShards`, `UpdateShardCount`
- `kinesis:IncreaseStreamRetentionPeriod`
- `cloudwatch:GetMetricStatistics` (optional, for the metrics snapshot menu)

The app will **auto-create** these 2 streams on startup:

| Stream | Mode | Shards | Purpose |
|--------|------|--------|---------|
| `kinesis-demo-ondemand`     | On-Demand    | auto | See auto-scaling; all basic tests |
| `kinesis-demo-provisioned`  | Provisioned  | 2    | Hash-range routing, hot shards, split/merge |

## How to Run

```bash
# From the repo root:
cd src/HLD/Components/examples/kinesis-java-demo

# First run downloads AWS SDK jars (~30 s). Subsequent runs are fast.
mvn compile exec:java
```

The interactive menu appears. Type a number and press Enter. First-time start-up takes ~30 s because each new stream must reach `ACTIVE` status.

### Recommended walkthrough order

```
Step 1:  Press 7   → List shards (see hash-range split across 2 shards)
Step 2:  Press 1   → Send a single message       (learn: PutRecord, ShardId, SequenceNumber)
Step 3:  Press 2   → Send a click event          (learn: PK=userId → per-user ordering)
Step 4:  Press 3   → Batch send with UNIFORM PK  (learn: even shard distribution)
Step 5:  Press 4   → Batch send with HOT PK      (learn: the #1 production failure)
Step 6:  Press 11  → Show IteratorAge per shard  (learn: the P1 monitoring metric)
Step 7:  Press 8   → Read from TRIM_HORIZON      (learn: replay-from-beginning)
Step 8:  Press 10  → Replay from N minutes ago   (learn: AT_TIMESTAMP replay)
Step 9:  Press 5   → PutRecord with ExplicitHashKey (learn: force shard routing)
Step 10: Press 12  → Competing consumers         (learn: what KCL automates)
Step 11: Press 14  → SplitShard                  (learn: resharding + parent/child)
Step 12: Press 7   → List shards again           (see CLOSED parent + 2 children)
Step 13: Press 17  → CloudWatch metrics snapshot (learn: the alarm set)
```

### Tip — running two terminals

For option **9 (LATEST tail -f)** and option **12 (competing consumers)** you get the most insight by opening a second terminal and running option **3** or **6** (stress test) to generate live traffic.

## What to observe in AWS Console

After running each operation, open **AWS Console → Kinesis → Data streams** (in your region) and:

| Operation | What to look for in Console |
|-----------|---------------------------|
| Any produce | Click the stream → **Monitoring** tab → `IncomingBytes`, `IncomingRecords` (1-min lag) |
| Hot-shard batch (option 4) | Per-shard `WriteProvisionedThroughputExceeded` > 0 on one shard |
| Any produce | **Data viewer** → pick shard, pick starting position, Get records → see your record |
| Read from TRIM_HORIZON | Console data viewer with "Trim horizon" shows the same records |
| Replay from N min ago | Console data viewer with "At timestamp" — matches what your code returned |
| SplitShard (14) | **Shards** tab shows parent **CLOSED**, two new children **OPEN** |
| Retention change (16) | Stream summary shows new `Data retention period` |
| CloudWatch metrics (17) | Console **Monitoring** tab shows the same values we fetched |

## What you can do

```
┌───────────────────────────────────────────────────────────────┐
│  PRODUCE                                                      │
│    1. PutRecord — one record                                  │
│    2. Put a structured click event (PK=userId)                │
│    3. PutRecords batch — UNIFORM PK (healthy)                 │
│    4. PutRecords batch — HOT PK  (production failure!)        │
│    5. PutRecord with ExplicitHashKey (force shard)            │
│    6. Stress test — N records, show throttles                 │
│                                                               │
│  CONSUME                                                      │
│    7. List shards + hash ranges                               │
│    8. Read shard from TRIM_HORIZON (beginning)                │
│    9. Read shard at LATEST (tail -f)                          │
│   10. Replay from N minutes ago (AT_TIMESTAMP)                │
│   11. Show IteratorAge per shard (the P1 metric)              │
│   12. Competing consumers — one thread per shard              │
│                                                               │
│  ADMIN                                                        │
│   13. DescribeStreamSummary                                   │
│   14. SplitShard (provisioned)                                │
│   15. MergeShards (provisioned)                               │
│   16. IncreaseStreamRetentionPeriod                           │
│   17. CloudWatch metrics snapshot (last 5 min)                │
│   18. Delete + recreate a stream (= purge)                    │
│                                                               │
│    0. Exit                                                    │
└───────────────────────────────────────────────────────────────┘
```

## Concepts demonstrated

Each menu option prints a detailed concept explanation before executing. Concepts covered:

| # | Concept | Kinesis vs Kafka comparison |
|---|---------|------------------------|
| 1 | **PutRecord** — partition key → MD5 → shard | Same as Kafka `ProducerRecord(key, value)` |
| 2 | **PK = userId** → per-user ordering | Same as Kafka partition key |
| 3 | **PutRecords** — batch up to 500, FailedRecordCount is per-record, not atomic | Kafka producer batches transparently |
| 4 | **Hot shard** — same PK on every record → one shard saturates | Same failure mode as Kafka hot partition |
| 5 | **ExplicitHashKey** — bypass PK hashing, force a shard | Kafka uses `Partitioner` interface |
| 6 | **Throttling** — 1 MiB/s / 1,000 rec/s per shard → `ProvisionedThroughputExceededException` | Kafka backpressures via producer buffer |
| 7 | **Shards** own contiguous hash ranges | Kafka partitions do too |
| 8 | **TRIM_HORIZON** — replay everything still in retention | Kafka `--from-beginning` |
| 9 | **LATEST** — tail from now | Kafka `auto.offset.reset=latest` |
| 10 | **AT_TIMESTAMP** — replay from wall-clock time | Kafka `--offset by-time` |
| 11 | **IteratorAgeMilliseconds** — #1 metric; alerts at 5 min / 10 % of retention | Kafka `consumer_lag` |
| 12 | **Per-shard consumer** — what KCL automates (shard discovery, lease coordination, checkpoints) | Kafka consumer groups |
| 14 | **SplitShard** — hot-shard remediation, parent→2 children handshake | Kafka has no live repartitioning |
| 15 | **MergeShards** — scale-down cost when traffic drops | n/a in Kafka |
| 16 | **Retention 24 h → 365 d** — replay windows | Kafka retention by time/size |
| 17 | **CloudWatch** — the production alarm set | Kafka: JMX + Prometheus/Grafana |

## Project structure

```
kinesis-java-demo/
├── pom.xml                              # AWS SDK v2 (kinesis + cloudwatch) + Gson
└── src/main/java/com/demo/kinesis/
    ├── KinesisPlayground.java           # Interactive CLI (main class)
    ├── KinesisConfig.java               # Client bootstrap, auto-creates 2 streams
    ├── KinesisProducer.java             # PutRecord, PutRecords, hot shard, explicit hash, stress
    ├── KinesisConsumer.java             # Shard iterator (all 4 types), competing consumers, IteratorAge
    ├── KinesisAdmin.java                # Describe, split, merge, retention, CloudWatch, purge
    └── ConceptExplainer.java            # Concept boxes printed before each op
```

## Changing the region

Edit `KinesisConfig.java`:
```java
private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai (current)
```
Then `mvn compile exec:java` again.

## Cleanup

Delete the streams when done:
```bash
aws kinesis delete-stream --stream-name kinesis-demo-ondemand     --enforce-consumer-deletion
aws kinesis delete-stream --stream-name kinesis-demo-provisioned  --enforce-consumer-deletion

# Verify:
aws kinesis list-streams
```

Also delete the IAM access key from **AWS Console → IAM → Users → kinesis-demo-user → Security credentials → Delete access key**.

## Cost

- **On-Demand stream**: ~$0.04/hr idle + $0.04 per GB ingested. A 1-hour demo session ≈ **$0.04–0.10**.
- **Provisioned stream** with 2 shards: $0.015 × 2 × 1 h = **$0.03/hr**. Plus $0.014 per 1M PUT payload units.
- **CloudWatch GetMetricStatistics**: free within the 1 M-requests-per-month tier.

**IMPORTANT:** after you're done, run the cleanup commands above. A forgotten 2-shard Provisioned stream costs ~$22/month; a forgotten On-Demand stream with no traffic costs ~$29/month (the base hourly fee is charged even when idle).

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|--------------|-----|
| `software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException` right after startup | Stream is still `CREATING` | The bootstrap waits up to 2 min; retry if you hit a transient case |
| `ExpiredIteratorException` | Shard iterator is valid for 5 min; you waited too long between `GetShardIterator` and `GetRecords` | Just re-run the menu option; the code refetches iterators on each call |
| `ProvisionedThroughputExceededException` while batch-sending (option 4) | You're hot-sharding on purpose | This is the point — read the concept box! |
| `Unable to load AWS credentials` | `~/.aws/credentials` missing or has stale session token | Re-run `aws configure` and check with `aws sts get-caller-identity` |
| Every `GetRecords` returns empty and `lagMs` = 0 | You're at `LATEST` and no one is writing | Run option 3 in another terminal to produce records |
| CloudWatch snapshot shows `datapoints=0` | It takes ~1–2 minutes for Kinesis to publish metrics | Wait a minute and retry option 17 |
