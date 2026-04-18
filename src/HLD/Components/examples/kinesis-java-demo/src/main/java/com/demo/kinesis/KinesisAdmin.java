package com.demo.kinesis;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Admin operations: describe stream, resharding (split / merge),
 * retention management, CloudWatch metrics snapshot.
 */
public class KinesisAdmin {

    private final KinesisClient kinesis;
    private final CloudWatchClient cloudwatch;

    public KinesisAdmin(KinesisClient kinesis, CloudWatchClient cloudwatch) {
        this.kinesis = kinesis;
        this.cloudwatch = cloudwatch;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1. DescribeStream summary
    // ──────────────────────────────────────────────────────────────────────
    public void describe(String streamName) {
        var s = kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                .streamName(streamName).build()).streamDescriptionSummary();

        System.out.println("  ─── Stream summary ─────────────────────");
        System.out.println("    name          : " + s.streamName());
        System.out.println("    arn           : " + s.streamARN());
        System.out.println("    status        : " + s.streamStatus());
        System.out.println("    mode          : " + s.streamModeDetails().streamMode());
        System.out.println("    open shards   : " + s.openShardCount());
        System.out.println("    retention (h) : " + s.retentionPeriodHours());
        System.out.println("    creation      : " + s.streamCreationTimestamp());
        System.out.println("    encryption    : " + s.encryptionType());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Split a shard — double capacity in its hash range
    //    Only valid for Provisioned-mode streams.
    // ──────────────────────────────────────────────────────────────────────
    public void splitShard(String streamName, String shardId) {
        var shards = kinesis.listShards(ListShardsRequest.builder()
                .streamName(streamName).build()).shards();

        Shard target = shards.stream()
                .filter(s -> s.shardId().equals(shardId))
                .filter(s -> s.sequenceNumberRange().endingSequenceNumber() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No open shard " + shardId));

        BigInteger start = new BigInteger(target.hashKeyRange().startingHashKey());
        BigInteger end   = new BigInteger(target.hashKeyRange().endingHashKey());
        BigInteger mid   = start.add(end).divide(BigInteger.TWO);

        System.out.printf("  Splitting %s at hash %s (range was [%s..%s])%n",
                shardId, mid, start, end);

        kinesis.splitShard(SplitShardRequest.builder()
                .streamName(streamName)
                .shardToSplit(shardId)
                .newStartingHashKey(mid.toString())
                .build());

        System.out.println("  ✓ SplitShard submitted. Stream will be UPDATING for ~1 min.");
        System.out.println("    Parent shard is now CLOSED (no new writes, drain-only).");
        System.out.println("    Two NEW child shards created — listShards again to see them.");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Merge two adjacent shards (only in Provisioned mode)
    // ──────────────────────────────────────────────────────────────────────
    public void mergeShards(String streamName, String shardA, String shardB) {
        System.out.printf("  Merging %s + %s%n", shardA, shardB);
        kinesis.mergeShards(MergeShardsRequest.builder()
                .streamName(streamName)
                .shardToMerge(shardA)
                .adjacentShardToMerge(shardB)
                .build());
        System.out.println("  ✓ MergeShards submitted. Both parents become CLOSED; 1 child appears.");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. UpdateShardCount (Provisioned only) — uniform scale up/down
    // ──────────────────────────────────────────────────────────────────────
    public void updateShardCount(String streamName, int target) {
        System.out.printf("  Updating shard count to %d (limits: 2x up or 2x down per 24h)%n", target);
        kinesis.updateShardCount(UpdateShardCountRequest.builder()
                .streamName(streamName)
                .targetShardCount(target)
                .scalingType(ScalingType.UNIFORM_SCALING)
                .build());
        System.out.println("  ✓ Submitted. Stream is UPDATING for ~2 min.");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. Increase retention — enables longer replay windows
    // ──────────────────────────────────────────────────────────────────────
    public void increaseRetention(String streamName, int hours) {
        if (hours < 24 || hours > 8760) throw new IllegalArgumentException("24–8760 hours");
        kinesis.increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest.builder()
                .streamName(streamName).retentionPeriodHours(hours).build());
        System.out.println("  ✓ Retention set to " + hours + "h. Old records now preserved.");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. CloudWatch snapshot — the metrics you'd alarm on
    // ──────────────────────────────────────────────────────────────────────
    public void cloudwatchSnapshot(String streamName) {
        Instant end   = Instant.now();
        Instant start = end.minus(5, ChronoUnit.MINUTES);
        System.out.printf("%n  CloudWatch metrics (last 5 min) for stream '%s':%n", streamName);

        String[] metrics = {
                "IncomingBytes",
                "IncomingRecords",
                "GetRecords.IteratorAgeMilliseconds",
                "WriteProvisionedThroughputExceeded",
                "ReadProvisionedThroughputExceeded"
        };

        for (String m : metrics) {
            var resp = cloudwatch.getMetricStatistics(GetMetricStatisticsRequest.builder()
                    .namespace("AWS/Kinesis")
                    .metricName(m)
                    .dimensions(Dimension.builder().name("StreamName").value(streamName).build())
                    .startTime(start)
                    .endTime(end)
                    .period(60)
                    .statistics(Statistic.SUM, Statistic.MAXIMUM)
                    .build());

            double totalSum  = resp.datapoints().stream().mapToDouble(Datapoint::sum).sum();
            double maxVal    = resp.datapoints().stream().mapToDouble(Datapoint::maximum).max().orElse(0);
            System.out.printf("    %-45s sum=%.1f  max=%.1f  datapoints=%d%n",
                    m, totalSum, maxVal, resp.datapoints().size());
        }
        System.out.println("    (hint: IteratorAgeMilliseconds > 300,000 = P1 alarm; fall-behind > 5 min)");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 7. Delete + recreate a stream (like SQS's purge)
    // ──────────────────────────────────────────────────────────────────────
    public void purgeAndRecreate(String streamName, int shardCount, StreamMode mode) {
        try {
            kinesis.deleteStream(DeleteStreamRequest.builder()
                    .streamName(streamName).enforceConsumerDeletion(true).build());
            System.out.println("  Deleted " + streamName + "; waiting for tombstone...");
            for (int i = 0; i < 30; i++) {
                try {
                    kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                            .streamName(streamName).build());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException nf) {
                    break;
                }
            }
        } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException e) {
            // already gone
        }
        var req = CreateStreamRequest.builder()
                .streamName(streamName)
                .streamModeDetails(StreamModeDetails.builder().streamMode(mode).build());
        if (mode == StreamMode.PROVISIONED) req = req.shardCount(shardCount);
        kinesis.createStream(req.build());
        System.out.println("  ✓ Recreated " + streamName + " (" + mode + ")");
    }
}
