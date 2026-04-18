# Kafka Java Playground

Interactive CLI running a **7-broker Kafka cluster** (KRaft mode, RF=3, min.ISR=2) locally via Docker. **Produce, consume, kill brokers, watch leader election, see URP** — then see everything in a **web-based Kafka UI** at `localhost:8080`.

## Prerequisites

| What | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker Desktop | Latest | `docker --version` |

## Setup (~2 minutes)

### Option A: One-click from IntelliJ (recommended)

Right-click `KafkaEnvironment.java` → **Run** (no args needed). It will:
1. Start Kafka broker + Kafka UI via Docker Compose
2. Wait for both to become healthy
3. Auto-open **http://localhost:8080** (Kafka UI) in your browser

Then right-click `KafkaPlayground.java` → **Run** for the interactive CLI.

### Option B: Maven commands from terminal

```bash
cd src/HLD/Components/examples/kafka-java-demo

# Start Kafka + Kafka UI + open browser
mvn compile exec:java -Pstart-kafka

# Run the interactive playground
mvn compile exec:java

# Check status of containers
mvn compile exec:java -Pstatus

# Stop everything
mvn compile exec:java -Pstop-kafka
```

### Option C: Docker Compose directly

```bash
cd src/HLD/Components/examples/kafka-java-demo

# Start Kafka broker (KRaft mode, no ZooKeeper) + Kafka UI
docker compose up -d

# Verify both are running
docker compose ps
```

Wait ~10 seconds for Kafka to be healthy. You'll see:
- `kafka-demo` — the Kafka broker (port 9092)
- `kafka-ui` — web dashboard (port 8080)

### Open Kafka UI

Open **http://localhost:8080** in your browser. This is your "AWS Console" equivalent. You can see:
- Topics, partitions, messages, consumer groups, brokers, lag — everything.

### Run the Playground

```bash
# From kafka-java-demo directory:
mvn compile exec:java
```

## Recommended Walkthrough

```
Step 1:   Press 1   → Send with key           (see: partition routing, hash(key) % 4)
Step 2:   Press 2   → Send ordered events      (see: 5 events, SAME partition, in order)
Step 3:   Press 10  → Consume & commit         (see: manual offset commit, at-least-once)
Step 4:   Press 3   → Send with headers        (see: metadata in Kafka UI)
Step 5:   Press 4   → Send to specific parts   (see: explicit partition control)
Step 6:   Press 6   → Null key messages        (see: sticky partitioning distribution)
Step 7:   Press 5   → Compare acks=0 vs all    (see: speed vs safety tradeoff)
Step 8:   Press 9   → Bulk send 1000 msgs      (see: throughput, msgs/sec)
Step 9:   Press 14  → Show consumer lag         (see: per-partition lag table)
Step 10:  Press 12  → Competing consumers       (see: 3 threads sharing partitions)
Step 11:  Press 13  → Replay from beginning     (see: rewind and re-read all data)
Step 12:  Press 7   → Send to compacted topic   (see: multiple versions per key)
Step 13:  Press 8   → Send tombstone            (see: key deletion marker)
Step 14:  Press 15  → Read compacted topic      (see: latest value per key only)
Step 15:  Press 11  → Show partition assignment  (see: rebalance listener fire)

--- ADVANCED ---
Step 16:  Press 16  → Transactional producer      (see: commit vs abort, EOS)
Step 17:  Press 17  → Idempotent producer          (see: PID + seq number dedup)
Step 18:  Press 18  → Retry + DLQ                  (see: failed → retry → dead letter)
Step 19:  Press 19  → Multiple consumer groups      (see: 2 groups, both get ALL msgs)
Step 20:  Press 20  → Cluster & ISR inspection      (see: brokers, leader, replicas, ISR)
Step 21:  Press 21  → Head-of-line blocking         (see: poison pill blocks later msgs)
Step 22:  Press 22  → Consume transactional         (see: read_committed filters aborted)
```

## What to observe in Kafka UI (localhost:8080)

| Operation | What to look for in Kafka UI |
|-----------|------------------------------|
| Send with key | Topics → demo-orders → Messages → see partition column |
| Ordered events | Same key always in same partition, offsets sequential |
| Send with headers | Click any message → Headers tab |
| Null key | Messages spread across partitions |
| Bulk send | Messages count jumps; check per-partition distribution |
| Consume & commit | Consumers tab → consumer group → see committed offsets |
| Consumer lag | Consumers tab → group → Lag column per partition |
| Competing consumers | Multiple members in same group, partitions split |
| Compacted topic | demo-user-profiles → Messages → multiple versions per key |
| Tombstone | Message with null value appears |
| Transactional producer | demo-txn-input + demo-txn-output → see committed + aborted msgs |
| Retry + DLQ | demo-orders-retry and demo-orders-dlq topics appear with failed msgs |
| Cluster inspection | Brokers tab → see leader, ISR, replicas per partition |
| Multiple groups | Consumers tab → two separate groups with independent offsets |

## What you can do

```
┌───────────────────────────────────────────────────────────────┐
│  PRODUCE                                                      │
│    1.  Send single message with key  (partition routing)       │
│    2.  Send ordered events (same key → same partition)        │
│    3.  Send with headers (metadata)                           │
│    4.  Send to specific partition (explicit)                   │
│    5.  Compare acks=0 vs acks=all (speed vs safety)           │
│    6.  Send null-key messages (sticky partitioning)           │
│    7.  Send to compacted topic (log compaction)               │
│    8.  Send tombstone (delete key in compacted topic)         │
│    9.  Bulk send N messages (throughput + lag demo)            │
│                                                               │
│  CONSUME                                                      │
│   10.  Consume & commit (manual offset commit)                │
│   11.  Show partition assignment (rebalance listener)         │
│   12.  Competing consumers (multi-threaded consumer group)    │
│   13.  Replay from beginning (seek to offset 0)              │
│   14.  Show consumer lag (per-partition)                      │
│   15.  Read compacted topic (latest value per key)            │
│                                                               │
│  ADVANCED (from doc sections 6, 8, 13, 14)                    │
│   16.  Transactional producer — exactly-once (EOS)            │
│   17.  Idempotent producer — duplicate detection              │
│   18.  Retry + DLQ pattern (failed → retry → dead letter)    │
│   19.  Multiple consumer groups (independent readers)         │
│   20.  Cluster & ISR inspection (Admin API)                   │
│   21.  Head-of-line blocking (offset-based ack problem)       │
│   22.  Consume transactional messages (read_committed)        │
│                                                               │
│    0.  Exit                                                   │
└───────────────────────────────────────────────────────────────┘
```

## Concepts demonstrated

| # | Concept | What you learn |
|---|---------|----------------|
| 1 | **Key-based partitioning** | hash(key) % partitions determines target partition |
| 2 | **Message ordering** | Same key = same partition = ordered processing |
| 3 | **Headers** | Metadata without touching the message value |
| 4 | **Explicit partitioning** | Override automatic key-based routing |
| 5 | **Acks modes** | acks=0 (fast/unsafe) vs acks=all (slow/safe) |
| 6 | **Sticky partitioning** | Null key batching behavior since Kafka 2.4 |
| 7 | **Log compaction** | Keep only latest value per key |
| 8 | **Tombstones** | Delete a key by publishing null value |
| 9 | **Throughput** | Batching + compression + linger.ms |
| 10 | **Manual offset commit** | At-least-once delivery pattern |
| 11 | **Rebalancing** | Partition assignment when consumers join/leave |
| 12 | **Consumer groups** | Parallel processing, no duplicate delivery within group |
| 13 | **Replay** | Seek to beginning, re-read all retained messages |
| 14 | **Consumer lag** | Per-partition monitoring, health indicator |
| 15 | **Compacted reads** | Read current state from compacted topic |
| 16 | **Exactly-Once (EOS)** | Transactional producer: commit vs abort, atomic multi-topic writes |
| 17 | **Idempotent producer** | PID + sequence number prevents network retry duplicates |
| 18 | **Retry + DLQ** | Failed messages → retry topic → dead letter queue (no built-in DLQ) |
| 19 | **Multiple groups** | Two independent consumer groups read ALL messages from same topic |
| 20 | **Cluster & ISR** | Admin API: brokers, controller, leader, replicas, ISR per partition |
| 21 | **Head-of-line blocking** | Offset-based ack: poison pill blocks all subsequent messages |
| 22 | **Transactional consume** | read_committed isolation filters out aborted transaction messages |
| 23 | **Cluster topology** | See all 7 brokers, controller quorum, broker roles |
| 24 | **URP check** | Under-Replicated Partitions — the #1 Kafka monitoring metric |
| 25 | **Stop broker** | Kill a broker, see ISR shrink and leader election |
| 26 | **Start broker** | Recover a broker, see ISR restore and URP drop to 0 |
| 27 | **Full failure sim** | Automated: kill leader → new election → produce → recover |
| 28 | **Leader distribution** | How leaders are spread across 7 brokers for load balancing |

## Cluster Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                7-BROKER KAFKA CLUSTER                        │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Controller Quorum (KRaft / Raft consensus)           │   │
│  │  Broker 1 ◄──► Broker 2 ◄──► Broker 3                │   │
│  │  (broker+    (broker+     (broker+                    │   │
│  │   controller)  controller)  controller)               │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐          │
│  │Broker 4 │ │Broker 5 │ │Broker 6 │ │Broker 7 │          │
│  │ (pure)  │ │ (pure)  │ │ (pure)  │ │ (pure)  │          │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘          │
│                                                             │
│  Every topic: RF=3, min.ISR=2                               │
│  Each partition: 1 leader + 2 followers across 3 brokers    │
│  Survives 1 broker failure with ZERO data loss              │
└─────────────────────────────────────────────────────────────┘
```

## Project structure

```
kafka-java-demo/
├── docker-compose.yml                     # 7-broker Kafka cluster (KRaft) + Kafka UI
├── pom.xml                                # Kafka clients 3.9.0 + Gson
└── src/main/
    ├── java/com/demo/kafka/
    │   ├── KafkaEnvironment.java          # Start/stop Kafka + open UI (run from IntelliJ)
    │   ├── KafkaPlayground.java           # Interactive CLI (main class)
    │   ├── KafkaConfig.java               # Connection, topic creation, configs
    │   ├── KafkaProducerDemo.java         # 11 producer operations
    │   ├── KafkaConsumerDemo.java         # 11 consumer operations
    │   ├── ClusterSimulation.java         # 7-broker failure/recovery simulations
    │   └── ConceptExplainer.java          # Concept boxes printed before each op
    └── resources/
        └── simplelogger.properties        # Suppress verbose Kafka client logs
```

## Cleanup

```bash
# Stop Kafka and Kafka UI
docker compose down

# Remove all data (volumes)
docker compose down -v
```

## Cost

**Completely free.** Everything runs locally on Docker. No AWS account needed.

## Running it next time (quick reference)

```bash
cd src/HLD/Components/examples/kafka-java-demo

# 1. Start Kafka + Kafka UI (wait ~15s for healthy)
docker compose up -d

# 2. Verify both containers are running and healthy
docker compose ps

# 3. Open Kafka UI in browser
open http://localhost:8080       # macOS
# xdg-open http://localhost:8080 # Linux

# 4. Run the interactive playground
mvn compile exec:java

# 5. When done, stop everything
docker compose down

# To also wipe all topic data (clean slate)
docker compose down -v
```

**Or, from IntelliJ:**

1. Right-click `KafkaEnvironment.java` → **Run** (starts Kafka + opens Kafka UI in browser)
2. Right-click `KafkaPlayground.java` → **Run** (interactive CLI)
3. To stop: Run `KafkaEnvironment` with program argument `stop`

**Or, via Maven profiles:**

```bash
mvn compile exec:java -Pstart-kafka   # start + auto-open browser
mvn compile exec:java                  # run playground
mvn compile exec:java -Pstatus         # check container status
mvn compile exec:java -Pstop-kafka     # stop everything
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Connection refused localhost:9092` | Run `docker compose up -d` and wait 10s |
| `docker compose` not found | Update Docker Desktop or use `docker-compose` (hyphen) |
| Port 8080 already in use | Change the port in docker-compose.yml: `"8081:8080"` |
| Port 9092 already in use | Stop other Kafka instances: `docker ps` then `docker stop <id>` |
| Maven compilation errors | Ensure Java 17+: `java -version` |
| Kafka UI shows cluster "Offline" | This is a Docker networking issue — make sure you are using the dual-listener docker-compose.yml (PLAINTEXT_HOST for host, PLAINTEXT_DOCKER for containers). Run `docker compose down -v && docker compose up -d` to restart fresh. |
