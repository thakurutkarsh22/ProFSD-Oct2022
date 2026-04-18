package com.demo.kinesis;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

/**
 * Bootstraps the Kinesis client and auto-creates two streams if they don't
 * already exist:
 *   - kinesis-demo-ondemand     (On-Demand mode, auto-scaling)
 *   - kinesis-demo-provisioned  (Provisioned mode, 2 shards — enough to
 *                                show hash-ring routing, hot shards, and
 *                                split/merge)
 *
 * Uses the default credential chain (~/.aws/credentials or env vars).
 */
public class KinesisConfig {

    // CHANGE THIS if you configured aws cli with a different region.
    private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai

    public static final String ONDEMAND_STREAM    = "kinesis-demo-ondemand";
    public static final String PROVISIONED_STREAM = "kinesis-demo-provisioned";
    public static final int    PROVISIONED_SHARDS = 2;

    private final KinesisClient kinesis;
    private final CloudWatchClient cloudwatch;

    public KinesisConfig() {
        this.kinesis    = KinesisClient.builder().region(REGION).build();
        this.cloudwatch = CloudWatchClient.builder().region(REGION).build();
    }

    public void initialize() {
        System.out.println("=== Initializing Kinesis streams in " + REGION + " ===\n");

        ensureStream(ONDEMAND_STREAM, StreamMode.ON_DEMAND, 0);
        ensureStream(PROVISIONED_STREAM, StreamMode.PROVISIONED, PROVISIONED_SHARDS);

        waitUntilActive(ONDEMAND_STREAM);
        waitUntilActive(PROVISIONED_STREAM);

        printStreamSummary(ONDEMAND_STREAM);
        printStreamSummary(PROVISIONED_STREAM);
        System.out.println();
    }

    private void ensureStream(String name, StreamMode mode, int shardCount) {
        try {
            kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                    .streamName(name).build());
            System.out.println("  Stream exists: " + name);
        } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException e) {
            var req = CreateStreamRequest.builder()
                    .streamName(name)
                    .streamModeDetails(StreamModeDetails.builder().streamMode(mode).build());
            if (mode == StreamMode.PROVISIONED) {
                req = req.shardCount(shardCount);
            }
            kinesis.createStream(req.build());
            System.out.println("  Created stream: " + name + " (" + mode + (mode == StreamMode.PROVISIONED ? ", " + shardCount + " shards" : "") + ")");
        }
    }

    private void waitUntilActive(String name) {
        for (int i = 0; i < 60; i++) {
            var status = kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                    .streamName(name).build()).streamDescriptionSummary().streamStatus();
            if (status == StreamStatus.ACTIVE) return;
            System.out.println("  Waiting for " + name + " (status=" + status + ")...");
            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        throw new IllegalStateException("Stream " + name + " not ACTIVE after 2 minutes");
    }

    private void printStreamSummary(String name) {
        var s = kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                .streamName(name).build()).streamDescriptionSummary();
        System.out.printf("  %-30s arn=%s  shards=%d  mode=%s  retention=%dh%n",
                name, s.streamARN(), s.openShardCount(), s.streamModeDetails().streamMode(),
                s.retentionPeriodHours());
    }

    public KinesisClient kinesis()         { return kinesis; }
    public CloudWatchClient cloudwatch()   { return cloudwatch; }
    public Region region()                 { return REGION; }

    public void close() {
        kinesis.close();
        cloudwatch.close();
    }
}
