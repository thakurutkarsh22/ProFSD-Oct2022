package com.demo.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaProducerDemo {

    private final KafkaProducer<String, String> producer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public KafkaProducerDemo() {
        this.producer = new KafkaProducer<>(KafkaConfig.producerProps());
    }

    // ──────────────────────────────────────────────────────────────
    //  1) Send single message with key (shows partition routing)
    // ──────────────────────────────────────────────────────────────
    public void sendSingleWithKey() {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "customerId", "CUST-42",
                "amount", 129.99,
                "currency", "USD",
                "timestamp", Instant.now().toString()
        );
        String value = gson.toJson(order);

        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_ORDERS, orderId, value);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("  ✗ Send failed: " + exception.getMessage());
            } else {
                System.out.printf("  ✓ Sent to topic=%s  partition=%d  offset=%d  key=%s%n",
                        metadata.topic(), metadata.partition(), metadata.offset(), orderId);
                System.out.printf("    hash(\"%s\") %% 4 partitions = partition %d%n",
                        orderId, metadata.partition());
            }
        });
        producer.flush();
    }

    // ──────────────────────────────────────────────────────────────
    //  2) Send multiple messages with SAME key (prove ordering)
    // ──────────────────────────────────────────────────────────────
    public void sendOrderedMessages() {
        String orderId = "ORD-SAME-KEY";
        String[] statuses = {"CREATED", "PAYMENT_RECEIVED", "PROCESSING", "SHIPPED", "DELIVERED"};

        System.out.printf("  Sending 5 events for key=\"%s\" — all should land in SAME partition:%n%n", orderId);

        for (int i = 0; i < statuses.length; i++) {
            Map<String, Object> event = Map.of(
                    "orderId", orderId,
                    "status", statuses[i],
                    "sequence", i + 1,
                    "timestamp", Instant.now().toString()
            );

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC_ORDERS, orderId, gson.toJson(event));

            try {
                RecordMetadata metadata = producer.send(record).get();
                System.out.printf("  [%d/5] status=%-20s → partition=%d  offset=%d%n",
                        i + 1, statuses[i], metadata.partition(), metadata.offset());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }
        System.out.println("\n  ✓ All 5 events in same partition → consumer will see them IN ORDER.");
    }

    // ──────────────────────────────────────────────────────────────
    //  3) Send with headers (metadata without touching the value)
    // ──────────────────────────────────────────────────────────────
    public void sendWithHeaders() {
        String orderId = "ORD-HDR-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "amount", 250.00
        );

        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_ORDERS, orderId, gson.toJson(order));

        record.headers()
                .add(new RecordHeader("source", "checkout-service".getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("event-type", "ORDER_CREATED".getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("correlation-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("priority", "HIGH".getBytes(StandardCharsets.UTF_8)));

        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                System.out.printf("  ✓ Sent with 4 headers → partition=%d  offset=%d%n",
                        metadata.partition(), metadata.offset());
                System.out.println("    Headers: source=checkout-service, event-type=ORDER_CREATED,");
                System.out.println("             correlation-id=<uuid>, priority=HIGH");
                System.out.println("    → Open Kafka UI → Messages tab → click message → see headers");
            }
        });
        producer.flush();
    }

    // ──────────────────────────────────────────────────────────────
    //  4) Send to specific partition (bypass key hashing)
    // ──────────────────────────────────────────────────────────────
    public void sendToSpecificPartition() {
        System.out.println("  Sending one message to each of the 4 partitions of '" + KafkaConfig.TOPIC_ORDERS + "':\n");

        for (int partition = 0; partition < 4; partition++) {
            String key = "manual-p" + partition;
            String value = gson.toJson(Map.of(
                    "message", "Explicitly sent to partition " + partition,
                    "timestamp", Instant.now().toString()
            ));

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC_ORDERS, partition, key, value);

            try {
                RecordMetadata metadata = producer.send(record).get();
                System.out.printf("  ✓ partition=%d  offset=%d  key=%s%n",
                        metadata.partition(), metadata.offset(), key);
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }
        System.out.println("\n  → Open Kafka UI → Topic 'demo-orders' → see messages spread across partitions");
    }

    // ──────────────────────────────────────────────────────────────
    //  5) Fire-and-forget (acks=0) vs safe send (acks=all)
    // ──────────────────────────────────────────────────────────────
    public void compareAckModes() {
        System.out.println("  Sending 100 messages with acks=0 (fire-and-forget)...\n");

        long startFire = System.currentTimeMillis();
        try (KafkaProducer<String, String> fastProducer = new KafkaProducer<>(KafkaConfig.producerPropsFireAndForget())) {
            for (int i = 0; i < 100; i++) {
                fastProducer.send(new ProducerRecord<>(KafkaConfig.TOPIC_HIGHVOLUME, null,
                        "{\"click\":" + i + ",\"ts\":\"" + Instant.now() + "\"}"));
            }
            fastProducer.flush();
        }
        long fireDuration = System.currentTimeMillis() - startFire;

        System.out.println("  Sending 100 messages with acks=all (durable)...\n");

        long startSafe = System.currentTimeMillis();
        AtomicInteger acked = new AtomicInteger(0);
        for (int i = 0; i < 100; i++) {
            producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_HIGHVOLUME, null,
                            "{\"click\":" + i + ",\"ts\":\"" + Instant.now() + "\"}"),
                    (metadata, ex) -> { if (ex == null) acked.incrementAndGet(); });
        }
        producer.flush();
        long safeDuration = System.currentTimeMillis() - startSafe;

        System.out.println("  ┌──────────────────────────────────────────────┐");
        System.out.println("  │ Results:                                     │");
        System.out.printf("  │   acks=0   → %4d ms  (no guarantee)         │%n", fireDuration);
        System.out.printf("  │   acks=all → %4d ms  (%d/100 acked)        │%n", safeDuration, acked.get());
        System.out.println("  │                                              │");
        System.out.println("  │   acks=0 is faster but can LOSE messages.   │");
        System.out.println("  │   acks=all waits for broker + ISR ack.      │");
        System.out.println("  └──────────────────────────────────────────────┘");
    }

    // ──────────────────────────────────────────────────────────────
    //  6) Null-key messages (round robin / sticky partitioning)
    // ──────────────────────────────────────────────────────────────
    public void sendNullKeyMessages() {
        System.out.println("  Sending 20 messages with key=null (sticky partitioning):\n");

        Map<Integer, Integer> partitionCounts = new TreeMap<>();
        for (int i = 0; i < 20; i++) {
            try {
                RecordMetadata metadata = producer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_ORDERS, null, null,
                        "{\"event\":\"click\",\"seq\":" + i + "}")).get();
                partitionCounts.merge(metadata.partition(), 1, Integer::sum);
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }

        System.out.println("  Distribution across partitions:");
        for (var entry : partitionCounts.entrySet()) {
            String bar = "█".repeat(entry.getValue());
            System.out.printf("    Partition %d: %s (%d msgs)%n", entry.getKey(), bar, entry.getValue());
        }
        System.out.println("\n  Note: With sticky partitioning, messages batch to one partition then rotate.");
        System.out.println("  You may see uneven distribution in small batches — this is normal.");
    }

    // ──────────────────────────────────────────────────────────────
    //  7) Send to compacted topic (log compaction demo)
    // ──────────────────────────────────────────────────────────────
    public void sendToCompactedTopic() {
        System.out.println("  Sending user profile updates (same key, different values):\n");

        String[][] updates = {
                {"user-alice", "{\"name\":\"Alice\",\"email\":\"alice@v1.com\",\"version\":1}"},
                {"user-bob",   "{\"name\":\"Bob\",\"email\":\"bob@v1.com\",\"version\":1}"},
                {"user-alice", "{\"name\":\"Alice\",\"email\":\"alice@v2.com\",\"version\":2}"},
                {"user-charlie","{\"name\":\"Charlie\",\"email\":\"charlie@v1.com\",\"version\":1}"},
                {"user-bob",   "{\"name\":\"Bob\",\"email\":\"bob@v2.com\",\"version\":2}"},
                {"user-alice", "{\"name\":\"Alice Smith\",\"email\":\"alice@v3.com\",\"version\":3}"},
                {"user-bob",   "{\"name\":\"Bob Jones\",\"email\":\"bob@v3.com\",\"version\":3}"},
        };

        for (String[] update : updates) {
            try {
                RecordMetadata metadata = producer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_COMPACTED, update[0], update[1])).get();
                System.out.printf("  ✓ key=%-14s → partition=%d  offset=%d  value=%s%n",
                        update[0], metadata.partition(), metadata.offset(),
                        update[1].substring(0, Math.min(50, update[1].length())) + "...");
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }

        System.out.println("\n  7 messages sent but only 3 unique keys (alice, bob, charlie).");
        System.out.println("  After compaction runs, old versions will be removed.");
        System.out.println("  → Kafka UI → Topic 'demo-user-profiles' → Messages → watch over time");
    }

    // ──────────────────────────────────────────────────────────────
    //  8) Tombstone (delete via null value) on compacted topic
    // ──────────────────────────────────────────────────────────────
    public void sendTombstone() {
        String key = "user-charlie";
        System.out.printf("  Sending TOMBSTONE for key=\"%s\" (value=null)%n", key);
        System.out.println("  This marks the key for deletion during log compaction.\n");

        ProducerRecord<String, String> tombstone = new ProducerRecord<>(
                KafkaConfig.TOPIC_COMPACTED, key, null);

        producer.send(tombstone, (metadata, exception) -> {
            if (exception == null) {
                System.out.printf("  ✓ Tombstone sent → partition=%d  offset=%d%n",
                        metadata.partition(), metadata.offset());
                System.out.println("    After compaction, ALL records for 'user-charlie' will be removed.");
                System.out.println("    The tombstone itself is removed after delete.retention.ms (24h default).");
            }
        });
        producer.flush();
    }

    // ──────────────────────────────────────────────────────────────
    //  9) Bulk send for consumer lag demo
    // ──────────────────────────────────────────────────────────────
    public void sendBulkForLagDemo(int count) {
        System.out.printf("  Sending %d messages to '%s' for lag demo...%n%n", count, KafkaConfig.TOPIC_ORDERS);

        long start = System.currentTimeMillis();
        AtomicInteger success = new AtomicInteger(0);

        for (int i = 0; i < count; i++) {
            String key = "order-" + (i % 100);
            String value = gson.toJson(Map.of(
                    "orderId", "BULK-" + i,
                    "amount", Math.random() * 500,
                    "timestamp", Instant.now().toString()
            ));
            producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_ORDERS, key, value),
                    (m, e) -> { if (e == null) success.incrementAndGet(); });
        }
        producer.flush();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("  ✓ Sent %d/%d messages in %d ms (%.0f msgs/sec)%n",
                success.get(), count, duration, count * 1000.0 / duration);
        System.out.println("  → Now run 'Consume with lag monitoring' to see consumer lag in action");
    }

    // ──────────────────────────────────────────────────────────────
    //  10) Transactional producer — exactly-once across partitions
    // ──────────────────────────────────────────────────────────────
    public void transactionalSend() {
        String txnId = "txn-demo-" + System.currentTimeMillis();
        System.out.printf("  Creating transactional producer (transactional.id=%s)%n%n", txnId);

        try (KafkaProducer<String, String> txnProducer = new KafkaProducer<>(KafkaConfig.transactionalProducerProps(txnId))) {
            txnProducer.initTransactions();

            // Successful transaction
            System.out.println("  ── Transaction 1: COMMIT (write to orders + payments atomically) ──\n");
            txnProducer.beginTransaction();
            try {
                String orderId = "TXN-ORD-" + UUID.randomUUID().toString().substring(0, 6);

                RecordMetadata m1 = txnProducer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_TXN_INPUT, orderId,
                        gson.toJson(Map.of("orderId", orderId, "action", "CREATE_ORDER")))).get();
                System.out.printf("  → Wrote to %s partition=%d offset=%d%n", m1.topic(), m1.partition(), m1.offset());

                RecordMetadata m2 = txnProducer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_TXN_OUTPUT, orderId,
                        gson.toJson(Map.of("orderId", orderId, "action", "DEBIT_PAYMENT")))).get();
                System.out.printf("  → Wrote to %s partition=%d offset=%d%n", m2.topic(), m2.partition(), m2.offset());

                txnProducer.commitTransaction();
                System.out.println("  ✓ Transaction COMMITTED — both messages visible atomically.\n");

            } catch (Exception e) {
                txnProducer.abortTransaction();
                System.out.println("  ✗ Transaction ABORTED: " + e.getMessage());
            }

            // Aborted transaction
            System.out.println("  ── Transaction 2: ABORT (simulate failure) ──\n");
            txnProducer.beginTransaction();
            try {
                txnProducer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_TXN_INPUT, "TXN-ABORT",
                        "{\"action\":\"THIS_WILL_BE_ABORTED\"}")).get();
                System.out.println("  → Wrote to input topic (but will abort)...");

                // Simulate failure
                throw new RuntimeException("Simulated payment service failure!");

            } catch (RuntimeException e) {
                txnProducer.abortTransaction();
                System.out.println("  ✗ Transaction ABORTED: " + e.getMessage());
                System.out.println("    The message above is invisible to read_committed consumers.");
            } catch (Exception e) {
                txnProducer.abortTransaction();
            }

            System.out.println("\n  KEY INSIGHT:");
            System.out.println("  • Committed tx: both messages visible atomically");
            System.out.println("  • Aborted tx: messages written but INVISIBLE to read_committed consumers");
            System.out.println("  • isolation.level=read_committed filters out aborted transaction messages");
            System.out.println("  → Kafka UI: check demo-txn-input — you'll see both, but consumer won't see aborted one");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  11) Idempotent producer — show duplicate detection
    // ──────────────────────────────────────────────────────────────
    public void idempotentProducerDemo() {
        System.out.println("  Demonstrating idempotent producer (enable.idempotence=true):\n");
        System.out.println("  This producer has a Producer ID (PID) assigned by the broker.");
        System.out.println("  Each message gets a sequence number per (PID, partition).");
        System.out.println("  If a message is retried, the broker detects the duplicate.\n");

        String key = "IDEMP-KEY";
        Map<String, Object> event = Map.of("orderId", "IDEMP-001", "action", "test-idempotence");
        String value = gson.toJson(event);

        System.out.println("  Sending 5 messages with the SAME content using idempotent producer:\n");

        Set<Long> offsets = new LinkedHashSet<>();
        for (int i = 0; i < 5; i++) {
            try {
                RecordMetadata metadata = producer.send(new ProducerRecord<>(
                        KafkaConfig.TOPIC_ORDERS, key, value)).get();
                offsets.add(metadata.offset());
                System.out.printf("  [%d] partition=%d  offset=%d%n", i + 1, metadata.partition(), metadata.offset());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }

        System.out.println("\n  All 5 messages got DIFFERENT offsets → they are 5 separate sends.");
        System.out.println("  Idempotence protects against NETWORK RETRIES, not application-level dupes.");
        System.out.println("\n  How it works internally:");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │  Producer sends: PID=100, seq=0 → broker accepts ✓          │");
        System.out.println("  │  Network timeout, producer retries: PID=100, seq=0           │");
        System.out.println("  │  Broker: \"I already have PID=100, seq=0\" → REJECTS dup ✗    │");
        System.out.println("  │                                                              │");
        System.out.println("  │  The 5 sends above had seq=0,1,2,3,4 → all unique → accepted│");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
    }

    public void close() {
        producer.close();
    }
}
