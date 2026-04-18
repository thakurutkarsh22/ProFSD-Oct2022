package com.demo.kafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaConsumerDemo {

    // ──────────────────────────────────────────────────────────────
    //  1) Basic consume — read and manually commit
    // ──────────────────────────────────────────────────────────────
    public void consumeBasic(String topic, String groupId, int maxMessages) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupId))) {
            consumer.subscribe(List.of(topic));

            System.out.printf("  Consuming from '%s' (group=%s), waiting for messages...%n%n", topic, groupId);

            int received = 0;
            int emptyPolls = 0;

            while (received < maxMessages && emptyPolls < 5) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));

                if (records.isEmpty()) {
                    emptyPolls++;
                    System.out.println("  (no messages, poll " + emptyPolls + "/5)");
                    continue;
                }
                emptyPolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    received++;
                    System.out.printf("  [%d] partition=%-2d  offset=%-6d  key=%-20s  value=%s%n",
                            received, record.partition(), record.offset(),
                            record.key() == null ? "(null)" : record.key(),
                            truncate(record.value(), 60));

                    printHeaders(record);

                    if (received >= maxMessages) break;
                }

                consumer.commitSync();
                System.out.printf("  → Committed offsets for %d records%n%n", records.count());
            }

            System.out.printf("  ✓ Consumed %d messages total.%n", received);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  2) Consume and show partition assignment
    // ──────────────────────────────────────────────────────────────
    public void consumeShowAssignment(String topic, String groupId) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupId))) {
            consumer.subscribe(List.of(topic), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    System.out.println("  ⚡ REVOKED: " + partitions);
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    System.out.println("  ⚡ ASSIGNED: " + partitions);
                    for (TopicPartition tp : partitions) {
                        System.out.printf("    → This consumer now owns %s partition %d%n", tp.topic(), tp.partition());
                    }
                }
            });

            System.out.println("  Triggering partition assignment via first poll...\n");
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            System.out.printf("%n  Received %d messages in first poll.%n", records.count());

            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("    partition=%d  offset=%d  key=%s%n",
                        record.partition(), record.offset(), record.key());
            }
            if (!records.isEmpty()) consumer.commitSync();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  3) Competing consumers (multiple threads = simulates instances)
    // ──────────────────────────────────────────────────────────────
    public void competingConsumers(String topic, String groupId, int numConsumers) {
        System.out.printf("  Launching %d consumers in group '%s' for topic '%s'...%n", numConsumers, groupId, topic);
        System.out.println("  Each thread simulates a separate consumer instance.\n");

        AtomicBoolean running = new AtomicBoolean(true);
        Map<String, AtomicInteger> threadCounts = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(numConsumers);
        CountDownLatch startLatch = new CountDownLatch(numConsumers);

        for (int i = 0; i < numConsumers; i++) {
            final String consumerId = "consumer-" + (i + 1);
            threadCounts.put(consumerId, new AtomicInteger(0));

            executor.submit(() -> {
                try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupId))) {
                    consumer.subscribe(List.of(topic), new ConsumerRebalanceListener() {
                        @Override
                        public void onPartitionsRevoked(Collection<TopicPartition> p) {}
                        @Override
                        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                            System.out.printf("    [%s] assigned: %s%n", consumerId, partitions);
                        }
                    });

                    startLatch.countDown();
                    while (running.get()) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                        for (ConsumerRecord<String, String> record : records) {
                            threadCounts.get(consumerId).incrementAndGet();
                        }
                        if (!records.isEmpty()) consumer.commitSync();
                    }
                }
            });
        }

        try {
            startLatch.await(10, TimeUnit.SECONDS);
            System.out.println("\n  All consumers running. Processing for 10 seconds...\n");
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        running.set(false);
        executor.shutdown();
        try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        System.out.println("  ┌───────────────────────────────────────────┐");
        System.out.println("  │ Messages consumed per consumer:           │");
        System.out.println("  ├───────────────────────────────────────────┤");
        int total = 0;
        for (var entry : threadCounts.entrySet()) {
            int count = entry.getValue().get();
            total += count;
            String bar = "█".repeat(Math.min(count, 30));
            System.out.printf("  │ %-12s  %4d msgs  %s%n", entry.getKey(), count, bar);
        }
        System.out.println("  ├───────────────────────────────────────────┤");
        System.out.printf("  │ Total: %d messages                        %n", total);
        System.out.println("  └───────────────────────────────────────────┘");
        System.out.println("  Each consumer got different partitions — no duplicate processing!");
    }

    // ──────────────────────────────────────────────────────────────
    //  4) Seek to beginning — replay all messages
    // ──────────────────────────────────────────────────────────────
    public void replayFromBeginning(String topic, int maxMessages) {
        String replayGroup = "replay-group-" + System.currentTimeMillis();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(replayGroup))) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(100));

            System.out.println("  Seeking ALL partitions to beginning...\n");
            consumer.seekToBeginning(consumer.assignment());

            int received = 0;
            int emptyPolls = 0;
            Map<Integer, Integer> partitionCounts = new TreeMap<>();

            while (received < maxMessages && emptyPolls < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) { emptyPolls++; continue; }
                emptyPolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    received++;
                    partitionCounts.merge(record.partition(), 1, Integer::sum);
                    if (received <= 5) {
                        System.out.printf("  [%d] partition=%d  offset=%d  key=%s%n",
                                received, record.partition(), record.offset(), record.key());
                    }
                }
            }

            if (received > 5) System.out.printf("  ... and %d more messages%n", received - 5);
            System.out.printf("%n  ✓ Replayed %d messages from beginning.%n", received);
            System.out.println("  Distribution:");
            for (var entry : partitionCounts.entrySet()) {
                System.out.printf("    Partition %d: %d messages%n", entry.getKey(), entry.getValue());
            }
            System.out.println("\n  KEY INSIGHT: Kafka retains messages. You can replay anytime.");
            System.out.println("  Traditional queues (RabbitMQ/SQS) delete after consumption.");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  5) Show consumer lag (per-partition)
    // ──────────────────────────────────────────────────────────────
    public void showConsumerLag(String groupId) {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfig.BOOTSTRAP_SERVERS))) {

            Map<TopicPartition, OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata().get();

            if (offsets.isEmpty()) {
                System.out.println("  No committed offsets found for group '" + groupId + "'.");
                System.out.println("  Run a consumer first, then check lag.");
                return;
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(offsets.keySet().stream().collect(
                            java.util.stream.Collectors.toMap(
                                    tp -> tp,
                                    tp -> OffsetSpec.latest()
                            ))).all().get();

            System.out.printf("  Consumer Lag for group '%s':%n%n", groupId);
            System.out.println("  ┌──────────────────────┬───────────┬────────────┬──────────┬─────────────────┐");
            System.out.println("  │ Topic:Partition       │ Committed │ End Offset │ LAG      │ Visual          │");
            System.out.println("  ├──────────────────────┼───────────┼────────────┼──────────┼─────────────────┤");

            long totalLag = 0;
            for (var entry : offsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long committed = entry.getValue().offset();
                long endOffset = endOffsets.get(tp).offset();
                long lag = endOffset - committed;
                totalLag += lag;

                String lagBar = lag > 0 ? "█".repeat((int) Math.min(lag, 15)) : "✓ caught up";
                System.out.printf("  │ %-20s │ %9d │ %10d │ %8d │ %s%n",
                        tp.topic() + ":" + tp.partition(),
                        committed, endOffset, lag, lagBar);
            }

            System.out.println("  ├──────────────────────┼───────────┼────────────┼──────────┼─────────────────┤");
            System.out.printf("  │ TOTAL LAG             │           │            │ %8d │%n", totalLag);
            System.out.println("  └──────────────────────┴───────────┴────────────┴──────────┴─────────────────┘");

            if (totalLag > 0) {
                System.out.println("\n  ⚠ Consumer is behind. In production:");
                System.out.println("    → Scale consumers (add instances up to partition count)");
                System.out.println("    → Optimize processing speed");
                System.out.println("    → Check for hot partitions (uneven lag distribution)");
            }
        } catch (Exception e) {
            System.err.println("  ✗ Failed to get lag: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  6) Consume from compacted topic
    // ──────────────────────────────────────────────────────────────
    public void consumeCompactedTopic() {
        String group = "compaction-reader-" + System.currentTimeMillis();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(group))) {
            consumer.subscribe(List.of(KafkaConfig.TOPIC_COMPACTED));
            consumer.poll(Duration.ofMillis(100));
            consumer.seekToBeginning(consumer.assignment());

            System.out.println("  Reading ALL records from compacted topic 'demo-user-profiles':\n");

            Map<String, String> latestByKey = new LinkedHashMap<>();
            int total = 0;
            int emptyPolls = 0;

            while (emptyPolls < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) { emptyPolls++; continue; }
                emptyPolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    total++;
                    String val = record.value() == null ? "(TOMBSTONE — deleted)" : record.value();
                    System.out.printf("  [offset=%d] key=%-14s → %s%n",
                            record.offset(), record.key(), truncate(val, 55));
                    latestByKey.put(record.key(), val);
                }
            }

            System.out.printf("%n  Total records seen: %d%n", total);
            System.out.println("  Latest value per key (what matters after compaction):");
            System.out.println("  ┌────────────────────┬───────────────────────────────────────────┐");
            for (var entry : latestByKey.entrySet()) {
                System.out.printf("  │ %-18s │ %s%n", entry.getKey(), truncate(entry.getValue(), 40));
            }
            System.out.println("  └────────────────────┴───────────────────────────────────────────┘");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  7) Retry + DLQ pattern (failed messages go to retry → DLQ)
    // ──────────────────────────────────────────────────────────────
    public void retryWithDLQ(String sourceTopic, int maxRetries) {
        String group = "retry-dlq-demo-" + System.currentTimeMillis();
        System.out.printf("  Retry + DLQ Pattern (maxRetries=%d):%n", maxRetries);
        System.out.printf("  Source: %s → Retry: %s → DLQ: %s%n%n", sourceTopic, KafkaConfig.TOPIC_RETRY, KafkaConfig.TOPIC_DLQ);

        org.apache.kafka.clients.producer.KafkaProducer<String, String> retryProducer =
                new org.apache.kafka.clients.producer.KafkaProducer<>(KafkaConfig.producerProps());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(group))) {
            consumer.subscribe(List.of(sourceTopic));
            consumer.poll(Duration.ofMillis(100));
            consumer.seekToBeginning(consumer.assignment());

            int processed = 0, retried = 0, deadLettered = 0;
            int emptyPolls = 0;

            while (emptyPolls < 3 && processed < 30) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) { emptyPolls++; continue; }
                emptyPolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    processed++;
                    int retryCount = getRetryCount(record);
                    boolean shouldFail = record.key() != null && record.key().hashCode() % 3 == 0;

                    if (shouldFail && retryCount < maxRetries) {
                        org.apache.kafka.clients.producer.ProducerRecord<String, String> retryRecord =
                                new org.apache.kafka.clients.producer.ProducerRecord<>(
                                        KafkaConfig.TOPIC_RETRY, record.key(), record.value());
                        retryRecord.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                "retry-count", String.valueOf(retryCount + 1).getBytes(StandardCharsets.UTF_8)));
                        retryRecord.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                "original-topic", sourceTopic.getBytes(StandardCharsets.UTF_8)));
                        retryProducer.send(retryRecord);
                        retried++;
                        System.out.printf("  ↻ RETRY [%d/%d] key=%-15s → %s%n",
                                retryCount + 1, maxRetries, record.key(), KafkaConfig.TOPIC_RETRY);

                    } else if (shouldFail) {
                        org.apache.kafka.clients.producer.ProducerRecord<String, String> dlqRecord =
                                new org.apache.kafka.clients.producer.ProducerRecord<>(
                                        KafkaConfig.TOPIC_DLQ, record.key(), record.value());
                        dlqRecord.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                "error-reason", "Max retries exceeded".getBytes(StandardCharsets.UTF_8)));
                        dlqRecord.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                "original-topic", sourceTopic.getBytes(StandardCharsets.UTF_8)));
                        retryProducer.send(dlqRecord);
                        deadLettered++;
                        System.out.printf("  ☠ DLQ        key=%-15s → %s (retries exhausted)%n",
                                record.key(), KafkaConfig.TOPIC_DLQ);

                    } else {
                        System.out.printf("  ✓ OK         key=%-15s   processed successfully%n",
                                record.key() == null ? "(null)" : record.key());
                    }
                }
                consumer.commitSync();
            }

            retryProducer.flush();
            retryProducer.close();

            System.out.println("\n  ┌────────────────────────────────────────────────┐");
            System.out.printf("  │ Results:  Processed=%d  Retried=%d  DLQ=%d       %n", processed, retried, deadLettered);
            System.out.println("  ├────────────────────────────────────────────────┤");
            System.out.println("  │ Pattern:                                       │");
            System.out.println("  │   Source → process → ✓ success                │");
            System.out.println("  │                    → ✗ fail → Retry Topic     │");
            System.out.println("  │                               → ✗ fail again  │");
            System.out.println("  │                                 → DLQ Topic   │");
            System.out.println("  │                                                │");
            System.out.println("  │ In production: retry topic has delay (backoff) │");
            System.out.println("  │ DLQ messages are investigated manually or by   │");
            System.out.println("  │ an alerting pipeline.                           │");
            System.out.println("  └────────────────────────────────────────────────┘");
            System.out.println("  → Kafka UI: check demo-orders-retry and demo-orders-dlq topics");
        }
    }

    private int getRetryCount(ConsumerRecord<String, String> record) {
        for (Header h : record.headers()) {
            if ("retry-count".equals(h.key())) {
                return Integer.parseInt(new String(h.value(), StandardCharsets.UTF_8));
            }
        }
        return 0;
    }

    // ──────────────────────────────────────────────────────────────
    //  8) Multiple independent consumer groups on same topic
    // ──────────────────────────────────────────────────────────────
    public void multipleConsumerGroups(String topic) {
        System.out.println("  Two INDEPENDENT consumer groups reading the SAME topic '" + topic + "':");
        System.out.println("  Each group gets ALL messages independently.\n");

        String groupA = "group-A-" + System.currentTimeMillis();
        String groupB = "group-B-" + System.currentTimeMillis();

        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupA))) {
                consumer.subscribe(List.of(topic));
                consumer.poll(Duration.ofMillis(100));
                consumer.seekToBeginning(consumer.assignment());
                int emptyPolls = 0;
                while (emptyPolls < 3) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                    if (records.isEmpty()) { emptyPolls++; continue; }
                    emptyPolls = 0;
                    for (ConsumerRecord<String, String> r : records) countA.incrementAndGet();
                }
            }
            done.countDown();
        });

        executor.submit(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupB))) {
                consumer.subscribe(List.of(topic));
                consumer.poll(Duration.ofMillis(100));
                consumer.seekToBeginning(consumer.assignment());
                int emptyPolls = 0;
                while (emptyPolls < 3) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                    if (records.isEmpty()) { emptyPolls++; continue; }
                    emptyPolls = 0;
                    for (ConsumerRecord<String, String> r : records) countB.incrementAndGet();
                }
            }
            done.countDown();
        });

        try { done.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        executor.shutdown();

        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.printf("  │ Group A ('%s')%n", groupA);
        System.out.printf("  │   → Read %d messages%n", countA.get());
        System.out.printf("  │ Group B ('%s')%n", groupB);
        System.out.printf("  │   → Read %d messages%n", countB.get());
        System.out.println("  ├──────────────────────────────────────────────────────────┤");
        System.out.println("  │ BOTH groups got ALL messages (independent offsets).      │");
        System.out.println("  │                                                          │");
        System.out.println("  │ This is Kafka's killer feature vs traditional queues:    │");
        System.out.println("  │ • RabbitMQ/SQS: message consumed → gone                │");
        System.out.println("  │ • Kafka: multiple groups read independently              │");
        System.out.println("  │   e.g., OrderService + AnalyticsService + AuditService   │");
        System.out.println("  │   all consume from the same 'orders' topic.              │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
    }

    // ──────────────────────────────────────────────────────────────
    //  9) ISR & cluster inspection (Admin API)
    // ──────────────────────────────────────────────────────────────
    public void inspectCluster() {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfig.BOOTSTRAP_SERVERS))) {

            // Describe the cluster
            DescribeClusterResult cluster = admin.describeCluster();
            System.out.println("  ╔══════════════════════════════════════════════════════════╗");
            System.out.println("  ║              KAFKA CLUSTER INSPECTION                    ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════╝\n");

            System.out.printf("  Cluster ID:   %s%n", cluster.clusterId().get());
            System.out.printf("  Controller:   Node %d%n", cluster.controller().get().id());
            System.out.println("  Brokers:");
            for (org.apache.kafka.common.Node node : cluster.nodes().get()) {
                System.out.printf("    → Broker %d: %s:%d (rack=%s)%n",
                        node.id(), node.host(), node.port(),
                        node.rack() == null ? "none" : node.rack());
            }

            // Describe all topics with ISR details
            Set<String> topicNames = admin.listTopics().names().get();
            Map<String, TopicDescription> descriptions = admin.describeTopics(topicNames).allTopicNames().get();

            System.out.println("\n  ┌────────────────────────┬───────┬────────┬──────────┬──────────────────────────┐");
            System.out.println("  │ Topic                  │ Part  │ Leader │ Replicas │ ISR (In-Sync Replicas)   │");
            System.out.println("  ├────────────────────────┼───────┼────────┼──────────┼──────────────────────────┤");

            for (var entry : descriptions.entrySet()) {
                String topicName = entry.getKey();
                if (topicName.startsWith("__")) continue;
                for (var partInfo : entry.getValue().partitions()) {
                    String replicas = partInfo.replicas().stream()
                            .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
                    String isr = partInfo.isr().stream()
                            .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
                    String isrStatus = partInfo.isr().size() == partInfo.replicas().size() ? " ✓" : " ⚠ UNDER-REPLICATED";

                    System.out.printf("  │ %-22s │ %-5d │ %-6d │ [%-6s] │ [%-6s]%s%n",
                            partInfo.partition() == 0 ? topicName : "",
                            partInfo.partition(),
                            partInfo.leader().id(),
                            replicas, isr, isrStatus);
                }
            }
            System.out.println("  └────────────────────────┴───────┴────────┴──────────┴──────────────────────────┘");

            System.out.println("\n  KEY CONCEPTS:");
            System.out.println("  • Leader: handles all reads/writes for this partition");
            System.out.println("  • Replicas: brokers that have a copy of this partition");
            System.out.println("  • ISR: replicas caught up with leader (eligible for leader election)");
            System.out.println("  • Under-replicated: ISR < Replicas → follower fell behind → risk!");
            System.out.println("  • In single-broker setup, all ISR=[1] and replicas=[1]");

            // Show topic configs
            System.out.println("\n  Topic Configs (non-default):");
            System.out.println("  ────────────────────────────────────────────");
            java.util.Collection<org.apache.kafka.common.config.ConfigResource> configResources =
                    topicNames.stream()
                            .filter(t -> !t.startsWith("__"))
                            .map(t -> new org.apache.kafka.common.config.ConfigResource(
                                    org.apache.kafka.common.config.ConfigResource.Type.TOPIC, t))
                            .toList();

            Map<org.apache.kafka.common.config.ConfigResource, org.apache.kafka.clients.admin.Config> configs =
                    admin.describeConfigs(configResources).all().get();

            for (var entry : configs.entrySet()) {
                String topic = entry.getKey().name();
                boolean hasNonDefault = false;
                for (org.apache.kafka.clients.admin.ConfigEntry configEntry : entry.getValue().entries()) {
                    if (!configEntry.isDefault() && !configEntry.name().startsWith("__")) {
                        if (!hasNonDefault) {
                            System.out.printf("  %s:%n", topic);
                            hasNonDefault = true;
                        }
                        System.out.printf("    %s = %s%n", configEntry.name(), configEntry.value());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("  ✗ Failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  10) Head-of-line blocking (offset-based ack problem)
    // ──────────────────────────────────────────────────────────────
    public void headOfLineBlocking() {
        System.out.println("  Demonstrating HEAD-OF-LINE BLOCKING (Kafka's offset-based ack limitation)\n");

        String topic = KafkaConfig.TOPIC_ORDERS;
        String group = "hol-demo-" + System.currentTimeMillis();

        // First: produce 10 messages
        org.apache.kafka.clients.producer.KafkaProducer<String, String> prod =
                new org.apache.kafka.clients.producer.KafkaProducer<>(KafkaConfig.producerProps());
        String fixedKey = "HOL-KEY";
        for (int i = 0; i < 10; i++) {
            try {
                prod.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                        topic, fixedKey,
                        "{\"seq\":" + i + ",\"type\":\"" + (i == 3 ? "POISON" : "NORMAL") + "}")).get();
            } catch (Exception e) {
                System.err.println("  Failed to produce: " + e.getMessage());
            }
        }
        prod.flush();
        prod.close();
        System.out.println("  Produced 10 messages (msg[3] is a 'poison pill'):\n");

        // Consume and simulate failure on msg[3]
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(group))) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(100));

            Set<TopicPartition> assignment = consumer.assignment();
            for (TopicPartition tp : assignment) {
                long endOffset = consumer.endOffsets(Set.of(tp)).get(tp);
                consumer.seek(tp, Math.max(0, endOffset - 10));
            }

            int attempts = 0;
            boolean poisonHit = false;

            outer:
            while (attempts < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    boolean isPoison = record.value().contains("POISON");

                    if (isPoison && !poisonHit) {
                        poisonHit = true;
                        attempts++;
                        System.out.printf("  [offset=%d] ☠ POISON PILL — processing fails! (attempt %d/3)%n",
                                record.offset(), attempts);
                        System.out.println("    → Cannot commit this offset → messages 4-9 are BLOCKED.");
                        System.out.println("    → This is HEAD-OF-LINE BLOCKING.\n");
                        break;
                    } else {
                        System.out.printf("  [offset=%d] %s seq=%s%n",
                                record.offset(), isPoison ? "☠ SKIPPING POISON" : "✓",
                                record.value().contains("seq") ? record.value() : "?");
                    }
                }

                if (poisonHit && attempts < 3) {
                    break;
                }
            }
        }

        System.out.println("\n  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ HEAD-OF-LINE BLOCKING EXPLAINED:                             │");
        System.out.println("  │                                                              │");
        System.out.println("  │  offset:  0  1  2  [3]  4  5  6  7  8  9                   │");
        System.out.println("  │           ✓  ✓  ✓   ☠   ⏸  ⏸  ⏸  ⏸  ⏸  ⏸                │");
        System.out.println("  │                      ↑                                       │");
        System.out.println("  │               Can't skip! Offsets are sequential.             │");
        System.out.println("  │                                                              │");
        System.out.println("  │  Kafka commits OFFSETS, not individual messages.              │");
        System.out.println("  │  Committing offset=4 means \"I processed 0,1,2,3\"            │");
        System.out.println("  │  So if msg[3] fails → you can't commit → 4-9 blocked.       │");
        System.out.println("  │                                                              │");
        System.out.println("  │  SOLUTIONS:                                                  │");
        System.out.println("  │  1. Retry + DLQ: move poison pill to DLQ, skip it            │");
        System.out.println("  │  2. SQS: per-message visibility → no HOL blocking            │");
        System.out.println("  │  3. Track failed offsets in-memory, skip on retry             │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
    }

    // ──────────────────────────────────────────────────────────────
    //  11) Consume transactional messages (read_committed)
    // ──────────────────────────────────────────────────────────────
    public void consumeTransactional() {
        String group = "txn-consumer-" + System.currentTimeMillis();
        Properties props = KafkaConfig.consumerProps(group);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        System.out.println("  Consuming from transactional topics with isolation.level=read_committed:\n");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaConfig.TOPIC_TXN_INPUT, KafkaConfig.TOPIC_TXN_OUTPUT));
            consumer.poll(Duration.ofMillis(100));
            consumer.seekToBeginning(consumer.assignment());

            int total = 0;
            int emptyPolls = 0;
            Map<String, Integer> topicCounts = new TreeMap<>();

            while (emptyPolls < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) { emptyPolls++; continue; }
                emptyPolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    total++;
                    topicCounts.merge(record.topic(), 1, Integer::sum);
                    System.out.printf("  [%s] partition=%d  offset=%d  key=%s  value=%s%n",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), truncate(record.value(), 50));
                }
            }

            System.out.printf("%n  ✓ Read %d messages (read_committed: aborted tx messages filtered out)%n", total);
            for (var entry : topicCounts.entrySet()) {
                System.out.printf("    %s: %d messages%n", entry.getKey(), entry.getValue());
            }
            System.out.println("\n  KEY: Aborted transaction messages are physically on disk but invisible.");
            System.out.println("  → Kafka UI shows them because it reads at read_uncommitted level.");
        }
    }

    private void printHeaders(ConsumerRecord<String, String> record) {
        if (record.headers().toArray().length > 0) {
            StringBuilder sb = new StringBuilder("           headers: ");
            for (Header h : record.headers()) {
                sb.append(h.key()).append("=").append(new String(h.value(), StandardCharsets.UTF_8)).append("  ");
            }
            System.out.println(sb);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
