package com.demo.sqs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;

/**
 * Bootstraps SQS client and creates all demo queues (standard, FIFO, DLQ)
 * if they don't already exist. Uses the default credential chain
 * (~/.aws/credentials or env vars).
 */
public class SqsConfig {

    private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai

    public static final String STANDARD_QUEUE    = "sqs-demo-standard";
    public static final String FIFO_QUEUE        = "sqs-demo-fifo.fifo";
    public static final String FIFO_DEDUP_QUEUE  = "sqs-demo-dedup.fifo";
    public static final String DLQ_QUEUE         = "sqs-demo-dlq";

    private final SqsClient client;
    private String standardQueueUrl;
    private String fifoQueueUrl;
    private String fifoDedupQueueUrl;
    private String dlqQueueUrl;

    public SqsConfig() {
        this.client = SqsClient.builder()
                .region(REGION)
                .build();
    }

    public void initialize() {
        System.out.println("=== Initializing SQS queues in " + REGION + " ===\n");

        this.dlqQueueUrl       = ensureQueue(DLQ_QUEUE, false);
        this.standardQueueUrl  = ensureStandardQueueWithDlq();
        this.fifoQueueUrl      = ensureFifoQueue();
        this.fifoDedupQueueUrl = ensureFifoDedupQueue();

        System.out.println("\n  Standard  : " + standardQueueUrl);
        System.out.println("  FIFO      : " + fifoQueueUrl);
        System.out.println("  FIFO-Dedup: " + fifoDedupQueueUrl);
        System.out.println("  DLQ       : " + dlqQueueUrl);
        System.out.println();
    }

    private String ensureQueue(String name, boolean isFifo) {
        try {
            return client.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(name).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            var attrs = new java.util.HashMap<QueueAttributeName, String>();
            if (isFifo) {
                attrs.put(QueueAttributeName.FIFO_QUEUE, "true");
                attrs.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");
            }
            String url = client.createQueue(CreateQueueRequest.builder()
                    .queueName(name).attributes(attrs).build()).queueUrl();
            System.out.println("  Created queue: " + name);
            return url;
        }
    }

    private String ensureStandardQueueWithDlq() {
        String dlqArn = client.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(dlqQueueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).attributes().get(QueueAttributeName.QUEUE_ARN);

        String redrivePolicy = """
                {"deadLetterTargetArn":"%s","maxReceiveCount":"3"}""".formatted(dlqArn);

        try {
            String url = client.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(STANDARD_QUEUE).build()).queueUrl();
            client.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(url)
                    .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, redrivePolicy))
                    .build());
            return url;
        } catch (QueueDoesNotExistException e) {
            String url = client.createQueue(CreateQueueRequest.builder()
                    .queueName(STANDARD_QUEUE)
                    .attributes(Map.of(
                            QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                            QueueAttributeName.REDRIVE_POLICY, redrivePolicy
                    )).build()).queueUrl();
            System.out.println("  Created queue: " + STANDARD_QUEUE + " (with DLQ redrive)");
            return url;
        }
    }

    private String ensureFifoQueue() {
        return ensureQueue(FIFO_QUEUE, true);
    }

    private String ensureFifoDedupQueue() {
        try {
            return client.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(FIFO_DEDUP_QUEUE).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            var attrs = new java.util.HashMap<QueueAttributeName, String>();
            attrs.put(QueueAttributeName.FIFO_QUEUE, "true");
            attrs.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false");
            String url = client.createQueue(CreateQueueRequest.builder()
                    .queueName(FIFO_DEDUP_QUEUE).attributes(attrs).build()).queueUrl();
            System.out.println("  Created queue: " + FIFO_DEDUP_QUEUE + " (explicit dedup, NO content-based)");
            return url;
        }
    }

    public SqsClient client()            { return client; }
    public String standardQueueUrl()      { return standardQueueUrl; }
    public String fifoQueueUrl()          { return fifoQueueUrl; }
    public String fifoDedupQueueUrl()     { return fifoDedupQueueUrl; }
    public String dlqQueueUrl()           { return dlqQueueUrl; }

    public void close() {
        client.close();
    }
}
