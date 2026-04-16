package com.demo.sqs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

public class SqsProducer {

    private final SqsClient client;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SqsProducer(SqsClient client) {
        this.client = client;
    }

    // ──────────────────────────────────────────────
    //  1) Send a single message to Standard queue
    // ──────────────────────────────────────────────
    public void sendSingleMessage(String queueUrl, String body) {
        SendMessageResponse resp = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build());

        System.out.println("  Sent message:");
        System.out.println("    MessageId : " + resp.messageId());
        System.out.println("    MD5       : " + resp.md5OfMessageBody());
    }

    // ──────────────────────────────────────────────
    //  2) Send message with attributes (metadata)
    // ──────────────────────────────────────────────
    public void sendWithAttributes(String queueUrl) {
        Map<String, String> order = Map.of(
                "orderId", "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "customerId", "CUST-42",
                "amount", "129.99",
                "currency", "USD",
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> attrs = Map.of(
                "Source", MessageAttributeValue.builder()
                        .dataType("String").stringValue("checkout-service").build(),
                "Priority", MessageAttributeValue.builder()
                        .dataType("Number").stringValue("1").build(),
                "Environment", MessageAttributeValue.builder()
                        .dataType("String").stringValue("production").build()
        );

        SendMessageResponse resp = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(gson.toJson(order))
                .messageAttributes(attrs)
                .delaySeconds(0)
                .build());

        System.out.println("  Sent order event with attributes:");
        System.out.println("    MessageId : " + resp.messageId());
        System.out.println("    Body      : " + gson.toJson(order));
        System.out.println("    Attrs     : Source=checkout-service, Priority=1");
    }

    // ──────────────────────────────────────────────
    //  3) Batch send — up to 10 messages at once
    // ──────────────────────────────────────────────
    public void sendBatch(String queueUrl, int count) {
        int batchSize = Math.min(count, 10);
        List<SendMessageBatchRequestEntry> entries = IntStream.rangeClosed(1, batchSize)
                .mapToObj(i -> {
                    Map<String, String> payload = Map.of(
                            "type", "batch-item",
                            "index", String.valueOf(i),
                            "timestamp", Instant.now().toString()
                    );
                    return SendMessageBatchRequestEntry.builder()
                            .id("msg-" + i)
                            .messageBody(gson.toJson(payload))
                            .build();
                }).toList();

        SendMessageBatchResponse resp = client.sendMessageBatch(
                SendMessageBatchRequest.builder()
                        .queueUrl(queueUrl)
                        .entries(entries)
                        .build());

        System.out.println("  Batch sent " + resp.successful().size() + " messages:");
        resp.successful().forEach(s ->
                System.out.println("    OK  id=" + s.id() + "  messageId=" + s.messageId()));
        resp.failed().forEach(f ->
                System.out.println("    FAIL id=" + f.id() + "  code=" + f.code()));
    }

    // ──────────────────────────────────────────────
    //  4) Send to FIFO queue with MessageGroupId
    // ──────────────────────────────────────────────
    public void sendFifoMessages(String fifoQueueUrl) {
        String[] customers = {"customer-A", "customer-B", "customer-A", "customer-B", "customer-A"};
        String[] actions   = {"place-order", "place-order", "pay", "pay", "ship"};

        for (int i = 0; i < customers.length; i++) {
            Map<String, String> event = Map.of(
                    "customer", customers[i],
                    "action", actions[i],
                    "sequence", String.valueOf(i + 1),
                    "timestamp", Instant.now().toString()
            );

            SendMessageResponse resp = client.sendMessage(SendMessageRequest.builder()
                    .queueUrl(fifoQueueUrl)
                    .messageBody(gson.toJson(event))
                    .messageGroupId(customers[i])
                    .build());

            System.out.println("  FIFO msg #" + (i + 1)
                    + "  group=" + customers[i]
                    + "  action=" + actions[i]
                    + "  msgId=" + resp.messageId().substring(0, 12) + "...");
        }
        System.out.println("\n  Go check AWS Console → FIFO queue → messages will be ordered per group!");
    }

    // ──────────────────────────────────────────────────────────
    //  5) Deduplication demo — explicit ID vs content-based
    // ──────────────────────────────────────────────────────────
    public void demonstrateDeduplication(String contentBasedFifoUrl, String explicitDedupFifoUrl) {

        // ── PART A: Content-based deduplication ──
        System.out.println("  ══════════════════════════════════════════════════════════════");
        System.out.println("  PART A: Content-Based Deduplication (sqs-demo-fifo.fifo)");
        System.out.println("  Queue has ContentBasedDeduplication=true");
        System.out.println("  SQS hashes the message body → same body = duplicate → rejected");
        System.out.println("  ══════════════════════════════════════════════════════════════\n");

        String sameBody = "{\"orderId\":\"ORD-999\",\"action\":\"charge\",\"amount\":49.99}";

        System.out.println("  Sending message #1 with body: " + sameBody);
        SendMessageResponse resp1 = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(contentBasedFifoUrl)
                .messageBody(sameBody)
                .messageGroupId("orders")
                .build());
        System.out.println("  Result: MessageId=" + resp1.messageId().substring(0, 12) + "...");
        System.out.println("          SequenceNumber=" + resp1.sequenceNumber());

        System.out.println("\n  Sending message #2 with EXACT SAME body (within 5-min window)...");
        SendMessageResponse resp2 = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(contentBasedFifoUrl)
                .messageBody(sameBody)
                .messageGroupId("orders")
                .build());
        System.out.println("  Result: MessageId=" + resp2.messageId().substring(0, 12) + "...");
        System.out.println("          SequenceNumber=" + resp2.sequenceNumber());

        boolean isDuplicate = resp1.sequenceNumber().equals(resp2.sequenceNumber());
        System.out.println("\n  Same SequenceNumber? " + isDuplicate);
        if (isDuplicate) {
            System.out.println("  ✓ DUPLICATE DETECTED! SQS accepted the API call (no error) but assigned");
            System.out.println("    the SAME SequenceNumber → only ONE copy exists in the queue.");
            System.out.println("    The consumer will see this message only ONCE.");
        }

        System.out.println("\n  Now sending message #3 with DIFFERENT body...");
        String differentBody = "{\"orderId\":\"ORD-999\",\"action\":\"charge\",\"amount\":49.99,\"retry\":true}";
        SendMessageResponse resp3 = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(contentBasedFifoUrl)
                .messageBody(differentBody)
                .messageGroupId("orders")
                .build());
        System.out.println("  Result: SequenceNumber=" + resp3.sequenceNumber());
        System.out.println("  Different SequenceNumber? " + !resp2.sequenceNumber().equals(resp3.sequenceNumber()));
        System.out.println("  ✓ Different body → different hash → treated as NEW message.\n");

        // ── PART B: Explicit MessageDeduplicationId ──
        System.out.println("  ══════════════════════════════════════════════════════════════");
        System.out.println("  PART B: Explicit MessageDeduplicationId (sqs-demo-dedup.fifo)");
        System.out.println("  Queue has ContentBasedDeduplication=false");
        System.out.println("  YOU provide a dedup ID → same ID within 5 min = duplicate");
        System.out.println("  ══════════════════════════════════════════════════════════════\n");

        String dedupId = "payment-ORD-777-attempt-1";

        System.out.println("  Sending message #1 with DeduplicationId=\"" + dedupId + "\"");
        SendMessageResponse respA = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(explicitDedupFifoUrl)
                .messageBody("{\"orderId\":\"ORD-777\",\"amount\":100}")
                .messageGroupId("payments")
                .messageDeduplicationId(dedupId)
                .build());
        System.out.println("  Result: SequenceNumber=" + respA.sequenceNumber());

        System.out.println("\n  Sending message #2 with SAME DeduplicationId but DIFFERENT body...");
        SendMessageResponse respB = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(explicitDedupFifoUrl)
                .messageBody("{\"orderId\":\"ORD-777\",\"amount\":200,\"note\":\"different body!\"}")
                .messageGroupId("payments")
                .messageDeduplicationId(dedupId)
                .build());
        System.out.println("  Result: SequenceNumber=" + respB.sequenceNumber());

        boolean dedupWorked = respA.sequenceNumber().equals(respB.sequenceNumber());
        System.out.println("\n  Same SequenceNumber? " + dedupWorked);
        if (dedupWorked) {
            System.out.println("  ✓ DUPLICATE DETECTED! Even though the BODY was different,");
            System.out.println("    the DeduplicationId was the same → SQS treated it as a duplicate.");
            System.out.println("    The second message body is IGNORED. Only the first copy exists.");
        }

        System.out.println("\n  Sending message #3 with DIFFERENT DeduplicationId...");
        String dedupId2 = "payment-ORD-777-attempt-2";
        SendMessageResponse respC = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(explicitDedupFifoUrl)
                .messageBody("{\"orderId\":\"ORD-777\",\"amount\":100}")
                .messageGroupId("payments")
                .messageDeduplicationId(dedupId2)
                .build());
        System.out.println("  Result: SequenceNumber=" + respC.sequenceNumber());
        System.out.println("  Different SequenceNumber? " + !respB.sequenceNumber().equals(respC.sequenceNumber()));
        System.out.println("  ✓ Different DeduplicationId → treated as NEW message (even with same body).\n");

        System.out.println("  ══════════════════════════════════════════════════════════════");
        System.out.println("  SUMMARY:");
        System.out.println("  ──────────────────────────────────────────────────────────────");
        System.out.println("  Content-Based:   same body    → duplicate (body hash is the dedup key)");
        System.out.println("  Explicit ID:     same dedup ID → duplicate (even if body differs!)");
        System.out.println("  Dedup window:    5 minutes (not configurable)");
        System.out.println("  After 5 min:     same message/ID is accepted again as NEW");
        System.out.println("  No error:        SQS returns 200 for duplicates (check SequenceNumber)");
        System.out.println("  ══════════════════════════════════════════════════════════════");
    }

    // ──────────────────────────────────────────────
    //  6) Send with delay (per-message delay)
    // ──────────────────────────────────────────────
    public void sendDelayedMessage(String queueUrl, int delaySeconds) {
        Map<String, String> payload = Map.of(
                "type", "delayed-notification",
                "scheduledFor", Instant.now().plusSeconds(delaySeconds).toString(),
                "message", "This message was delayed by " + delaySeconds + " seconds"
        );

        client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(gson.toJson(payload))
                .delaySeconds(delaySeconds)
                .build());

        System.out.println("  Sent delayed message (will be visible in " + delaySeconds + "s)");
        System.out.println("  Check the queue in AWS Console — it won't appear immediately!");
    }
}
