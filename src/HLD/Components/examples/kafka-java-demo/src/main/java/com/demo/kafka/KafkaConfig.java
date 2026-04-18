package com.demo.kafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class KafkaConfig {

    public static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9192,localhost:9292";

    public static final short REPLICATION_FACTOR = 3;
    public static final int   MIN_ISR            = 2;

    public static final String TOPIC_ORDERS       = "demo-orders";
    public static final String TOPIC_PAYMENTS     = "demo-payments";
    public static final String TOPIC_COMPACTED    = "demo-user-profiles";
    public static final String TOPIC_HIGHVOLUME   = "demo-clicks";
    public static final String TOPIC_RETRY        = "demo-orders-retry";
    public static final String TOPIC_DLQ          = "demo-orders-dlq";
    public static final String TOPIC_TXN_INPUT    = "demo-txn-input";
    public static final String TOPIC_TXN_OUTPUT   = "demo-txn-output";

    public static final String GROUP_ORDER_PROCESSING = "order-processing-group";
    public static final String GROUP_ANALYTICS        = "analytics-group";

    public static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        return props;
    }

    public static Properties producerPropsFireAndForget() {
        Properties props = producerProps();
        props.put(ProducerConfig.ACKS_CONFIG, "0");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        return props;
    }

    public static Properties transactionalProducerProps(String txnId) {
        Properties props = producerProps();
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, txnId);
        return props;
    }

    public static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return props;
    }

    public static void createTopics() {
        System.out.println("\n  Setting up topics (RF=" + REPLICATION_FACTOR + ", min.insync.replicas=" + MIN_ISR + ")...");

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS))) {

            int brokerCount = admin.describeCluster().nodes().get().size();
            System.out.printf("  Cluster has %d brokers.%n", brokerCount);

            short rf = (short) Math.min(REPLICATION_FACTOR, brokerCount);
            if (rf < REPLICATION_FACTOR) {
                System.out.printf("  ⚠ Only %d brokers available, using RF=%d instead of %d%n", brokerCount, rf, REPLICATION_FACTOR);
            }

            Set<String> existing = admin.listTopics().names().get();
            List<NewTopic> toCreate = new ArrayList<>();

            Map<String, String> minIsrConfig = Map.of("min.insync.replicas", String.valueOf(Math.min(MIN_ISR, rf)));

            if (!existing.contains(TOPIC_ORDERS)) {
                NewTopic t = new NewTopic(TOPIC_ORDERS, 6, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_PAYMENTS)) {
                NewTopic t = new NewTopic(TOPIC_PAYMENTS, 4, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_COMPACTED)) {
                NewTopic compacted = new NewTopic(TOPIC_COMPACTED, 3, rf);
                Map<String, String> compactConfig = new HashMap<>(minIsrConfig);
                compactConfig.put("cleanup.policy", "compact");
                compactConfig.put("min.cleanable.dirty.ratio", "0.01");
                compactConfig.put("segment.ms", "5000");
                compacted.configs(compactConfig);
                toCreate.add(compacted);
            }
            if (!existing.contains(TOPIC_HIGHVOLUME)) {
                NewTopic t = new NewTopic(TOPIC_HIGHVOLUME, 7, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_RETRY)) {
                NewTopic t = new NewTopic(TOPIC_RETRY, 3, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_DLQ)) {
                NewTopic t = new NewTopic(TOPIC_DLQ, 1, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_TXN_INPUT)) {
                NewTopic t = new NewTopic(TOPIC_TXN_INPUT, 3, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }
            if (!existing.contains(TOPIC_TXN_OUTPUT)) {
                NewTopic t = new NewTopic(TOPIC_TXN_OUTPUT, 3, rf);
                t.configs(minIsrConfig);
                toCreate.add(t);
            }

            if (!toCreate.isEmpty()) {
                admin.createTopics(toCreate).all().get();
                System.out.println("  ✓ Created topics: " + toCreate.stream().map(NewTopic::name).toList());
                System.out.println("  Waiting for partitions to propagate across brokers...");
                Thread.sleep(3000);
            } else {
                System.out.println("  ✓ All topics already exist.");
            }

            describeTopics(admin);

        } catch (ExecutionException | InterruptedException e) {
            System.err.println("  ✗ Failed to create topics: " + e.getMessage());
            System.err.println("    → Is Kafka running? Try: docker compose up -d");
            System.exit(1);
        }
    }

    private static void describeTopics(AdminClient admin) throws ExecutionException, InterruptedException {
        List<String> topics = List.of(TOPIC_ORDERS, TOPIC_PAYMENTS, TOPIC_COMPACTED, TOPIC_HIGHVOLUME,
                TOPIC_RETRY, TOPIC_DLQ, TOPIC_TXN_INPUT, TOPIC_TXN_OUTPUT);
        Map<String, TopicDescription> descriptions = admin.describeTopics(topics).allTopicNames().get();

        System.out.println("\n  ┌────────────────────────┬────────────┬────────────┬──────────┬──────────────────┐");
        System.out.println("  │ Topic                  │ Partitions │ Replicas   │ Min ISR  │ Cleanup Policy   │");
        System.out.println("  ├────────────────────────┼────────────┼────────────┼──────────┼──────────────────┤");

        for (var entry : descriptions.entrySet()) {
            TopicDescription desc = entry.getValue();
            String name = String.format("%-22s", desc.name());
            String parts = String.format("%-10d", desc.partitions().size());
            String replicas = String.format("%-10d", desc.partitions().get(0).replicas().size());
            String minIsr = String.format("%-8d", Math.min(MIN_ISR, desc.partitions().get(0).replicas().size()));
            String cleanup = desc.name().equals(TOPIC_COMPACTED) ? "compact" : "delete";
            System.out.printf("  │ %s │ %s │ %s │ %s │ %-16s │%n", name, parts, replicas, minIsr, cleanup);
        }
        System.out.println("  └────────────────────────┴────────────┴────────────┴──────────┴──────────────────┘");
    }
}
