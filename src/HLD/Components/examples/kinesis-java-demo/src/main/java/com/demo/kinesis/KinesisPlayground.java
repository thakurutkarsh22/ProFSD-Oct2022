package com.demo.kinesis;

import software.amazon.awssdk.services.kinesis.model.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

/**
 * Interactive Kinesis CLI.
 *
 * Run:     mvn compile exec:java
 * Requires: ~/.aws/credentials (or env vars) with AmazonKinesisFullAccess.
 */
public class KinesisPlayground {

    private static KinesisConfig   config;
    private static KinesisProducer producer;
    private static KinesisConsumer consumer;
    private static KinesisAdmin    admin;

    public static void main(String[] args) {
        printBanner();

        config = new KinesisConfig();
        config.initialize();
        producer = new KinesisProducer(config.kinesis());
        consumer = new KinesisConsumer(config.kinesis());
        admin    = new KinesisAdmin(config.kinesis(), config.cloudwatch());

        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice > ");
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    // ── PRODUCE ──
                    case "1" -> {
                        ConceptExplainer.putSingle();
                        System.out.print("  Message body: ");
                        String body = sc.nextLine();
                        System.out.print("  PartitionKey (enter = 'default'): ");
                        String pk = sc.nextLine().trim();
                        if (pk.isEmpty()) pk = "default";
                        producer.putSingle(KinesisConfig.ONDEMAND_STREAM, pk, body);
                    }
                    case "2" -> {
                        ConceptExplainer.putClickEvent();
                        System.out.print("  userId (enter = 'user_42'): ");
                        String user = sc.nextLine().trim();
                        if (user.isEmpty()) user = "user_42";
                        System.out.print("  page (enter = '/home'): ");
                        String page = sc.nextLine().trim();
                        if (page.isEmpty()) page = "/home";
                        producer.putClickEvent(KinesisConfig.ONDEMAND_STREAM, user, page);
                    }
                    case "3" -> {
                        ConceptExplainer.putBatchUniform();
                        System.out.print("  How many records (1-500)? ");
                        int n = Integer.parseInt(sc.nextLine().trim());
                        producer.putBatch(KinesisConfig.PROVISIONED_STREAM, n, true);
                    }
                    case "4" -> {
                        ConceptExplainer.putBatchHot();
                        System.out.print("  How many records (1-500)? ");
                        int n = Integer.parseInt(sc.nextLine().trim());
                        producer.putBatch(KinesisConfig.PROVISIONED_STREAM, n, false);
                    }
                    case "5" -> {
                        ConceptExplainer.putExplicitHashKey();
                        // force hash into the middle of shard 0 — compute first shard's midpoint
                        var shards = consumer.listShards(KinesisConfig.PROVISIONED_STREAM);
                        Shard s0 = shards.get(0);
                        BigInteger start = new BigInteger(s0.hashKeyRange().startingHashKey());
                        BigInteger end   = new BigInteger(s0.hashKeyRange().endingHashKey());
                        BigInteger mid   = start.add(end).divide(BigInteger.TWO);
                        System.out.println("  Targeting shard " + s0.shardId() + " via ExplicitHashKey=" + mid);
                        producer.putWithExplicitHashKey(KinesisConfig.PROVISIONED_STREAM,
                                mid.toString(), "{\"forced\":true,\"ts\":\"" + Instant.now() + "\"}");
                    }
                    case "6" -> {
                        ConceptExplainer.stressTest();
                        System.out.print("  How many total records (e.g. 5000)? ");
                        int total = Integer.parseInt(sc.nextLine().trim());
                        producer.stressTest(KinesisConfig.PROVISIONED_STREAM, total, 500);
                    }

                    // ── CONSUME ──
                    case "7" -> {
                        ConceptExplainer.listShards();
                        System.out.print("  Stream (o = ondemand / p = provisioned): ");
                        String stream = pickStream(sc);
                        consumer.listShards(stream);
                    }
                    case "8" -> {
                        ConceptExplainer.readFromStart();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        var shards = consumer.listShards(stream);
                        System.out.print("  shardId (enter = first open shard): ");
                        String sid = sc.nextLine().trim();
                        if (sid.isEmpty()) sid = shards.get(0).shardId();
                        System.out.print("  Max records to read (enter = 50): ");
                        String mxs = sc.nextLine().trim();
                        int mx = mxs.isEmpty() ? 50 : Integer.parseInt(mxs);
                        consumer.readShardFromStart(stream, sid, mx);
                    }
                    case "9" -> {
                        ConceptExplainer.readLatest();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        var shards = consumer.listShards(stream);
                        System.out.print("  shardId (enter = first open shard): ");
                        String sid = sc.nextLine().trim();
                        if (sid.isEmpty()) sid = shards.get(0).shardId();
                        System.out.println("  Subscribing at LATEST for 15s — send records from another terminal!");
                        consumer.readShardLatest(stream, sid, 200);
                    }
                    case "10" -> {
                        ConceptExplainer.replay();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        var shards = consumer.listShards(stream);
                        System.out.print("  shardId (enter = first open shard): ");
                        String sid = sc.nextLine().trim();
                        if (sid.isEmpty()) sid = shards.get(0).shardId();
                        System.out.print("  Replay from how many minutes ago? ");
                        int mins = Integer.parseInt(sc.nextLine().trim());
                        Instant t = Instant.now().minus(mins, ChronoUnit.MINUTES);
                        consumer.replayFromTimestamp(stream, sid, t, 100);
                    }
                    case "11" -> {
                        ConceptExplainer.iteratorAge();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        consumer.showIteratorAges(stream);
                    }
                    case "12" -> {
                        ConceptExplainer.competingConsumers();
                        System.out.print("  Stream (o/p, default p): ");
                        String stream = pickStream(sc);
                        System.out.print("  Run for how many seconds (e.g. 30)? ");
                        int secs = Integer.parseInt(sc.nextLine().trim());
                        System.out.println("  TIP: open another terminal and run option 3 or 6 to generate traffic.");
                        consumer.runCompetingConsumers(stream, secs);
                    }

                    // ── ADMIN ──
                    case "13" -> {
                        ConceptExplainer.describe();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        admin.describe(stream);
                    }
                    case "14" -> {
                        ConceptExplainer.splitShard();
                        var shards = consumer.listShards(KinesisConfig.PROVISIONED_STREAM);
                        System.out.print("  shardId to split (enter = first open): ");
                        String sid = sc.nextLine().trim();
                        if (sid.isEmpty()) sid = shards.stream()
                                .filter(s -> s.sequenceNumberRange().endingSequenceNumber() == null)
                                .findFirst().get().shardId();
                        admin.splitShard(KinesisConfig.PROVISIONED_STREAM, sid);
                    }
                    case "15" -> {
                        ConceptExplainer.mergeShards();
                        consumer.listShards(KinesisConfig.PROVISIONED_STREAM);
                        System.out.print("  shard A: ");
                        String a = sc.nextLine().trim();
                        System.out.print("  adjacent shard B: ");
                        String b = sc.nextLine().trim();
                        admin.mergeShards(KinesisConfig.PROVISIONED_STREAM, a, b);
                    }
                    case "16" -> {
                        ConceptExplainer.retention();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        System.out.print("  New retention in hours (24–8760): ");
                        int h = Integer.parseInt(sc.nextLine().trim());
                        admin.increaseRetention(stream, h);
                    }
                    case "17" -> {
                        ConceptExplainer.cloudwatch();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        admin.cloudwatchSnapshot(stream);
                    }
                    case "18" -> {
                        ConceptExplainer.purge();
                        System.out.print("  Stream (o/p): ");
                        String stream = pickStream(sc);
                        System.out.print("  Type YES to confirm delete+recreate: ");
                        if (!"YES".equals(sc.nextLine().trim())) {
                            System.out.println("  Cancelled."); break;
                        }
                        StreamMode mode = stream.equals(KinesisConfig.ONDEMAND_STREAM)
                                ? StreamMode.ON_DEMAND : StreamMode.PROVISIONED;
                        admin.purgeAndRecreate(stream, KinesisConfig.PROVISIONED_SHARDS, mode);
                    }

                    case "0" -> {
                        System.out.println("  Bye!");
                        running = false;
                    }
                    default -> System.out.println("  Unknown choice.");
                }
            } catch (Exception e) {
                System.err.println("  ✗ Error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        }
        config.close();
    }

    private static String pickStream(Scanner sc) {
        String line = sc.nextLine().trim().toLowerCase();
        return line.startsWith("o") ? KinesisConfig.ONDEMAND_STREAM : KinesisConfig.PROVISIONED_STREAM;
    }

    private static void printBanner() {
        System.out.println("""
                ╔════════════════════════════════════════════════════════════════════╗
                ║                 AWS KINESIS JAVA PLAYGROUND                        ║
                ║                                                                    ║
                ║  Auto-creates 2 streams:                                           ║
                ║    kinesis-demo-ondemand      (On-Demand, auto-scale)              ║
                ║    kinesis-demo-provisioned   (Provisioned, 2 shards)              ║
                ║                                                                    ║
                ║  Verify everything in AWS Console → Kinesis → Data streams →       ║
                ║    Data viewer  (pick shard + starting position + Get records)     ║
                ╚════════════════════════════════════════════════════════════════════╝
                """);
    }

    private static void printMenu() {
        System.out.println("""

                ┌─ PRODUCE ─────────────────────────────────────────────────────────┐
                │  1. PutRecord — one record                                        │
                │  2. Put a structured click event (PK=userId)                      │
                │  3. PutRecords batch — UNIFORM PK (healthy)                       │
                │  4. PutRecords batch — HOT PK  (production failure!)              │
                │  5. PutRecord with ExplicitHashKey (force shard)                  │
                │  6. Stress test — N records, show throttles                       │
                │                                                                   │
                ├─ CONSUME ─────────────────────────────────────────────────────────┤
                │  7. List shards + hash ranges                                     │
                │  8. Read shard from TRIM_HORIZON (beginning)                      │
                │  9. Read shard at LATEST (tail -f)                                │
                │ 10. Replay from N minutes ago (AT_TIMESTAMP)                      │
                │ 11. Show IteratorAge per shard (the P1 metric)                    │
                │ 12. Competing consumers — one thread per shard                    │
                │                                                                   │
                ├─ ADMIN ───────────────────────────────────────────────────────────┤
                │ 13. DescribeStreamSummary                                         │
                │ 14. SplitShard (provisioned)                                      │
                │ 15. MergeShards (provisioned)                                     │
                │ 16. IncreaseStreamRetentionPeriod                                 │
                │ 17. CloudWatch metrics snapshot (last 5 min)                      │
                │ 18. Delete + recreate a stream (= purge)                          │
                │                                                                   │
                │  0. Exit                                                          │
                └───────────────────────────────────────────────────────────────────┘""");
    }
}
