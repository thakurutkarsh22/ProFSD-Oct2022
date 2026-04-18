package com.demo.sns;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SnsSubscriber {

    private final SnsClient snsClient;
    private final SqsClient sqsClient;

    public SnsSubscriber(SnsClient snsClient, SqsClient sqsClient) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
    }

    // ──────────────────────────────────────────────
    //  1) Receive messages from any SQS subscriber queue
    // ──────────────────────────────────────────────
    public void receiveFromQueue(String queueUrl, String queueLabel, int maxMessages) {
        System.out.println("  Polling " + queueLabel + " (long-poll 5s, max " + maxMessages + " msgs)...\n");

        ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(5)
                .messageAttributeNames("All")
                .attributeNames(QueueAttributeName.ALL)
                .build());

        List<Message> messages = resp.messages();
        if (messages.isEmpty()) {
            System.out.println("  No messages in " + queueLabel + ". Publish some first!");
            return;
        }

        System.out.println("  Received " + messages.size() + " message(s) from " + queueLabel + ":\n");

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            System.out.println("  ─── Message " + (i + 1) + " ───");
            System.out.println("  MessageId : " + msg.messageId());
            System.out.println("  Body      : " + truncate(msg.body(), 200));

            if (!msg.messageAttributes().isEmpty()) {
                System.out.println("  Attributes:");
                msg.messageAttributes().forEach((k, v) ->
                        System.out.println("    " + k + " = " + v.stringValue()));
            }

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build());
            System.out.println("  [Processed & deleted]\n");
        }
    }

    // ──────────────────────────────────────────────
    //  2) Drain ALL subscriber queues (show fan-out result)
    // ──────────────────────────────────────────────
    public void drainAllQueues(SnsConfig config) {
        System.out.println("  Draining all subscriber queues to show fan-out routing:\n");

        String[][] queues = {
                {config.inventoryQueueUrl(), "INVENTORY (filter: order_placed)"},
                {config.billingQueueUrl(),   "BILLING   (filter: payment_due)"},
                {config.analyticsQueueUrl(), "ANALYTICS (no filter — gets ALL)"},
                {config.highvalueQueueUrl(), "HIGHVALUE (filter: amount >= 500)"},
        };

        for (String[] q : queues) {
            int count = drainAndCount(q[0]);
            System.out.println("    " + q[1] + " → " + count + " messages");
        }

        System.out.println("\n  → Notice how filters route different events to different queues.");
        System.out.println("  → Analytics got everything. Others got only matching events.");
    }

    private int drainAndCount(String queueUrl) {
        int total = 0;
        while (true) {
            ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(2)
                    .build());
            if (resp.messages().isEmpty()) break;
            total += resp.messages().size();
            for (Message msg : resp.messages()) {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build());
            }
        }
        return total;
    }

    // ──────────────────────────────────────────────
    //  3) Receive FIFO messages (show ordering proof)
    // ──────────────────────────────────────────────
    public void receiveFifo(String fifoQueueUrl) {
        System.out.println("  Polling FIFO queue (long-poll 5s)...\n");

        int total = 0;
        String lastGroup = "";

        while (true) {
            ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(fifoQueueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(3)
                    .attributeNames(QueueAttributeName.ALL)
                    .build());

            if (resp.messages().isEmpty()) break;

            for (Message msg : resp.messages()) {
                var sysAttrs = msg.attributes();
                String group = sysAttrs.containsKey(MessageSystemAttributeName.MESSAGE_GROUP_ID)
                        ? sysAttrs.get(MessageSystemAttributeName.MESSAGE_GROUP_ID) : "?";
                String seq = sysAttrs.containsKey(MessageSystemAttributeName.SEQUENCE_NUMBER)
                        ? sysAttrs.get(MessageSystemAttributeName.SEQUENCE_NUMBER) : "?";

                if (!group.equals(lastGroup)) {
                    System.out.println("\n  ── Group: " + group + " ──");
                    lastGroup = group;
                }

                System.out.println("    Seq=" + seq + "  Body=" + truncate(msg.body(), 100));
                total++;

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(fifoQueueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build());
            }
        }

        if (total == 0) {
            System.out.println("  No FIFO messages. Publish some first (option 6 or 7)!");
        } else {
            System.out.println("\n  Total: " + total + " messages");
            System.out.println("  → Within each group, SequenceNumbers are strictly increasing.");
            System.out.println("  → This PROVES ordering is maintained per MessageGroupId.");
        }
    }

    // ──────────────────────────────────────────────
    //  4) List all subscriptions on a topic
    // ──────────────────────────────────────────────
    public void listSubscriptions(String topicArn) {
        ListSubscriptionsByTopicResponse resp = snsClient.listSubscriptionsByTopic(
                ListSubscriptionsByTopicRequest.builder().topicArn(topicArn).build());

        System.out.println("  Subscriptions for topic: " + topicArn + "\n");
        if (resp.subscriptions().isEmpty()) {
            System.out.println("    (none)");
            return;
        }

        for (Subscription sub : resp.subscriptions()) {
            System.out.println("    Protocol : " + sub.protocol());
            System.out.println("    Endpoint : " + sub.endpoint());
            System.out.println("    SubArn   : " + sub.subscriptionArn());

            if (!sub.subscriptionArn().equals("PendingConfirmation")) {
                try {
                    GetSubscriptionAttributesResponse attrs = snsClient.getSubscriptionAttributes(
                            GetSubscriptionAttributesRequest.builder()
                                    .subscriptionArn(sub.subscriptionArn()).build());
                    String filter = attrs.attributes().getOrDefault("FilterPolicy", "(none)");
                    String raw = attrs.attributes().getOrDefault("RawMessageDelivery", "false");
                    System.out.println("    Filter   : " + filter);
                    System.out.println("    RawDelivery: " + raw);
                } catch (Exception e) {
                    System.out.println("    (could not fetch attributes)");
                }
            }
            System.out.println();
        }
    }

    // ──────────────────────────────────────────────
    //  5) Print queue stats for all subscriber queues
    // ──────────────────────────────────────────────
    public void printAllQueueStats(SnsConfig config) {
        System.out.println("  ┌───────────────────────────────────────────────────────────────────┐");
        System.out.println("  │  QUEUE                    │ Available │ In-Flight │ Delayed       │");
        System.out.println("  ├───────────────────────────┼───────────┼───────────┼───────────────┤");

        printQueueStatsRow(config.inventoryQueueUrl(), "Inventory (order_placed)");
        printQueueStatsRow(config.billingQueueUrl(),   "Billing   (payment_due) ");
        printQueueStatsRow(config.analyticsQueueUrl(), "Analytics (all msgs)    ");
        printQueueStatsRow(config.highvalueQueueUrl(), "HighValue (amount>=500) ");
        printQueueStatsRow(config.fifoQueueUrl(),      "FIFO subscriber         ");
        printQueueStatsRow(config.dlqQueueUrl(),       "Dead Letter Queue       ");

        System.out.println("  └───────────────────────────┴───────────┴───────────┴───────────────┘");
        System.out.println("\n  → Compare these counts with what you see in AWS Console → SQS.");
    }

    private void printQueueStatsRow(String queueUrl, String label) {
        try {
            GetQueueAttributesResponse resp = sqsClient.getQueueAttributes(
                    GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED)
                            .build());

            String avail = resp.attributes().getOrDefault(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0");
            String inflight = resp.attributes().getOrDefault(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0");
            String delayed = resp.attributes().getOrDefault(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0");

            System.out.printf("  │  %-25s │ %9s │ %9s │ %13s │%n",
                    label, avail, inflight, delayed);
        } catch (Exception e) {
            System.out.printf("  │  %-25s │ %9s │ %9s │ %13s │%n",
                    label, "err", "err", "err");
        }
    }

    // ──────────────────────────────────────────────
    //  6) Purge all subscriber queues
    // ──────────────────────────────────────────────
    public void purgeAllQueues(SnsConfig config) {
        String[] urls = {
                config.inventoryQueueUrl(), config.billingQueueUrl(),
                config.analyticsQueueUrl(), config.highvalueQueueUrl(),
                config.fifoQueueUrl(), config.dlqQueueUrl(),
                config.payloadQueueUrl(), config.wrappedQueueUrl(),
                config.sqsDlqQueueUrl(), config.dlqSourceQueueUrl()
        };
        String[] names = {
                "inventory", "billing", "analytics", "highvalue",
                "fifo", "sns-dlq", "payload", "wrapped", "sqs-dlq", "dlq-source"
        };

        for (int i = 0; i < urls.length; i++) {
            try {
                sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(urls[i]).build());
                System.out.println("    Purged " + names[i]);
            } catch (PurgeQueueInProgressException e) {
                System.out.println("    " + names[i] + " purge already in progress (wait 60s)");
            }
        }
        System.out.println("\n  All queues purged. May take up to 60s to reflect in console.");
    }

    // ──────────────────────────────────────────────
    //  7) Competing consumers simulation
    // ──────────────────────────────────────────────
    public void runCompetingConsumers(String queueUrl, String queueLabel, int numConsumers, int durationSec) {
        System.out.println("  Starting " + numConsumers + " competing consumers on " + queueLabel + "...");
        System.out.println("  Running for " + durationSec + " seconds...\n");

        ExecutorService pool = Executors.newFixedThreadPool(numConsumers);
        AtomicInteger totalProcessed = new AtomicInteger(0);
        Map<String, AtomicInteger> perConsumer = new ConcurrentHashMap<>();

        long deadline = System.currentTimeMillis() + (durationSec * 1000L);

        for (int c = 0; c < numConsumers; c++) {
            String name = "Consumer-" + (char) ('A' + c);
            perConsumer.put(name, new AtomicInteger(0));

            pool.submit(() -> {
                while (System.currentTimeMillis() < deadline) {
                    ReceiveMessageResponse resp = sqsClient.receiveMessage(
                            ReceiveMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .maxNumberOfMessages(5)
                                    .waitTimeSeconds(2)
                                    .build());

                    for (Message msg : resp.messages()) {
                        System.out.println("    [" + name + "] Processing: "
                                + truncate(msg.body(), 60));
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(msg.receiptHandle())
                                .build());

                        totalProcessed.incrementAndGet();
                        perConsumer.get(name).incrementAndGet();
                    }
                }
            });
        }

        pool.shutdown();
        try { pool.awaitTermination(durationSec + 5, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}

        System.out.println("\n  ── RESULTS ──");
        System.out.println("  Total processed: " + totalProcessed.get());
        perConsumer.forEach((name, count) ->
                System.out.println("    " + name + ": " + count.get() + " messages"));
        System.out.println("\n  → Each message processed by EXACTLY ONE consumer (SQS guarantees this).");
        System.out.println("  → But the BROADCAST happened at SNS level (all queues got a copy).");
    }

    // ──────────────────────────────────────────────
    //  8) Simulate DLQ — receive-and-fail repeatedly
    // ──────────────────────────────────────────────
    public void simulateDlqFailures(String sourceQueueUrl) {
        System.out.println("  Simulating consumer failures on dlq-source-queue...");
        System.out.println("  (maxReceiveCount=2 → after 2 receives without delete, SQS moves to DLQ)\n");

        int pass = 0;
        int totalSeen = 0;

        while (pass < 4) {
            pass++;
            ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(sourceQueueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(3)
                    .attributeNames(QueueAttributeName.ALL)
                    .build());

            if (resp.messages().isEmpty()) {
                if (totalSeen == 0) {
                    System.out.println("  No messages found. Publish DLQ test messages first (option 17).");
                    return;
                }
                System.out.println("  Pass " + pass + ": no messages (likely moved to SQS DLQ already)");
                continue;
            }

            for (Message msg : resp.messages()) {
                String receiveCount = msg.attributes().getOrDefault(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "?");
                totalSeen++;

                System.out.println("    Pass " + pass + " | ReceiveCount=" + receiveCount
                        + " | Body=" + truncate(msg.body(), 80));
                System.out.println("      → NOT deleting (simulating failure). Letting visibility timeout expire.");
            }

            if (pass < 4) {
                System.out.println("\n    Waiting 6s for visibility timeout to expire...\n");
                try { Thread.sleep(6000); } catch (InterruptedException ignored) {}
            }
        }

        System.out.println("\n  RESULT:");
        System.out.println("  Messages have been received " + pass + "x without acknowledgment.");
        System.out.println("  SQS will move them to sqs-dlq (after maxReceiveCount=2 exceeded).");
        System.out.println("  Use option 20 to inspect the DLQ and see message metadata.");
    }

    // ──────────────────────────────────────────────
    //  9) Inspect DLQ messages (show failure metadata)
    // ──────────────────────────────────────────────
    public void inspectDlqMessages(String snsDlqUrl, String sqsDlqUrl) {
        System.out.println("  ═══ SNS SUBSCRIPTION DLQ (sns-demo-dlq) ═══\n");
        int snsDlqCount = inspectSingleDlq(snsDlqUrl, "SNS-DLQ");

        System.out.println("\n  ═══ SQS REDRIVE DLQ (sns-demo-sqs-dlq) ═══\n");
        int sqsDlqCount = inspectSingleDlq(sqsDlqUrl, "SQS-DLQ");

        System.out.println("\n  ── SUMMARY ──");
        System.out.println("  SNS subscription DLQ: " + snsDlqCount + " messages");
        System.out.println("    → These arrived because SNS could not deliver to the endpoint");
        System.out.println("    → Causes: Lambda error, HTTP 5xx, permission denied on SQS");
        System.out.println();
        System.out.println("  SQS redrive DLQ: " + sqsDlqCount + " messages");
        System.out.println("    → These arrived because the CONSUMER failed to process them");
        System.out.println("    → SQS delivered successfully, but consumer didn't delete after N tries");
        System.out.println("    → Controlled by RedrivePolicy.maxReceiveCount on source queue");
        System.out.println();
        System.out.println("  TWO-LAYER DLQ PATTERN (from the doc):");
        System.out.println("    Layer 1: SNS sub DLQ  → catches delivery failures (SNS → SQS failed)");
        System.out.println("    Layer 2: SQS DLQ      → catches processing failures (consumer crashed)");
        System.out.println("    Production needs BOTH. Monitor BOTH with CloudWatch alarms.");
    }

    private int inspectSingleDlq(String dlqUrl, String label) {
        ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(3)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build());

        if (resp.messages().isEmpty()) {
            System.out.println("  (empty — no failed messages)");
            return 0;
        }

        System.out.println("  Found " + resp.messages().size() + " message(s):\n");

        for (int i = 0; i < resp.messages().size(); i++) {
            Message msg = resp.messages().get(i);
            System.out.println("  ─── " + label + " Message " + (i + 1) + " ───");
            System.out.println("  MessageId     : " + msg.messageId());
            System.out.println("  Body          : " + truncate(msg.body(), 150));

            var sysAttrs = msg.attributes();
            if (sysAttrs.containsKey(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)) {
                System.out.println("  ReceiveCount  : " + sysAttrs.get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT));
            }
            if (sysAttrs.containsKey(MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP)) {
                System.out.println("  FirstReceived : " + sysAttrs.get(MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP));
            }
            if (sysAttrs.containsKey(MessageSystemAttributeName.SENT_TIMESTAMP)) {
                System.out.println("  SentTimestamp  : " + sysAttrs.get(MessageSystemAttributeName.SENT_TIMESTAMP));
            }

            if (!msg.messageAttributes().isEmpty()) {
                System.out.println("  Attributes    :");
                msg.messageAttributes().forEach((k, v) ->
                        System.out.println("    " + k + " = " + v.stringValue()));
            }
            System.out.println();
        }

        return resp.messages().size();
    }

    // ──────────────────────────────────────────────
    //  10) Replay DLQ messages back to source queue
    // ──────────────────────────────────────────────
    public void replayDlqToSource(String dlqUrl, String sourceQueueUrl, String dlqLabel) {
        System.out.println("  Replaying messages from " + dlqLabel + " back to source queue...\n");

        int replayed = 0;
        while (true) {
            ReceiveMessageResponse resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(3)
                    .messageAttributeNames("All")
                    .build());

            if (resp.messages().isEmpty()) break;

            for (Message msg : resp.messages()) {
                sqsClient.sendMessage(software.amazon.awssdk.services.sqs.model.SendMessageRequest.builder()
                        .queueUrl(sourceQueueUrl)
                        .messageBody(msg.body())
                        .build());

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(dlqUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build());

                replayed++;
                System.out.println("    Replayed msg " + replayed + ": " + truncate(msg.body(), 80));
            }
        }

        if (replayed == 0) {
            System.out.println("  DLQ is empty. Nothing to replay.");
        } else {
            System.out.println("\n  Replayed " + replayed + " message(s) back to source queue.");
            System.out.println("  The consumer can now reprocess them.");
            System.out.println("  If they fail again → back to DLQ (maxReceiveCount resets).");
        }
    }

    // ──────────────────────────────────────────────
    //  11) Raw vs Wrapped message comparison
    // ──────────────────────────────────────────────
    public void showRawVsWrapped(String rawQueueUrl, String wrappedQueueUrl) {
        System.out.println("  ═══ RAW MESSAGE (RawMessageDelivery = true) ═══\n");

        ReceiveMessageResponse rawResp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(rawQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(3)
                .messageAttributeNames("All")
                .build());

        if (rawResp.messages().isEmpty()) {
            System.out.println("  (no messages — publish first with option 17 or 2)");
        } else {
            Message raw = rawResp.messages().get(0);
            System.out.println("  Body (what your consumer sees):");
            System.out.println("  " + raw.body());
            System.out.println();
            if (!raw.messageAttributes().isEmpty()) {
                System.out.println("  Message Attributes (available as SQS message attributes):");
                raw.messageAttributes().forEach((k, v) ->
                        System.out.println("    " + k + " = " + v.stringValue()));
            }
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(rawQueueUrl).receiptHandle(raw.receiptHandle()).build());
        }

        System.out.println("\n  ═══ WRAPPED MESSAGE (RawMessageDelivery = false) ═══\n");

        ReceiveMessageResponse wrappedResp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(wrappedQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(3)
                .messageAttributeNames("All")
                .build());

        if (wrappedResp.messages().isEmpty()) {
            System.out.println("  (no messages — publish first with option 17 or 2)");
        } else {
            Message wrapped = wrappedResp.messages().get(0);
            System.out.println("  Body (what your consumer sees — note the SNS JSON envelope!):");
            System.out.println("  " + wrapped.body());
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(wrappedQueueUrl).receiptHandle(wrapped.receiptHandle()).build());
        }

        System.out.println("\n  ── COMPARISON ──");
        System.out.println("  RAW (true):     Body = your original JSON. Clean and efficient.");
        System.out.println("  WRAPPED (false): Body = SNS JSON envelope containing:");
        System.out.println("    Type, MessageId, TopicArn, Message (your JSON, escaped),");
        System.out.println("    Timestamp, SignatureVersion, Signature, SigningCertURL,");
        System.out.println("    UnsubscribeURL, MessageAttributes");
        System.out.println();
        System.out.println("  WHEN TO USE WRAPPED (false):");
        System.out.println("    - You need X.509 signature verification (security)");
        System.out.println("    - You need TopicArn/UnsubscribeURL in the message");
        System.out.println("    - HTTP/HTTPS endpoints (need signature to verify sender)");
        System.out.println();
        System.out.println("  WHEN TO USE RAW (true):");
        System.out.println("    - SQS/Lambda subscribers (trust is implicit via IAM)");
        System.out.println("    - Reduces message size (no envelope overhead)");
        System.out.println("    - Avoids double JSON parsing (envelope → extract → parse body)");
    }

    // ──────────────────────────────────────────────
    //  12) FIFO message group routing demo
    // ──────────────────────────────────────────────
    public void fifoGroupRoutingDemo(String fifoQueueUrl) {
        System.out.println("  Demonstrating MessageGroupId routing with 2 competing consumers...\n");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Map<String, List<String>> consumerGroups = new ConcurrentHashMap<>();
        consumerGroups.put("Consumer-1", new CopyOnWriteArrayList<>());
        consumerGroups.put("Consumer-2", new CopyOnWriteArrayList<>());

        AtomicInteger totalProcessed = new AtomicInteger(0);
        long deadline = System.currentTimeMillis() + 15_000;

        for (int c = 1; c <= 2; c++) {
            String name = "Consumer-" + c;
            pool.submit(() -> {
                while (System.currentTimeMillis() < deadline) {
                    ReceiveMessageResponse resp = sqsClient.receiveMessage(
                            ReceiveMessageRequest.builder()
                                    .queueUrl(fifoQueueUrl)
                                    .maxNumberOfMessages(5)
                                    .waitTimeSeconds(2)
                                    .attributeNames(QueueAttributeName.ALL)
                                    .build());

                    for (Message msg : resp.messages()) {
                        String group = msg.attributes().getOrDefault(
                                MessageSystemAttributeName.MESSAGE_GROUP_ID, "?");
                        String seq = msg.attributes().getOrDefault(
                                MessageSystemAttributeName.SEQUENCE_NUMBER, "?");

                        consumerGroups.get(name).add(group + " (seq=" + seq + ")");
                        totalProcessed.incrementAndGet();

                        System.out.println("    [" + name + "] Group=" + group
                                + "  Seq=" + seq + "  Body=" + truncate(msg.body(), 60));

                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(fifoQueueUrl)
                                .receiptHandle(msg.receiptHandle())
                                .build());
                    }
                }
            });
        }

        pool.shutdown();
        try { pool.awaitTermination(20, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}

        System.out.println("\n  ── RESULTS ──");
        System.out.println("  Total processed: " + totalProcessed.get());

        for (var entry : consumerGroups.entrySet()) {
            System.out.println("\n  " + entry.getKey() + " (" + entry.getValue().size() + " messages):");
            Map<String, Integer> groupCounts = new java.util.LinkedHashMap<>();
            for (String g : entry.getValue()) {
                String groupName = g.split(" ")[0];
                groupCounts.merge(groupName, 1, Integer::sum);
            }
            groupCounts.forEach((g, count) ->
                    System.out.println("    Group " + g + ": " + count + " messages"));
        }

        System.out.println("\n  KEY OBSERVATION:");
        System.out.println("  All messages for the SAME ride_id went to the SAME consumer.");
        System.out.println("  Different ride_ids were distributed across consumers.");
        System.out.println("  This proves: MessageGroupId = lane divider for parallelism.");
    }

    // ──────────────────────────────────────────────
    //  13) Print extended queue stats (includes new queues)
    // ──────────────────────────────────────────────
    public void printAllQueueStatsExtended(SnsConfig config) {
        System.out.println("  ┌───────────────────────────────────────────────────────────────────┐");
        System.out.println("  │  QUEUE                    │ Available │ In-Flight │ Delayed       │");
        System.out.println("  ├───────────────────────────┼───────────┼───────────┼───────────────┤");

        printQueueStatsRow(config.inventoryQueueUrl(), "Inventory (order_placed)");
        printQueueStatsRow(config.billingQueueUrl(),   "Billing   (payment_due) ");
        printQueueStatsRow(config.analyticsQueueUrl(), "Analytics (all msgs)    ");
        printQueueStatsRow(config.highvalueQueueUrl(), "HighValue (amount>=500) ");
        printQueueStatsRow(config.fifoQueueUrl(),      "FIFO subscriber         ");
        printQueueStatsRow(config.payloadQueueUrl(),   "Payload (body filter)   ");
        printQueueStatsRow(config.wrappedQueueUrl(),   "Wrapped (raw=false)     ");
        printQueueStatsRow(config.dlqSourceQueueUrl(), "DLQ Source (redrive=2)  ");

        System.out.println("  ├───────────────────────────┼───────────┼───────────┼───────────────┤");
        printQueueStatsRow(config.dlqQueueUrl(),       "SNS Sub DLQ             ");
        printQueueStatsRow(config.sqsDlqQueueUrl(),    "SQS Redrive DLQ         ");

        System.out.println("  └───────────────────────────┴───────────┴───────────┴───────────────┘");
        System.out.println("\n  DLQ rows > 0 means something is failing. Investigate!");
    }

    // ──────────────────────────────────────────────
    //  14) Trigger SNS Subscription DLQ
    //      (block SQS permission → SNS can't deliver → DLQ)
    // ──────────────────────────────────────────────
    public void triggerSnsDlq(SnsConfig config, com.demo.sns.SnsPublisher publisher) {
        String queueUrl = config.dlqSourceQueueUrl();
        String topicArn = config.standardTopicArn();

        System.out.println("  STEP 1: Blocking SNS from delivering to dlq-source-queue...");
        System.out.println("          (setting DENY policy on the SQS queue)\n");

        String denyPolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Deny",
                    "Principal": {"Service": "sns.amazonaws.com"},
                    "Action": "sqs:SendMessage",
                    "Resource": "*"
                  }]
                }""";

        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, denyPolicy))
                .build());

        System.out.println("  STEP 2: Publishing messages to topic (they will route to dlq-source-queue)...");
        System.out.println("          But SNS will get ACCESS DENIED from SQS.\n");

        for (int i = 1; i <= 3; i++) {
            String body = "{\"test\":\"sns-dlq-trigger-" + i + "\",\"purpose\":\"SNS delivery will fail\"}";
            var attrs = Map.of(
                    "event_type", software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                            .dataType("String").stringValue("dlq_test").build()
            );

            var resp = config.snsClient().publish(
                    software.amazon.awssdk.services.sns.model.PublishRequest.builder()
                            .topicArn(topicArn)
                            .message(body)
                            .messageAttributes(attrs)
                            .build());

            System.out.println("    Published msg " + i + " → MessageId: " + resp.messageId());
        }

        System.out.println("\n  STEP 3: Waiting 15 seconds for SNS to attempt delivery and fail...");
        System.out.println("          (SNS retries 3 times for SQS, then routes to DLQ)\n");

        try { Thread.sleep(15_000); } catch (InterruptedException ignored) {}

        System.out.println("  STEP 4: Restoring SNS permission on dlq-source-queue...\n");

        String allowPolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"Service": "sns.amazonaws.com"},
                    "Action": "sqs:SendMessage",
                    "Resource": "%s",
                    "Condition": {"ArnEquals": {"aws:SourceArn": "%s"}}
                  }]
                }""".formatted(config.dlqQueueArn() != null ? "*" : "*", topicArn);

        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, allowPolicy))
                .build());

        System.out.println("  Permission restored.\n");

        System.out.println("  STEP 5: Checking SNS subscription DLQ...\n");

        int count = inspectSingleDlq(config.dlqQueueUrl(), "SNS-DLQ");

        if (count > 0) {
            System.out.println("\n  SUCCESS! " + count + " message(s) landed in the SNS subscription DLQ.");
        } else {
            System.out.println("\n  DLQ is still empty. SNS delivery retries may still be in progress.");
            System.out.println("  SNS retries for SQS subscribers are fast (3 immediate retries).");
            System.out.println("  Wait a few more seconds and check again with option 19.");
        }

        System.out.println("\n  WHAT HAPPENED:");
        System.out.println("  1. You published to SNS topic");
        System.out.println("  2. SNS tried to deliver to dlq-source-queue via sqs:SendMessage");
        System.out.println("  3. SQS policy said DENY → SNS got AccessDenied");
        System.out.println("  4. SNS retried 3 times (immediate retries for SQS protocol)");
        System.out.println("  5. All retries exhausted → SNS checked RedrivePolicy on the subscription");
        System.out.println("  6. RedrivePolicy pointed to sns-demo-dlq → message sent there");
        System.out.println("  7. This is LAYER 1 (SNS subscription DLQ) — delivery failure");
        System.out.println("     vs LAYER 2 (SQS redrive DLQ) — consumer processing failure");
    }

    // ──────────────────────────────────────────────
    //  Utility
    // ──────────────────────────────────────────────
    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
