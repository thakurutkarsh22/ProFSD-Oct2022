package com.demo.kafka;

import java.util.Scanner;

public class KafkaPlayground {

    public static void main(String[] args) {
        System.out.println("""
        
        ╔══════════════════════════════════════════════════════════════╗
        ║              KAFKA INTERACTIVE PLAYGROUND                     ║
        ║                                                              ║
        ║  Real Kafka broker running locally via Docker.               ║
        ║  Open Kafka UI at: http://localhost:8080                     ║
        ║                                                              ║
        ║  Every operation explains the concept, then runs it live.    ║
        ╚══════════════════════════════════════════════════════════════╝
        """);

        KafkaConfig.createTopics();

        KafkaProducerDemo producerDemo = new KafkaProducerDemo();
        KafkaConsumerDemo consumerDemo = new KafkaConsumerDemo();
        ClusterSimulation clusterSim = new ClusterSimulation();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printMenu();
            System.out.print("\n  Enter choice: ");
            String input = scanner.nextLine().trim();

            if (input.equals("0")) {
                producerDemo.close();
                System.out.println("\n  Goodbye! Don't forget: docker compose down");
                break;
            }

            System.out.println();

            try {
                switch (input) {
                    // ── PRODUCE ──
                    case "1" -> {
                        ConceptExplainer.sendWithKey();
                        producerDemo.sendSingleWithKey();
                    }
                    case "2" -> {
                        ConceptExplainer.sendOrdered();
                        producerDemo.sendOrderedMessages();
                    }
                    case "3" -> {
                        ConceptExplainer.sendHeaders();
                        producerDemo.sendWithHeaders();
                    }
                    case "4" -> {
                        ConceptExplainer.sendToPartition();
                        producerDemo.sendToSpecificPartition();
                    }
                    case "5" -> {
                        ConceptExplainer.compareAcks();
                        producerDemo.compareAckModes();
                    }
                    case "6" -> {
                        ConceptExplainer.nullKeys();
                        producerDemo.sendNullKeyMessages();
                    }
                    case "7" -> {
                        ConceptExplainer.compactedTopic();
                        producerDemo.sendToCompactedTopic();
                    }
                    case "8" -> {
                        ConceptExplainer.tombstone();
                        producerDemo.sendTombstone();
                    }
                    case "9" -> {
                        ConceptExplainer.bulkSend();
                        System.out.print("  How many messages? [1000]: ");
                        String countStr = scanner.nextLine().trim();
                        int count = countStr.isEmpty() ? 1000 : Integer.parseInt(countStr);
                        producerDemo.sendBulkForLagDemo(count);
                    }

                    // ── CONSUME ──
                    case "10" -> {
                        ConceptExplainer.basicConsume();
                        consumerDemo.consumeBasic(KafkaConfig.TOPIC_ORDERS,
                                KafkaConfig.GROUP_ORDER_PROCESSING, 20);
                    }
                    case "11" -> {
                        ConceptExplainer.partitionAssignment();
                        consumerDemo.consumeShowAssignment(KafkaConfig.TOPIC_ORDERS,
                                "assignment-demo-" + System.currentTimeMillis());
                    }
                    case "12" -> {
                        ConceptExplainer.competingConsumers();
                        System.out.print("  How many consumers? [3]: ");
                        String numStr = scanner.nextLine().trim();
                        int num = numStr.isEmpty() ? 3 : Integer.parseInt(numStr);
                        consumerDemo.competingConsumers(KafkaConfig.TOPIC_ORDERS,
                                "competing-" + System.currentTimeMillis(), num);
                    }
                    case "13" -> {
                        ConceptExplainer.replay();
                        consumerDemo.replayFromBeginning(KafkaConfig.TOPIC_ORDERS, 50);
                    }
                    case "14" -> {
                        ConceptExplainer.consumerLag();
                        System.out.print("  Consumer group [" + KafkaConfig.GROUP_ORDER_PROCESSING + "]: ");
                        String group = scanner.nextLine().trim();
                        if (group.isEmpty()) group = KafkaConfig.GROUP_ORDER_PROCESSING;
                        consumerDemo.showConsumerLag(group);
                    }
                    case "15" -> {
                        ConceptExplainer.compactedTopic();
                        consumerDemo.consumeCompactedTopic();
                    }

                    // ── ADVANCED ──
                    case "16" -> {
                        ConceptExplainer.transactionalProducer();
                        producerDemo.transactionalSend();
                    }
                    case "17" -> {
                        ConceptExplainer.idempotentProducer();
                        producerDemo.idempotentProducerDemo();
                    }
                    case "18" -> {
                        ConceptExplainer.retryDLQ();
                        consumerDemo.retryWithDLQ(KafkaConfig.TOPIC_ORDERS, 3);
                    }
                    case "19" -> {
                        ConceptExplainer.multipleGroups();
                        consumerDemo.multipleConsumerGroups(KafkaConfig.TOPIC_ORDERS);
                    }
                    case "20" -> {
                        ConceptExplainer.clusterInspection();
                        consumerDemo.inspectCluster();
                    }
                    case "21" -> {
                        ConceptExplainer.headOfLineBlocking();
                        consumerDemo.headOfLineBlocking();
                    }
                    case "22" -> {
                        ConceptExplainer.consumeTransactional();
                        consumerDemo.consumeTransactional();
                    }

                    // ── CLUSTER SIMULATION (7-broker) ──
                    case "23" -> {
                        ConceptExplainer.clusterTopology();
                        clusterSim.showClusterTopology();
                    }
                    case "24" -> {
                        ConceptExplainer.urpCheck();
                        clusterSim.showURP();
                    }
                    case "25" -> {
                        System.out.print("  Which broker to STOP? [4-7 recommended, 1-3 are controllers]: ");
                        String brokerStr = scanner.nextLine().trim();
                        int brokerId = brokerStr.isEmpty() ? 5 : Integer.parseInt(brokerStr);
                        clusterSim.stopBroker(brokerId);
                    }
                    case "26" -> {
                        System.out.print("  Which broker to START? [enter broker ID]: ");
                        String brokerStr2 = scanner.nextLine().trim();
                        int brokerId2 = brokerStr2.isEmpty() ? 5 : Integer.parseInt(brokerStr2);
                        clusterSim.startBroker(brokerId2);
                    }
                    case "27" -> {
                        ConceptExplainer.failureSimulation();
                        clusterSim.fullFailureSimulation();
                    }
                    case "28" -> {
                        ConceptExplainer.leaderDistribution();
                        clusterSim.showLeaderDistribution();
                    }

                    default -> System.out.println("  Invalid choice. Try again.");
                }
            } catch (Exception e) {
                System.err.println("  ✗ Error: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("\n  ─────────────────────────────────────────────────────────────");
        }

        scanner.close();
    }

    private static void printMenu() {
        System.out.println("""
        
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
        │  CLUSTER SIMULATION (7-broker, RF=3)                          │
        │   23.  Show cluster topology (all 7 brokers)                  │
        │   24.  Show URP (under-replicated partitions)                 │
        │   25.  STOP a broker (simulate failure)                       │
        │   26.  START a broker (recover from failure)                  │
        │   27.  Full failure simulation (automated kill + recover)     │
        │   28.  Show leader distribution across brokers                │
        │                                                               │
        │    0.  Exit                                                   │
        └───────────────────────────────────────────────────────────────┘""");
    }
}
