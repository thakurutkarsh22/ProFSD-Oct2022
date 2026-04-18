package com.demo.kafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterSimulation {

    // ──────────────────────────────────────────────────────────────
    //  Show full cluster topology — brokers, leaders, ISR, URP
    // ──────────────────────────────────────────────────────────────
    public void showClusterTopology() {
        try (AdminClient admin = createAdmin()) {
            DescribeClusterResult cluster = admin.describeCluster();
            Collection<Node> nodes = cluster.nodes().get();
            Node controller = cluster.controller().get();
            String clusterId = cluster.clusterId().get();

            System.out.println("  ╔══════════════════════════════════════════════════════════════════╗");
            System.out.println("  ║       7-BROKER KAFKA CLUSTER TOPOLOGY                            ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════════════╝\n");

            System.out.printf("  Cluster ID:   %s%n", clusterId);
            System.out.printf("  Controller:   Broker %d%n", controller.id());
            System.out.printf("  Broker Count: %d%n%n", nodes.size());

            System.out.println("  Brokers:");
            System.out.println("  ┌──────────┬──────────────────┬────────┬───────────────────┐");
            System.out.println("  │ Broker   │ Host             │ Port   │ Role              │");
            System.out.println("  ├──────────┼──────────────────┼────────┼───────────────────┤");
            for (Node node : nodes.stream().sorted(Comparator.comparingInt(Node::id)).toList()) {
                String role = node.id() <= 3 ? "broker+controller" : "broker";
                if (node.id() == controller.id()) role += " (ACTIVE CTRL)";
                System.out.printf("  │ %-8d │ %-16s │ %-6d │ %-17s │%n",
                        node.id(), node.host(), node.port(), role);
            }
            System.out.println("  └──────────┴──────────────────┴────────┴───────────────────┘");

            showPartitionDistribution(admin);

        } catch (Exception e) {
            System.err.println("  ✗ Failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Show URP (Under-Replicated Partitions) across all topics
    // ──────────────────────────────────────────────────────────────
    public void showURP() {
        try (AdminClient admin = createAdmin()) {
            Set<String> topicNames = admin.listTopics().names().get();
            topicNames.removeIf(t -> t.startsWith("__"));
            Map<String, TopicDescription> descriptions = admin.describeTopics(topicNames).allTopicNames().get();

            int totalPartitions = 0;
            int underReplicated = 0;
            List<String> urpDetails = new ArrayList<>();

            System.out.println("  ╔══════════════════════════════════════════════════════════════════╗");
            System.out.println("  ║       UNDER-REPLICATED PARTITIONS (URP) CHECK                    ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════════════╝\n");

            System.out.println("  ┌────────────────────────┬───────┬────────┬──────────┬──────────┬──────────┐");
            System.out.println("  │ Topic                  │ Part  │ Leader │ Replicas │ ISR      │ Status   │");
            System.out.println("  ├────────────────────────┼───────┼────────┼──────────┼──────────┼──────────┤");

            for (var entry : descriptions.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey)).toList()) {
                for (var partInfo : entry.getValue().partitions()) {
                    totalPartitions++;
                    String replicas = partInfo.replicas().stream()
                            .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
                    String isr = partInfo.isr().stream()
                            .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
                    boolean isUnderReplicated = partInfo.isr().size() < partInfo.replicas().size();
                    String status = isUnderReplicated ? "⚠ URP" : "✓ OK";

                    if (isUnderReplicated) {
                        underReplicated++;
                        urpDetails.add(String.format("    %s-P%d: replicas=[%s] isr=[%s] (missing: %d replica(s))",
                                entry.getKey(), partInfo.partition(), replicas, isr,
                                partInfo.replicas().size() - partInfo.isr().size()));
                    }

                    int leaderId = partInfo.leader() != null ? partInfo.leader().id() : -1;
                    System.out.printf("  │ %-22s │ %-5d │ %-6s │ [%-6s] │ [%-6s] │ %-8s │%n",
                            partInfo.partition() == 0 ? entry.getKey() : "",
                            partInfo.partition(),
                            leaderId == -1 ? "NONE!" : String.valueOf(leaderId),
                            replicas, isr, status);
                }
            }
            System.out.println("  └────────────────────────┴───────┴────────┴──────────┴──────────┴──────────┘");

            System.out.printf("%n  SUMMARY: %d / %d partitions under-replicated%n", underReplicated, totalPartitions);

            if (underReplicated == 0) {
                System.out.println("  ✓ ALL partitions healthy — URP = 0");
            } else {
                System.out.println("  ⚠ URP = " + underReplicated + " — DATA DURABILITY AT RISK!");
                System.out.println("\n  Under-replicated partitions:");
                urpDetails.forEach(System.out::println);
                System.out.println("\n  → A follower fell behind or a broker is down.");
                System.out.println("  → Check: docker compose ps (is a broker stopped?)");
            }
        } catch (Exception e) {
            System.err.println("  ✗ Failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Stop a broker (simulate failure via docker stop)
    // ──────────────────────────────────────────────────────────────
    public void stopBroker(int brokerId) {
        String container = "kafka-" + brokerId;
        System.out.printf("  Stopping broker %d (container: %s)...%n%n", brokerId, container);

        try {
            int exit = runDocker("stop", container);
            if (exit == 0) {
                System.out.printf("  ✓ Broker %d STOPPED. It is now offline.%n", brokerId);
                System.out.println("  → Partitions led by this broker will elect new leaders from ISR.");
                System.out.println("  → ISR for affected partitions will shrink.");
                System.out.println("  → URP will increase.");
                System.out.println("\n  Run option 24 (Show URP) to see the impact!");
            } else {
                System.err.printf("  ✗ Failed to stop broker %d%n", brokerId);
            }
        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Start a broker (recover from failure)
    // ──────────────────────────────────────────────────────────────
    public void startBroker(int brokerId) {
        String container = "kafka-" + brokerId;
        System.out.printf("  Starting broker %d (container: %s)...%n%n", brokerId, container);

        try {
            int exit = runDocker("start", container);
            if (exit == 0) {
                System.out.printf("  ✓ Broker %d STARTED. It is rejoining the cluster.%n", brokerId);
                System.out.println("  → Broker will fetch missed data from leaders.");
                System.out.println("  → Once caught up, it re-enters ISR for its partitions.");
                System.out.println("  → URP will decrease back to 0.");
                System.out.println("\n  Wait ~10 seconds, then run option 24 (Show URP) to verify recovery.");
            } else {
                System.err.printf("  ✗ Failed to start broker %d%n", brokerId);
            }
        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Full broker failure simulation (automated)
    // ──────────────────────────────────────────────────────────────
    public void fullFailureSimulation() {
        System.out.println("  ╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("  ║       BROKER FAILURE SIMULATION (RF=3, min.ISR=2)                ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════════════╝\n");

        try (AdminClient admin = createAdmin()) {
            // Step 1: Show initial state
            System.out.println("  ═══ STEP 1: INITIAL STATE (all 7 brokers healthy) ═══\n");
            int brokerCount = admin.describeCluster().nodes().get().size();
            System.out.printf("  Active brokers: %d%n", brokerCount);
            showLeadersForTopic(admin, KafkaConfig.TOPIC_ORDERS);

            // Step 2: Produce some messages
            System.out.println("\n  ═══ STEP 2: PRODUCE 50 MESSAGES (before failure) ═══\n");
            produceMessages(50, "pre-failure");

            // Step 3: Find which broker leads partition 0
            TopicDescription desc = admin.describeTopics(List.of(KafkaConfig.TOPIC_ORDERS))
                    .allTopicNames().get().get(KafkaConfig.TOPIC_ORDERS);
            int leaderBrokerId = desc.partitions().get(0).leader().id();
            System.out.printf("\n  Partition 0 leader is Broker %d. We will KILL this broker.\n", leaderBrokerId);

            // Step 4: Kill the leader
            System.out.printf("\n  ═══ STEP 3: KILLING BROKER %d (leader of partition 0) ═══%n%n", leaderBrokerId);
            int exit = runDocker("stop", "kafka-" + leaderBrokerId);
            if (exit != 0) {
                System.err.println("  ✗ Failed to stop broker. Aborting simulation.");
                return;
            }
            System.out.printf("  ✓ Broker %d is DOWN.%n", leaderBrokerId);

            // Step 5: Wait for leader election
            System.out.println("\n  Waiting 5 seconds for leader election...\n");
            Thread.sleep(5000);

            // Step 6: Show new state
            System.out.println("  ═══ STEP 4: AFTER FAILURE (new leaders elected) ═══\n");
            showLeadersForTopic(admin, KafkaConfig.TOPIC_ORDERS);

            // Step 7: Try producing while broker is down
            System.out.println("\n  ═══ STEP 5: PRODUCE WHILE BROKER IS DOWN ═══\n");
            System.out.println("  Producing 20 messages with acks=all (RF=3, min.ISR=2)...\n");
            produceMessages(20, "during-failure");
            System.out.println("  ✓ Production still works! 2 of 3 replicas are alive (meets min.ISR=2).\n");

            // Step 8: Show URP
            System.out.println("  ═══ STEP 6: URP STATUS ═══\n");
            showURPCompact(admin);

            // Step 9: Recover
            System.out.printf("\n  ═══ STEP 7: RECOVERING BROKER %d ═══%n%n", leaderBrokerId);
            runDocker("start", "kafka-" + leaderBrokerId);
            System.out.printf("  ✓ Broker %d restarted. Waiting 15 seconds for ISR to restore...%n", leaderBrokerId);

            Thread.sleep(15000);

            // Step 10: Show recovered state
            System.out.println("\n  ═══ STEP 8: AFTER RECOVERY ═══\n");
            showLeadersForTopic(admin, KafkaConfig.TOPIC_ORDERS);
            showURPCompact(admin);

            System.out.println("\n  ╔══════════════════════════════════════════════════════════════════╗");
            System.out.println("  ║  SIMULATION COMPLETE                                             ║");
            System.out.println("  ╠══════════════════════════════════════════════════════════════════╣");
            System.out.println("  ║  What happened:                                                  ║");
            System.out.println("  ║  1. Killed the leader of partition 0                             ║");
            System.out.println("  ║  2. Kafka elected a new leader from ISR (milliseconds)           ║");
            System.out.println("  ║  3. URP increased (ISR shrunk from 3 to 2)                       ║");
            System.out.println("  ║  4. Producers kept working (min.ISR=2 was met)                   ║");
            System.out.println("  ║  5. Broker recovered, caught up, re-entered ISR                  ║");
            System.out.println("  ║  6. URP returned to 0 — full health restored                     ║");
            System.out.println("  ║                                                                  ║");
            System.out.println("  ║  This is why Kafka uses RF=3 + min.ISR=2 + acks=all:             ║");
            System.out.println("  ║  → Survives 1 broker failure with ZERO data loss                 ║");
            System.out.println("  ║  → ZERO downtime (new leader elected instantly)                   ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("  ✗ Simulation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Show partition leader distribution across brokers
    // ──────────────────────────────────────────────────────────────
    public void showLeaderDistribution() {
        try (AdminClient admin = createAdmin()) {
            showPartitionDistribution(admin);
        } catch (Exception e) {
            System.err.println("  ✗ Failed: " + e.getMessage());
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private void showPartitionDistribution(AdminClient admin) throws Exception {
        Set<String> topicNames = admin.listTopics().names().get();
        topicNames.removeIf(t -> t.startsWith("__"));
        Map<String, TopicDescription> descriptions = admin.describeTopics(topicNames).allTopicNames().get();

        Map<Integer, Integer> leaderCounts = new TreeMap<>();
        Map<Integer, Integer> replicaCounts = new TreeMap<>();

        for (var entry : descriptions.values()) {
            for (var part : entry.partitions()) {
                if (part.leader() != null) {
                    leaderCounts.merge(part.leader().id(), 1, Integer::sum);
                }
                for (Node replica : part.replicas()) {
                    replicaCounts.merge(replica.id(), 1, Integer::sum);
                }
            }
        }

        System.out.println("\n  Partition Distribution Across Brokers:");
        System.out.println("  ┌──────────┬────────────────────┬────────────────────┐");
        System.out.println("  │ Broker   │ Leader Partitions  │ Total Replicas     │");
        System.out.println("  ├──────────┼────────────────────┼────────────────────┤");

        for (int brokerId : replicaCounts.keySet()) {
            int leaders = leaderCounts.getOrDefault(brokerId, 0);
            int replicas = replicaCounts.getOrDefault(brokerId, 0);
            String leaderBar = "█".repeat(Math.min(leaders, 20));
            String replicaBar = "░".repeat(Math.min(replicas, 20));
            System.out.printf("  │ %-8d │ %3d %s%n", brokerId, leaders, leaderBar);
            System.out.printf("  │          │ %3d %s │%n", replicas, replicaBar);
        }
        System.out.println("  └──────────┴────────────────────┴────────────────────┘");
        System.out.println("  █ = leader partitions (handles reads/writes)");
        System.out.println("  ░ = total replicas on that broker (includes followers)");
    }

    private void showLeadersForTopic(AdminClient admin, String topicName) throws Exception {
        TopicDescription desc = admin.describeTopics(List.of(topicName)).allTopicNames().get().get(topicName);

        System.out.printf("  Topic '%s' (%d partitions, RF=%d):%n%n",
                topicName, desc.partitions().size(), desc.partitions().get(0).replicas().size());

        for (var part : desc.partitions()) {
            String replicas = part.replicas().stream()
                    .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
            String isr = part.isr().stream()
                    .map(n -> String.valueOf(n.id())).reduce((a, b) -> a + "," + b).orElse("-");
            int leaderId = part.leader() != null ? part.leader().id() : -1;
            boolean isUnderReplicated = part.isr().size() < part.replicas().size();

            System.out.printf("    P%d → Leader: Broker %-2s  Replicas: [%s]  ISR: [%s] %s%n",
                    part.partition(),
                    leaderId == -1 ? "NONE" : String.valueOf(leaderId),
                    replicas, isr,
                    isUnderReplicated ? "⚠ URP" : "✓");
        }
    }

    private void showURPCompact(AdminClient admin) throws Exception {
        Set<String> topicNames = admin.listTopics().names().get();
        topicNames.removeIf(t -> t.startsWith("__"));
        Map<String, TopicDescription> descriptions = admin.describeTopics(topicNames).allTopicNames().get();

        int total = 0, urp = 0;
        for (var desc : descriptions.values()) {
            for (var part : desc.partitions()) {
                total++;
                if (part.isr().size() < part.replicas().size()) urp++;
            }
        }

        if (urp == 0) {
            System.out.printf("  ✓ URP = 0 / %d partitions — ALL HEALTHY%n", total);
        } else {
            System.out.printf("  ⚠ URP = %d / %d partitions — UNDER-REPLICATED%n", urp, total);
        }
    }

    private void produceMessages(int count, String prefix) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(KafkaConfig.producerProps())) {
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            long start = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                String key = prefix + "-" + (i % 20);
                String value = "{\"seq\":" + i + ",\"ts\":\"" + Instant.now() + "\"}";
                try {
                    RecordMetadata m = producer.send(new ProducerRecord<>(
                            KafkaConfig.TOPIC_ORDERS, key, value)).get();
                    success.incrementAndGet();
                    if (i < 3) {
                        System.out.printf("    [%d] → partition=%d offset=%d (leader=Broker ?)%n",
                                i, m.partition(), m.offset());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    if (failed.get() <= 2) {
                        System.out.printf("    [%d] ✗ FAILED: %s%n", i, e.getMessage());
                    }
                }
            }
            long duration = System.currentTimeMillis() - start;
            if (count > 3) System.out.printf("    ... (%d more)%n", count - 3);
            System.out.printf("  Sent %d/%d messages in %d ms (failed: %d)%n",
                    success.get(), count, duration, failed.get());
        }
    }

    private int runDocker(String action, String container) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", action, container)
                .inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }

    private AdminClient createAdmin() {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfig.BOOTSTRAP_SERVERS,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000));
    }
}
