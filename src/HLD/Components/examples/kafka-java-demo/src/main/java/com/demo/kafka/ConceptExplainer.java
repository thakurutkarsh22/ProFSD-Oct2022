package com.demo.kafka;

public class ConceptExplainer {

    public static void sendWithKey() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: KEY-BASED PARTITIONING                                ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Every Kafka message has an optional KEY.                        ║
        ║  If key is present: hash(key) %% partitions = target partition    ║
        ║                                                                  ║
        ║  Same key → ALWAYS same partition → ORDERED processing.          ║
        ║  This is how Kafka guarantees per-entity ordering.               ║
        ║                                                                  ║
        ║  WATCH: The partition number in the output. Send the same        ║
        ║  key multiple times — it always goes to the same partition.      ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void sendOrdered() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: MESSAGE ORDERING                                       ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka guarantees ordering WITHIN a partition only.              ║
        ║  No ordering across partitions.                                  ║
        ║                                                                  ║
        ║  Here we send 5 status updates for the SAME order:              ║
        ║  CREATED → PAYMENT → PROCESSING → SHIPPED → DELIVERED           ║
        ║                                                                  ║
        ║  Same key = same partition = consumer sees them in exact order.  ║
        ║  WATCH: All 5 messages land in the SAME partition.               ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void sendHeaders() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: MESSAGE HEADERS                                        ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Headers = metadata attached to a message (like HTTP headers).   ║
        ║  Key-value pairs. Don't affect partitioning or storage.          ║
        ║                                                                  ║
        ║  Use for: tracing IDs, source service, event-type, priority.     ║
        ║  Allows consumer-side filtering WITHOUT parsing the value.       ║
        ║                                                                  ║
        ║  Similar to: SQS MessageAttributes, RabbitMQ message headers.    ║
        ║  → Open Kafka UI → Messages → click a message → see headers.    ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void sendToPartition() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: EXPLICIT PARTITION ASSIGNMENT                          ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Normally: hash(key) decides the partition (automatic).          ║
        ║  But you CAN override and specify the partition directly.        ║
        ║                                                                  ║
        ║  ProducerRecord(topic, PARTITION, key, value)                    ║
        ║                        ▲ explicit partition number               ║
        ║                                                                  ║
        ║  Use for: priority lanes, geo-routing, testing.                  ║
        ║  Avoid in production — breaks key-based distribution.            ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void compareAcks() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: ACKNOWLEDGEMENT MODES (acks)                           ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  acks=0    Fire-and-forget. Don't wait for broker response.      ║
        ║            Fastest. Risk of DATA LOSS.                           ║
        ║                                                                  ║
        ║  acks=1    Wait for leader to write. If leader dies before       ║
        ║            replication → data lost.                              ║
        ║                                                                  ║
        ║  acks=all  Wait for ALL in-sync replicas. Slowest but safest.   ║
        ║            Combined with min.insync.replicas=2 → no data loss.  ║
        ║                                                                  ║
        ║  WATCH: The time difference between acks=0 and acks=all.         ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void nullKeys() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: NULL KEY & STICKY PARTITIONING                         ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  key=null → Kafka uses STICKY PARTITIONING (since 2.4):         ║
        ║  Messages batch to ONE partition until batch.size is reached,    ║
        ║  then rotate to the next partition.                              ║
        ║                                                                  ║
        ║  Before 2.4: round-robin (each message = different partition).   ║
        ║  Sticky is better: larger batches → fewer requests → faster.     ║
        ║                                                                  ║
        ║  WATCH: The partition distribution — may be uneven for           ║
        ║  small message counts (normal behavior).                         ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void compactedTopic() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: LOG COMPACTION                                         ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Normal topic: deletes messages after retention time.            ║
        ║  Compacted topic: keeps LATEST value per key forever.            ║
        ║                                                                  ║
        ║  Sending: Alice v1, Alice v2, Alice v3                           ║
        ║  After compaction: only Alice v3 survives.                       ║
        ║                                                                  ║
        ║  Use for: user profiles, configs, CDC changelogs.                ║
        ║  cleanup.policy=compact on the topic.                            ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void tombstone() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: TOMBSTONE (KEY DELETION IN COMPACTED TOPICS)           ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Publishing key="user-X", value=NULL → tombstone marker.         ║
        ║  After compaction: ALL records for that key are removed.         ║
        ║  The tombstone itself is removed after delete.retention.ms.      ║
        ║                                                                  ║
        ║  Use for: GDPR deletion, removing entities from state stores.    ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void basicConsume() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CONSUMER + MANUAL OFFSET COMMIT                        ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Consumer.poll() fetches a batch of messages.                    ║
        ║  consumer.commitSync() tells Kafka: "I'm done with these."      ║
        ║                                                                  ║
        ║  Offset = a single number per (group, partition).                ║
        ║  It means "I've processed everything UP TO this offset."         ║
        ║  NOT per-message acknowledgement (unlike RabbitMQ/SQS).          ║
        ║                                                                  ║
        ║  If consumer crashes before commit → messages reprocessed.       ║
        ║  This is at-least-once delivery (most common pattern).           ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void partitionAssignment() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CONSUMER REBALANCING & PARTITION ASSIGNMENT             ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  When a consumer joins a group, Kafka REBALANCES:                ║
        ║  → Partitions are redistributed among group members.             ║
        ║                                                                  ║
        ║  ConsumerRebalanceListener fires:                                ║
        ║    onPartitionsRevoked  → "these partitions taken away"          ║
        ║    onPartitionsAssigned → "these partitions are yours now"       ║
        ║                                                                  ║
        ║  Max consumers = partition count. Extra consumers sit idle.      ║
        ║  4 partitions + 6 consumers = 4 active + 2 idle.                ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void competingConsumers() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CONSUMER GROUPS — PARALLEL PROCESSING                  ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Multiple consumers in SAME group share the partitions.          ║
        ║  Each partition → exactly ONE consumer in the group.             ║
        ║  No duplicate processing within a group.                         ║
        ║                                                                  ║
        ║  Multiple DIFFERENT groups read ALL messages independently.      ║
        ║  Group A (order processing) and Group B (analytics) both         ║
        ║  see every message.                                              ║
        ║                                                                  ║
        ║  WATCH: Each thread gets different partitions, no overlap.       ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void replay() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: REPLAY (SEEK TO BEGINNING)                             ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka RETAINS messages (unlike SQS/RabbitMQ which delete).      ║
        ║  You can rewind a consumer to any offset and re-read.            ║
        ║                                                                  ║
        ║  consumer.seekToBeginning() → replay from offset 0.             ║
        ║  consumer.seek(partition, offset) → replay from specific point.  ║
        ║                                                                  ║
        ║  Use for: bug fix reprocessing, backfilling a new service,       ║
        ║  rebuilding a search index, debugging.                           ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void consumerLag() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CONSUMER LAG                                           ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Lag = End Offset (latest) - Consumer's Committed Offset.        ║
        ║  It tells you how far behind the consumer is.                    ║
        ║                                                                  ║
        ║  Lag = 0    → consumer is caught up (healthy).                   ║
        ║  Lag growing → consumer can't keep up (problem!).                ║
        ║                                                                  ║
        ║  Monitor PER PARTITION, not just total.                          ║
        ║  A total lag of 100 across 10 partitions = OK.                   ║
        ║  A total lag of 100 all on 1 partition = hot partition problem.   ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void bulkSend() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: THROUGHPUT & BATCHING                                  ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka batches messages for efficiency:                          ║
        ║  • batch.size=16KB → accumulate up to 16KB per partition         ║
        ║  • linger.ms=10 → wait 10ms to fill the batch                   ║
        ║  • compression.type=lz4 → compress each batch                   ║
        ║                                                                  ║
        ║  Result: fewer network requests, higher throughput.              ║
        ║  WATCH: The msgs/sec metric in the output.                       ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void transactionalProducer() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: EXACTLY-ONCE SEMANTICS (TRANSACTIONAL PRODUCER)       ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Problem: Write to Topic A and Topic B. If app crashes after    ║
        ║  writing to A but before B → inconsistency.                     ║
        ║                                                                  ║
        ║  Solution: Transactional Producer wraps multiple sends in a     ║
        ║  transaction. EITHER all succeed OR all are invisible.           ║
        ║                                                                  ║
        ║  beginTransaction() → send() → send() → commitTransaction()     ║
        ║  If anything fails → abortTransaction() → none visible.         ║
        ║                                                                  ║
        ║  Consumer with isolation.level=read_committed only sees          ║
        ║  committed transaction messages.                                 ║
        ║                                                                  ║
        ║  Requires: transactional.id set on producer, acks=all,           ║
        ║            enable.idempotence=true (auto-enabled).               ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void idempotentProducer() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: IDEMPOTENT PRODUCER                                   ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Problem: Producer sends message → network timeout → retries.   ║
        ║  Did the broker actually get the first attempt? Maybe yes,       ║
        ║  and now it's duplicated.                                        ║
        ║                                                                  ║
        ║  Solution: enable.idempotence=true (default since Kafka 3.0)    ║
        ║  → Broker assigns a Producer ID (PID)                           ║
        ║  → Each message gets a sequence number per (PID, partition)      ║
        ║  → If broker sees same (PID, seq) again → REJECTS the dup       ║
        ║                                                                  ║
        ║  SCOPE: Protects against NETWORK retries within a session.      ║
        ║  Does NOT deduplicate application-level resends.                 ║
        ║  For that, use transactional producer or app-level dedup.        ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void retryDLQ() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: RETRY TOPIC + DEAD LETTER QUEUE (DLQ)                 ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka has NO built-in DLQ (unlike SQS). You implement it.      ║
        ║                                                                  ║
        ║  Pattern:                                                        ║
        ║  ┌──────────┐    ┌────────────┐    ┌──────────┐                ║
        ║  │ source   │───→│ consumer   │───→│ retry    │                ║
        ║  │ topic    │    │ (process)  │    │ topic    │                ║
        ║  └──────────┘    └──────┬─────┘    └────┬─────┘                ║
        ║                         │✓              │ still fails            ║
        ║                    processed       ┌────▼─────┐                ║
        ║                                    │   DLQ    │                ║
        ║                                    │  topic   │                ║
        ║                                    └──────────┘                ║
        ║                                                                  ║
        ║  Headers carry: retry-count, original-topic, error-reason.      ║
        ║  DLQ messages are investigated manually or by alerting.          ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void multipleGroups() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: MULTIPLE INDEPENDENT CONSUMER GROUPS                   ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka's killer feature vs traditional message queues:           ║
        ║                                                                  ║
        ║           ┌─── Group A (OrderService) ──→ gets ALL msgs         ║
        ║  Topic ──┤                                                       ║
        ║           └─── Group B (Analytics)    ──→ gets ALL msgs         ║
        ║                                                                  ║
        ║  Each group has its OWN committed offset per partition.          ║
        ║  Group A at offset 100 while Group B at offset 50.              ║
        ║                                                                  ║
        ║  SQS/RabbitMQ: message consumed → deleted → one consumer.       ║
        ║  Kafka: messages retained → unlimited independent readers.      ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void clusterInspection() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CLUSTER, BROKERS, ISR & REPLICATION                   ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Broker = single Kafka server process.                           ║
        ║  Cluster = group of brokers coordinated by a controller.         ║
        ║                                                                  ║
        ║  Each partition has:                                             ║
        ║  • Leader — handles ALL reads/writes                             ║
        ║  • Replicas — full list of brokers holding the partition         ║
        ║  • ISR (In-Sync Replicas) — replicas caught up with leader      ║
        ║                                                                  ║
        ║  ISR shrinks when follower falls behind (network/disk issue).   ║
        ║  If ISR < min.insync.replicas → producer with acks=all fails.   ║
        ║                                                                  ║
        ║  Admin API lets you inspect all of this programmatically.        ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void headOfLineBlocking() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: HEAD-OF-LINE BLOCKING (OFFSET-BASED ACK)              ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka commits OFFSETS, not individual message acks.             ║
        ║  Committing offset=5 means "I processed 0,1,2,3,4"             ║
        ║                                                                  ║
        ║  If message 3 fails but 4,5,6 succeed:                          ║
        ║  You CAN'T commit offset=7 because that implies 3 was done.     ║
        ║  Messages 4-9 are BLOCKED behind the failed message.            ║
        ║                                                                  ║
        ║     offset: 0  1  2  [3]  4  5  6  7  8  9                     ║
        ║             ✓  ✓  ✓   ☠   ⏸  ⏸  ⏸  ⏸  ⏸  ⏸                  ║
        ║                                                                  ║
        ║  This is the fundamental trade-off of Kafka's design:            ║
        ║  • SQS: per-message visibility → no HOL blocking                │
        ║  • Kafka: offset-based → ordering + throughput, but HOL risk    │
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void consumeTransactional() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: CONSUMING TRANSACTIONAL MESSAGES                       ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Default: isolation.level=read_uncommitted → see everything.    ║
        ║  Set: isolation.level=read_committed → only see committed tx.   ║
        ║                                                                  ║
        ║  Aborted transaction messages are on disk but invisible.         ║
        ║  This ensures consumers see a CONSISTENT view.                   ║
        ║                                                                  ║
        ║  Producer (transaction):                                         ║
        ║    beginTransaction → send A → send B → commitTransaction       ║
        ║                                                                  ║
        ║  Consumer (read_committed):                                      ║
        ║    Sees A and B ONLY after commit. Never sees aborted.           ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void clusterTopology() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: MULTI-BROKER CLUSTER TOPOLOGY                         ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  You have 7 brokers running:                                     ║
        ║  • Brokers 1-3: combined broker + controller (KRaft quorum)      ║
        ║  • Brokers 4-7: pure brokers (data only)                         ║
        ║                                                                  ║
        ║  The Controller Quorum (Raft) runs on brokers 1-3.               ║
        ║  One of them is the ACTIVE controller (manages metadata,         ║
        ║  leader elections, ISR changes).                                  ║
        ║                                                                  ║
        ║  Topics have RF=3: each partition exists on 3 different brokers. ║
        ║  Leaders are spread across all 7 brokers for load balancing.     ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void urpCheck() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: URP (UNDER-REPLICATED PARTITIONS)                     ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  URP = partitions where ISR count < total replica count.         ║
        ║  It means some followers have fallen behind the leader.          ║
        ║                                                                  ║
        ║  URP = 0  → ALL replicas in sync → healthy                      ║
        ║  URP > 0  → some replicas behind → data durability at risk      ║
        ║                                                                  ║
        ║  This is THE #1 monitoring metric in production Kafka.           ║
        ║  If URP stays > 0 for minutes → investigate immediately.         ║
        ║                                                                  ║
        ║  Common causes: broker down, slow disk, GC pause, network.      ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void failureSimulation() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: BROKER FAILURE + LEADER ELECTION                       ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  What happens when a broker dies:                                ║
        ║                                                                  ║
        ║  1. Controller detects broker heartbeat timeout                   ║
        ║  2. For each partition led by dead broker:                        ║
        ║     → Controller picks new leader from ISR (milliseconds)        ║
        ║     → ISR shrinks (dead broker removed)                          ║
        ║  3. Producers/consumers discover new leaders via metadata         ║
        ║  4. URP increases (ISR < replicas for affected partitions)       ║
        ║                                                                  ║
        ║  With RF=3 + min.ISR=2:                                          ║
        ║  → 1 broker down: still works (2 ISR members remain)            ║
        ║  → 2 brokers down: producers FAIL (ISR < min.ISR)               ║
        ║                                                                  ║
        ║  This simulation kills a leader, shows election, then recovers.  ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    public static void leaderDistribution() {
        print("""
        ╔══════════════════════════════════════════════════════════════════╗
        ║  CONCEPT: LEADER DISTRIBUTION ACROSS BROKERS                     ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║                                                                  ║
        ║  Kafka spreads partition leaders across all brokers.              ║
        ║  This ensures no single broker handles all the write load.       ║
        ║                                                                  ║
        ║  With 7 brokers and ~30 partitions (RF=3):                       ║
        ║  → Each broker holds ~13 replicas (30×3 / 7 ≈ 13)              ║
        ║  → Each broker leads ~4 partitions (30 / 7 ≈ 4)               ║
        ║                                                                  ║
        ║  Uneven distribution = "hot broker" problem.                     ║
        ║  Kafka has preferred leader election to rebalance.               ║
        ║                                                                  ║
        ╚══════════════════════════════════════════════════════════════════╝
        """);
    }

    private static void print(String text) {
        System.out.println(text);
    }
}
