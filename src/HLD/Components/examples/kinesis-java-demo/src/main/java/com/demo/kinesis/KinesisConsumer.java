package com.demo.kinesis;

import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-level Kinesis consumer using GetShardIterator + GetRecords.
 *
 * This is intentionally NOT using the KCL — we show the primitives so you
 * understand what KCL automates (shard discovery, lease coordination,
 * DynamoDB checkpointing).
 *
 * Supports all four iterator types:
 *   - TRIM_HORIZON         — oldest record still in retention
 *   - LATEST               — only new records after subscribe
 *   - AT_TIMESTAMP         — records at or after a specific time
 *   - AT/AFTER_SEQUENCE_NUMBER — resume from a known offset
 */
public class KinesisConsumer {

    private final KinesisClient kinesis;

    public KinesisConsumer(KinesisClient kinesis) {
        this.kinesis = kinesis;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1. List all shards in a stream with their hash ranges
    // ──────────────────────────────────────────────────────────────────────
    public List<Shard> listShards(String streamName) {
        List<Shard> all = new ArrayList<>();
        String nextToken = null;
        do {
            var req = ListShardsRequest.builder();
            if (nextToken != null) {
                req.nextToken(nextToken);
            } else {
                req.streamName(streamName);
            }
            ListShardsResponse resp = kinesis.listShards(req.build());
            all.addAll(resp.shards());
            nextToken = resp.nextToken();
        } while (nextToken != null);

        System.out.printf("  Stream '%s' has %d shards (open + closed):%n",
                streamName, all.size());
        for (Shard s : all) {
            boolean closed = s.sequenceNumberRange().endingSequenceNumber() != null;
            System.out.printf("    %s  %s  hashRange=[%s .. %s]  parents=%s%n",
                    s.shardId(),
                    closed ? "CLOSED" : "OPEN",
                    shortHash(s.hashKeyRange().startingHashKey()),
                    shortHash(s.hashKeyRange().endingHashKey()),
                    Optional.ofNullable(s.parentShardId()).orElse("-"));
        }
        return all;
    }

    private String shortHash(String fullHash) {
        return fullHash.length() > 12 ? fullHash.substring(0, 6) + "…" + fullHash.substring(fullHash.length() - 6) : fullHash;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Read records from one shard, starting from TRIM_HORIZON (= beginning)
    // ──────────────────────────────────────────────────────────────────────
    public int readShardFromStart(String streamName, String shardId, int maxRecords) {
        return readShard(streamName, shardId, ShardIteratorType.TRIM_HORIZON, null, null, maxRecords);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Read ONLY new records (tail -f style)
    // ──────────────────────────────────────────────────────────────────────
    public int readShardLatest(String streamName, String shardId, int maxRecords) {
        return readShard(streamName, shardId, ShardIteratorType.LATEST, null, null, maxRecords);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. Replay — read records arriving at or after a timestamp
    //    This is KDS's superpower over SQS.
    // ──────────────────────────────────────────────────────────────────────
    public int replayFromTimestamp(String streamName, String shardId, Instant when, int maxRecords) {
        System.out.printf("  Replaying '%s' / %s from %s%n", streamName, shardId, when);
        return readShard(streamName, shardId, ShardIteratorType.AT_TIMESTAMP, null, when, maxRecords);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. Resume from a specific sequence number (manual checkpoint pattern)
    // ──────────────────────────────────────────────────────────────────────
    public int readFromSequence(String streamName, String shardId, String afterSeq, int maxRecords) {
        return readShard(streamName, shardId, ShardIteratorType.AFTER_SEQUENCE_NUMBER, afterSeq, null, maxRecords);
    }

    private int readShard(String streamName, String shardId,
                          ShardIteratorType type, String seq, Instant ts, int maxRecords) {
        var iterReq = GetShardIteratorRequest.builder()
                .streamName(streamName)
                .shardId(shardId)
                .shardIteratorType(type);
        if (seq != null) iterReq.startingSequenceNumber(seq);
        if (ts  != null) iterReq.timestamp(ts);

        String iterator = kinesis.getShardIterator(iterReq.build()).shardIterator();
        int read = 0;
        int emptyReceives = 0;

        System.out.printf("  Reading shard %s (iterator=%s)%n", shardId, type);
        while (read < maxRecords && emptyReceives < 3) {
            GetRecordsResponse resp = kinesis.getRecords(GetRecordsRequest.builder()
                    .shardIterator(iterator)
                    .limit(Math.min(100, maxRecords - read))
                    .build());

            if (resp.records().isEmpty()) {
                emptyReceives++;
                System.out.printf("    (empty read — lagMs=%d)%n", resp.millisBehindLatest());
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            } else {
                emptyReceives = 0;
                for (software.amazon.awssdk.services.kinesis.model.Record r : resp.records()) {
                    String body = r.data().asString(StandardCharsets.UTF_8);
                    System.out.printf("    [%s] pk=%s seq=%s ts=%s%n      → %s%n",
                            shardId, r.partitionKey(),
                            r.sequenceNumber().substring(r.sequenceNumber().length() - 8),
                            r.approximateArrivalTimestamp(),
                            body.length() > 120 ? body.substring(0, 120) + "..." : body);
                    read++;
                    if (read >= maxRecords) break;
                }
                System.out.printf("    (batch of %d, lagMs=%d behind tail)%n",
                        resp.records().size(), resp.millisBehindLatest());
            }

            iterator = resp.nextShardIterator();
            if (iterator == null) {
                System.out.println("    (shard ended — parent shard fully drained after split/merge)");
                break;
            }
        }
        System.out.printf("  ✓ Read %d records from shard %s%n", read, shardId);
        return read;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. Competing consumers — one worker thread per shard (mini-KCL)
    //    Each worker polls its own shard, prints records, tracks counts.
    //    This is what KCL does for you (minus the lease coordination).
    // ──────────────────────────────────────────────────────────────────────
    public void runCompetingConsumers(String streamName, int durationSeconds) throws InterruptedException {
        List<Shard> shards = listShards(streamName).stream()
                .filter(s -> s.sequenceNumberRange().endingSequenceNumber() == null) // open only
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(shards.size());
        AtomicLong totalRead = new AtomicLong();
        Map<String, AtomicLong> perShardCounts = new ConcurrentHashMap<>();

        System.out.printf("  Starting %d worker threads, one per shard, for %d seconds%n",
                shards.size(), durationSeconds);

        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;
        for (Shard shard : shards) {
            String sid = shard.shardId();
            perShardCounts.put(sid, new AtomicLong());
            pool.submit(() -> {
                try {
                    String iterator = kinesis.getShardIterator(GetShardIteratorRequest.builder()
                            .streamName(streamName).shardId(sid)
                            .shardIteratorType(ShardIteratorType.LATEST).build()).shardIterator();
                    while (System.currentTimeMillis() < deadline && iterator != null) {
                        GetRecordsResponse resp = kinesis.getRecords(GetRecordsRequest.builder()
                                .shardIterator(iterator).limit(100).build());
                        if (!resp.records().isEmpty()) {
                            perShardCounts.get(sid).addAndGet(resp.records().size());
                            totalRead.addAndGet(resp.records().size());
                        }
                        iterator = resp.nextShardIterator();
                        Thread.sleep(500);    // KDS allows 5 GetRecords/sec/shard
                    }
                } catch (Exception e) {
                    System.err.println("  worker " + sid + " error: " + e.getMessage());
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(durationSeconds + 10, TimeUnit.SECONDS);

        System.out.printf("%n  ✓ Competing consumers finished. Total read=%d%n", totalRead.get());
        perShardCounts.forEach((sid, n) ->
                System.out.printf("    %s : %d records%n", sid, n.get()));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 7. Show "IteratorAge" per shard (how far behind tail each shard is)
    //    This is THE most important metric to alarm on in production.
    // ──────────────────────────────────────────────────────────────────────
    public void showIteratorAges(String streamName) {
        List<Shard> shards = listShards(streamName).stream()
                .filter(s -> s.sequenceNumberRange().endingSequenceNumber() == null)
                .toList();

        System.out.printf("%n  IteratorAge per shard (ms behind tail, from TRIM_HORIZON):%n");
        for (Shard s : shards) {
            try {
                String iterator = kinesis.getShardIterator(GetShardIteratorRequest.builder()
                        .streamName(streamName).shardId(s.shardId())
                        .shardIteratorType(ShardIteratorType.TRIM_HORIZON).build()).shardIterator();
                GetRecordsResponse resp = kinesis.getRecords(GetRecordsRequest.builder()
                        .shardIterator(iterator).limit(1).build());
                long lagMs = resp.millisBehindLatest();
                System.out.printf("    %s : lag=%d ms (%.1f min) — %d records available%n",
                        s.shardId(), lagMs, lagMs / 60_000.0, resp.records().size());
            } catch (Exception e) {
                System.out.printf("    %s : error %s%n", s.shardId(), e.getMessage());
            }
        }
    }
}
