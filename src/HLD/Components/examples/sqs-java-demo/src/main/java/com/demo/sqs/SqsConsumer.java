package com.demo.sqs;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SqsConsumer {

    private final SqsClient client;

    public SqsConsumer(SqsClient client) {
        this.client = client;
    }

    // ──────────────────────────────────────────────
    //  1) Receive + process + delete (happy path)
    // ──────────────────────────────────────────────
    public void receiveAndProcess(String queueUrl, int maxMessages) {
        System.out.println("  Polling (long-poll 5s, max " + maxMessages + " msgs)...\n");

        ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(5)
                .messageAttributeNames("All")
                .attributeNamesWithStrings("All")
                .build());

        List<Message> messages = resp.messages();
        if (messages.isEmpty()) {
            System.out.println("  No messages available. Queue might be empty or messages are in-flight.");
            return;
        }

        System.out.println("  Received " + messages.size() + " message(s):\n");

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            System.out.println("  ─── Message " + (i + 1) + " ───");
            System.out.println("  MessageId      : " + msg.messageId());
            System.out.println("  Body           : " + msg.body());
            System.out.println("  ReceiptHandle  : " + msg.receiptHandle().substring(0, 40) + "...");
            System.out.println("  ApproxReceive# : " + msg.attributesAsStrings()
                    .getOrDefault("ApproximateReceiveCount", "?"));

            if (!msg.messageAttributes().isEmpty()) {
                System.out.println("  Attributes:");
                msg.messageAttributes().forEach((k, v) ->
                        System.out.println("    " + k + " = " + v.stringValue()));
            }

            // Simulate processing
            System.out.println("  [Processing...]");
            simulateWork(200);

            // Delete after successful processing
            client.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build());
            System.out.println("  [Deleted from queue]\n");
        }
    }

    // ──────────────────────────────────────────────
    //  2) Peek — receive but DON'T delete
    // ──────────────────────────────────────────────
    public void peekMessages(String queueUrl, int maxMessages) {
        System.out.println("  Peeking (messages will return to queue after visibility timeout)...\n");

        ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(3)
                .visibilityTimeout(5)  // short timeout so they come back quickly
                .messageAttributeNames("All")
                .attributeNamesWithStrings("All")
                .build());

        if (resp.messages().isEmpty()) {
            System.out.println("  No messages found.");
            return;
        }

        System.out.println("  Found " + resp.messages().size() + " message(s) (NOT deleted, will reappear in ~5s):\n");
        for (Message msg : resp.messages()) {
            System.out.println("  MessageId : " + msg.messageId());
            System.out.println("  Body      : " + msg.body());
            System.out.println("  Receive#  : " + msg.attributesAsStrings()
                    .getOrDefault("ApproximateReceiveCount", "?"));
            System.out.println();
        }
    }

    // ──────────────────────────────────────────────
    //  3) Demonstrate visibility timeout + DLQ
    //     Receive but intentionally don't delete.
    //     After maxReceiveCount (3), SQS moves to DLQ.
    // ──────────────────────────────────────────────
    public void simulateDlqFlow(String queueUrl, String dlqUrl) {
        System.out.println("  === DLQ Simulation ===");
        System.out.println("  Standard queue has maxReceiveCount=3 → after 3 receives without delete,");
        System.out.println("  SQS automatically moves the message to the DLQ.\n");

        System.out.println("  Step 1: Sending a 'poison pill' message...");
        client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("{\"type\":\"poison-pill\",\"reason\":\"simulating processing failure\"}")
                .build());

        for (int attempt = 1; attempt <= 4; attempt++) {
            System.out.println("\n  Step " + (attempt + 1) + ": Receive attempt #" + attempt + "...");

            ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(3)
                    .visibilityTimeout(2)  // very short so it comes back fast
                    .attributeNamesWithStrings("All")
                    .build());

            if (resp.messages().isEmpty()) {
                System.out.println("  No message received — might have moved to DLQ already!");
                break;
            }

            Message msg = resp.messages().get(0);
            String receiveCount = msg.attributesAsStrings()
                    .getOrDefault("ApproximateReceiveCount", "?");
            System.out.println("  Received! ApproximateReceiveCount=" + receiveCount);
            System.out.println("  [Simulating failure — NOT deleting the message]");

            // Wait for visibility timeout to expire
            System.out.println("  Waiting for visibility timeout to expire (2s)...");
            simulateWork(2500);
        }

        System.out.println("\n  Step 6: Checking DLQ for the poison pill...");
        simulateWork(2000);
        ReceiveMessageResponse dlqResp = client.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5)
                .build());

        if (!dlqResp.messages().isEmpty()) {
            System.out.println("  FOUND in DLQ! Message body: " + dlqResp.messages().get(0).body());
            System.out.println("\n  This confirms: message exceeded maxReceiveCount → moved to DLQ.");
            System.out.println("  Go check AWS Console → DLQ queue to see it there!");

            client.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .receiptHandle(dlqResp.messages().get(0).receiptHandle())
                    .build());
            System.out.println("  [Cleaned up — deleted from DLQ]");
        } else {
            System.out.println("  Message not in DLQ yet. SQS DLQ redrive can take a few seconds.");
            System.out.println("  Check the DLQ manually in AWS Console.");
        }
    }

    // ──────────────────────────────────────────────
    //  4) Receive from FIFO queue — shows ordering
    // ──────────────────────────────────────────────
    public void receiveFifo(String fifoQueueUrl) {
        System.out.println("  Receiving FIFO messages (ordered within each MessageGroupId)...\n");

        ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(fifoQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .attributeNamesWithStrings("All")
                .build());

        if (resp.messages().isEmpty()) {
            System.out.println("  No messages. Send FIFO messages first (option 5).");
            return;
        }

        for (int i = 0; i < resp.messages().size(); i++) {
            Message msg = resp.messages().get(i);
            String group = msg.attributesAsStrings().getOrDefault("MessageGroupId", "?");
            String seq = msg.attributesAsStrings().getOrDefault("SequenceNumber", "?");

            System.out.println("  #" + (i + 1)
                    + "  Group=" + group
                    + "  Seq=" + seq
                    + "  Body=" + msg.body());

                    // To simulate the visibility do not delete the message
           client.deleteMessage(DeleteMessageRequest.builder()
                   .queueUrl(fifoQueueUrl)
                   .receiptHandle(msg.receiptHandle())
                   .build());
        }
        System.out.println("\n  Notice: messages within the same group arrive in order!");
    }

    // ──────────────────────────────────────────────
    //  5) Get queue stats
    // ──────────────────────────────────────────────
    public void printQueueStats(String queueUrl, String queueName) {
        GetQueueAttributesResponse resp = client.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.ALL)
                        .build());

        System.out.println("  === " + queueName + " ===");
        var attrs = resp.attributesAsStrings();
        System.out.println("  Messages Available    : " + attrs.getOrDefault("ApproximateNumberOfMessages", "0"));
        System.out.println("  Messages In-Flight    : " + attrs.getOrDefault("ApproximateNumberOfMessagesNotVisible", "0"));
        System.out.println("  Messages Delayed      : " + attrs.getOrDefault("ApproximateNumberOfMessagesDelayed", "0"));
        System.out.println("  Visibility Timeout    : " + attrs.getOrDefault("VisibilityTimeout", "?") + "s");
        System.out.println("  Created               : " + attrs.getOrDefault("CreatedTimestamp", "?"));

        String redrive = attrs.get("RedrivePolicy");
        if (redrive != null) {
            System.out.println("  Redrive Policy        : " + redrive);
        }
        System.out.println();
    }

    // ──────────────────────────────────────────────
    //  6) Multiple competing consumers on same queue
    // ──────────────────────────────────────────────
    public void runCompetingConsumers(String queueUrl, int numConsumers, int durationSeconds) {
        System.out.println("  Launching " + numConsumers + " competing consumers for " + durationSeconds + "s...\n");

        AtomicInteger[] perConsumerCount = new AtomicInteger[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            perConsumerCount[i] = new AtomicInteger(0);
        }
        AtomicInteger totalProcessed = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(numConsumers);
        CountDownLatch startGate = new CountDownLatch(1);
        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;

        for (int c = 0; c < numConsumers; c++) {
            final int consumerId = c + 1;
            final AtomicInteger myCount = perConsumerCount[c];

            pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) { return; }

                while (System.currentTimeMillis() < deadline) {
                    try {
                        ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(5)
                                .waitTimeSeconds(2)
                                .attributeNamesWithStrings("All")
                                .build());

                        for (Message msg : resp.messages()) {
                            int count = myCount.incrementAndGet();
                            int total = totalProcessed.incrementAndGet();
                            String shortId = msg.messageId().substring(0, 8);

                            System.out.printf("  [Consumer-%d] msg #%d (total=%d) | id=%s... | body=%.50s%n",
                                    consumerId, count, total, shortId,
                                    msg.body().length() > 50 ? msg.body().substring(0, 50) : msg.body());

                            simulateWork(100 + (long)(Math.random() * 200));

                            client.deleteMessage(DeleteMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .receiptHandle(msg.receiptHandle())
                                    .build());
                        }
                    } catch (Exception e) {
                        System.out.println("  [Consumer-" + consumerId + "] Error: " + e.getMessage());
                    }
                }
            });
        }

        startGate.countDown();
        pool.shutdown();
        try { pool.awaitTermination(durationSeconds + 10, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}

        System.out.println("\n  ┌─────────────────────────────────────────┐");
        System.out.println("  │  COMPETING CONSUMERS — RESULTS          │");
        System.out.println("  ├─────────────────────────────────────────┤");
        for (int i = 0; i < numConsumers; i++) {
            System.out.printf("  │  Consumer-%d processed: %-5d messages  │%n", i + 1, perConsumerCount[i].get());
        }
        System.out.println("  ├─────────────────────────────────────────┤");
        System.out.printf("  │  TOTAL processed:       %-5d messages  │%n", totalProcessed.get());
        System.out.println("  └─────────────────────────────────────────┘");
        System.out.println("\n  KEY OBSERVATION: Each message went to exactly ONE consumer.");
        System.out.println("  SQS distributes messages across consumers — no duplication.");
        System.out.println("  This is the 'competing consumers' pattern (vs Kafka's 'consumer groups').");
    }

    // ──────────────────────────────────────────────
    //  7) Multiple consumers on FIFO — per-group locking
    // ──────────────────────────────────────────────
    public void runFifoCompetingConsumers(String fifoQueueUrl, int numConsumers, int durationSeconds) {
        System.out.println("  Launching " + numConsumers + " FIFO consumers for " + durationSeconds + "s...\n");

        ConcurrentHashMap<String, List<String>> consumerGroupMap = new ConcurrentHashMap<>();
        AtomicInteger totalProcessed = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(numConsumers);
        CountDownLatch startGate = new CountDownLatch(1);
        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;

        for (int c = 0; c < numConsumers; c++) {
            final int consumerId = c + 1;
            final String consumerName = "Consumer-" + consumerId;

            pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) { return; }

                while (System.currentTimeMillis() < deadline) {
                    try {
                        ReceiveMessageResponse resp = client.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(fifoQueueUrl)
                                .maxNumberOfMessages(5)
                                .waitTimeSeconds(2)
                                .attributeNamesWithStrings("All")
                                .build());

                        for (Message msg : resp.messages()) {
                            String group = msg.attributesAsStrings().getOrDefault("MessageGroupId", "?");
                            String seq = msg.attributesAsStrings().getOrDefault("SequenceNumber", "?");
                            int total = totalProcessed.incrementAndGet();

                            consumerGroupMap.computeIfAbsent(consumerName, k -> new CopyOnWriteArrayList<>())
                                    .add(group + ":" + seq);

                            System.out.printf("  [%s] total=%d | group=%-12s | seq=%s | body=%.40s%n",
                                    consumerName, total, group, seq,
                                    msg.body().length() > 40 ? msg.body().substring(0, 40) : msg.body());

                            simulateWork(150 + (long)(Math.random() * 150));

                            client.deleteMessage(DeleteMessageRequest.builder()
                                    .queueUrl(fifoQueueUrl)
                                    .receiptHandle(msg.receiptHandle())
                                    .build());
                        }
                    } catch (Exception e) {
                        System.out.println("  [Consumer-" + consumerId + "] Error: " + e.getMessage());
                    }
                }
            });
        }

        startGate.countDown();
        pool.shutdown();
        try { pool.awaitTermination(durationSeconds + 10, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}

        System.out.println("\n  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │  FIFO COMPETING CONSUMERS — RESULTS                         │");
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        consumerGroupMap.forEach((consumer, groups) -> {
            System.out.printf("  │  %-12s: %d msgs | groups: %s%n", consumer, groups.size(),
                    groups.stream().map(g -> g.split(":")[0]).distinct().toList());
        });
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        System.out.printf("  │  TOTAL processed: %d messages                               │%n", totalProcessed.get());
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println("\n  KEY OBSERVATIONS:");
        System.out.println("  1. Messages within the same group were processed IN ORDER (check seq numbers)");
        System.out.println("  2. Different groups could be processed by DIFFERENT consumers in PARALLEL");
        System.out.println("  3. While a message from group X is in-flight, no other consumer gets group X");
        System.out.println("  4. This is like Kafka: partition key → one consumer per partition");
    }

    private void simulateWork(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
    }
}
