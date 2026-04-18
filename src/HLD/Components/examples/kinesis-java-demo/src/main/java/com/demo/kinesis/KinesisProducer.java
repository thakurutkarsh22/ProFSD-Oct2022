package com.demo.kinesis;

import com.google.gson.Gson;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kinesis producer demos — covers PutRecord, PutRecords (batch),
 * partition-key routing, hot-shard simulation, and retry on partial failure.
 *
 * Every method prints what it did and the ShardId + SequenceNumber returned
 * so you can verify in the AWS console (Data viewer → pick shard + iterator).
 */
public class KinesisProducer {

    private static final Gson GSON = new Gson();

    private final KinesisClient kinesis;

    public KinesisProducer(KinesisClient kinesis) {
        this.kinesis = kinesis;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1. PutRecord — one record, one network call
    // ──────────────────────────────────────────────────────────────────────
    public void putSingle(String streamName, String partitionKey, String body) {
        PutRecordResponse resp = kinesis.putRecord(PutRecordRequest.builder()
                .streamName(streamName)
                .partitionKey(partitionKey)
                .data(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                .build());

        System.out.printf("  ✓ PutRecord  stream=%s  pk=%s  shard=%s  seq=%s%n",
                streamName, partitionKey, resp.shardId(), resp.sequenceNumber());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. PutRecord with a structured JSON payload (an event)
    // ──────────────────────────────────────────────────────────────────────
    public void putClickEvent(String streamName, String userId, String page) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId",   UUID.randomUUID().toString());
        event.put("userId",    userId);
        event.put("page",      page);
        event.put("timestamp", Instant.now().toString());
        event.put("userAgent", "demo-cli/1.0");

        String json = GSON.toJson(event);
        putSingle(streamName, userId, json);   // PK = userId → per-user ordering
        System.out.println("    payload=" + json);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. PutRecords — batch up to 500 records in ONE call
    // ──────────────────────────────────────────────────────────────────────
    public void putBatch(String streamName, int count, boolean uniformPk) {
        if (count < 1 || count > 500) throw new IllegalArgumentException("count must be 1–500");

        List<PutRecordsRequestEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String pk = uniformPk
                    ? "user_" + ThreadLocalRandom.current().nextInt(1000)   // spread
                    : "hot_user";                                           // hot shard!

            String body = GSON.toJson(Map.of(
                    "eventId",   UUID.randomUUID().toString(),
                    "seq",       i,
                    "pk",        pk,
                    "timestamp", Instant.now().toString()));

            entries.add(PutRecordsRequestEntry.builder()
                    .partitionKey(pk)
                    .data(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                    .build());
        }

        PutRecordsResponse resp = kinesis.putRecords(PutRecordsRequest.builder()
                .streamName(streamName)
                .records(entries)
                .build());

        // Per-record success/failure + shard distribution
        Map<String, Integer> shardCounts = new TreeMap<>();
        int failed = 0;
        for (PutRecordsResultEntry r : resp.records()) {
            if (r.errorCode() != null) {
                failed++;
            } else {
                shardCounts.merge(r.shardId(), 1, Integer::sum);
            }
        }

        System.out.printf("  ✓ PutRecords  stream=%s  sent=%d  failed=%d%n",
                streamName, count, failed);
        System.out.println("    shard distribution:");
        shardCounts.forEach((shard, n) ->
                System.out.printf("      %s : %d records (%.1f%%)%n",
                        shard, n, 100.0 * n / count));

        if (failed > 0) {
            System.out.println("  ⚠ " + failed + " records failed — retry logic would resend only these.");
        }

        if (!uniformPk) {
            System.out.println("  → Hot-shard mode: ALL records have PartitionKey='hot_user'");
            System.out.println("    → MD5('hot_user') is deterministic → all records land in ONE shard.");
            System.out.println("    → This is the classic production failure.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. PutRecords with RETRY on partial failure (production pattern)
    // ──────────────────────────────────────────────────────────────────────
    public void putBatchWithRetry(String streamName, List<PutRecordsRequestEntry> records) {
        int maxAttempts = 5;
        List<PutRecordsRequestEntry> pending = records;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            PutRecordsResponse resp = kinesis.putRecords(PutRecordsRequest.builder()
                    .streamName(streamName).records(pending).build());

            if (resp.failedRecordCount() == 0) {
                System.out.printf("  ✓ All %d records sent on attempt %d%n", records.size(), attempt);
                return;
            }

            List<PutRecordsRequestEntry> retry = new ArrayList<>();
            for (int i = 0; i < resp.records().size(); i++) {
                if (resp.records().get(i).errorCode() != null) {
                    retry.add(pending.get(i));      // preserve original order
                }
            }
            System.out.printf("  attempt %d: %d failed, retrying...%n", attempt, retry.size());
            pending = retry;

            long backoffMs = (long) (Math.pow(2, attempt) * 100 + ThreadLocalRandom.current().nextInt(100));
            try { Thread.sleep(backoffMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        System.err.printf("  ✗ %d records still failing after %d attempts%n", pending.size(), maxAttempts);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. ExplicitHashKey — force a record into a specific shard
    // ──────────────────────────────────────────────────────────────────────
    public void putWithExplicitHashKey(String streamName, String explicitHashKey, String body) {
        PutRecordResponse resp = kinesis.putRecord(PutRecordRequest.builder()
                .streamName(streamName)
                .partitionKey("any")              // ignored when ExplicitHashKey is set
                .explicitHashKey(explicitHashKey)
                .data(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                .build());

        System.out.printf("  ✓ PutRecord (explicit hash=%s)  shard=%s  seq=%s%n",
                explicitHashKey, resp.shardId(), resp.sequenceNumber());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. High-rate stress test — N records/sec for T seconds
    //    Useful to generate backlog for the consumer demos, or to watch
    //    CloudWatch "IncomingRecords" and "WriteProvisionedThroughputExceeded"
    // ──────────────────────────────────────────────────────────────────────
    public void stressTest(String streamName, int totalRecords, int batchSize) {
        System.out.printf("  Stress test: %d records in batches of %d%n", totalRecords, batchSize);
        long startNanos = System.nanoTime();
        int sent = 0, failed = 0;

        while (sent < totalRecords) {
            int thisBatch = Math.min(batchSize, totalRecords - sent);
            List<PutRecordsRequestEntry> entries = new ArrayList<>(thisBatch);
            for (int i = 0; i < thisBatch; i++) {
                String pk = "user_" + ThreadLocalRandom.current().nextInt(10_000);
                String body = "{\"idx\":" + (sent + i) + ",\"ts\":\"" + Instant.now() + "\"}";
                entries.add(PutRecordsRequestEntry.builder()
                        .partitionKey(pk)
                        .data(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                        .build());
            }
            PutRecordsResponse resp = kinesis.putRecords(PutRecordsRequest.builder()
                    .streamName(streamName).records(entries).build());
            sent   += thisBatch;
            failed += resp.failedRecordCount();
            if (sent % 500 == 0) {
                System.out.printf("    sent=%d  failed=%d%n", sent, failed);
            }
        }
        double elapsed = (System.nanoTime() - startNanos) / 1e9;
        System.out.printf("  ✓ Done: sent=%d  failed=%d  elapsed=%.2fs  throughput=%.0f rec/s%n",
                sent, failed, elapsed, sent / elapsed);
    }
}
