package com.demo.sns;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Bootstraps SNS + SQS clients, auto-creates topics, queues, subscriptions,
 * and filter policies. Uses default credential chain (~/.aws/credentials).
 *
 * Creates:
 *   TOPICS:
 *     sns-demo-orders         — Standard topic for fan-out & filtering demos
 *     sns-demo-fifo.fifo      — FIFO topic for ordering & exactly-once demos
 *
 *   SQS QUEUES (as subscribers):
 *     sns-demo-inventory-queue   — filter: event_type = order_placed
 *     sns-demo-billing-queue     — filter: event_type = payment_due
 *     sns-demo-analytics-queue   — no filter (gets ALL)
 *     sns-demo-highvalue-queue   — filter: amount >= 500
 *     sns-demo-fifo-queue.fifo   — FIFO topic subscriber
 *     sns-demo-payload-queue     — payload-based filter (body JSON matching)
 *     sns-demo-wrapped-queue     — raw delivery OFF (shows SNS JSON envelope)
 *     sns-demo-dlq               — SNS subscription DLQ
 *     sns-demo-sqs-dlq           — SQS redrive DLQ (two-layer DLQ pattern)
 *     sns-demo-dlq-source-queue  — source queue with SQS redrive → sqs-dlq
 */
public class SnsConfig {

    private static final Region REGION = Region.AP_SOUTH_1;

    public static final String STANDARD_TOPIC = "sns-demo-orders";
    public static final String FIFO_TOPIC     = "sns-demo-fifo.fifo";

    public static final String INVENTORY_QUEUE      = "sns-demo-inventory-queue";
    public static final String BILLING_QUEUE        = "sns-demo-billing-queue";
    public static final String ANALYTICS_QUEUE      = "sns-demo-analytics-queue";
    public static final String HIGHVALUE_QUEUE      = "sns-demo-highvalue-queue";
    public static final String FIFO_SQS_QUEUE       = "sns-demo-fifo-queue.fifo";
    public static final String DLQ_QUEUE            = "sns-demo-dlq";
    public static final String PAYLOAD_FILTER_QUEUE = "sns-demo-payload-queue";
    public static final String WRAPPED_QUEUE        = "sns-demo-wrapped-queue";
    public static final String SQS_DLQ_QUEUE        = "sns-demo-sqs-dlq";
    public static final String DLQ_SOURCE_QUEUE     = "sns-demo-dlq-source-queue";

    private final SnsClient snsClient;
    private final SqsClient sqsClient;

    private String standardTopicArn, fifoTopicArn;

    private String inventoryQueueUrl, inventoryQueueArn;
    private String billingQueueUrl, billingQueueArn;
    private String analyticsQueueUrl, analyticsQueueArn;
    private String highvalueQueueUrl, highvalueQueueArn;
    private String fifoQueueUrl, fifoQueueArn;
    private String dlqQueueUrl, dlqQueueArn;
    private String payloadQueueUrl, payloadQueueArn;
    private String wrappedQueueUrl, wrappedQueueArn;
    private String sqsDlqQueueUrl, sqsDlqQueueArn;
    private String dlqSourceQueueUrl, dlqSourceQueueArn;

    private String inventorySubArn, billingSubArn, analyticsSubArn, highvalueSubArn;
    private String fifoSubArn, payloadSubArn, wrappedSubArn, dlqSourceSubArn;

    public SnsConfig() {
        this.snsClient = SnsClient.builder().region(REGION).build();
        this.sqsClient = SqsClient.builder().region(REGION).build();
    }

    public void initialize() {
        System.out.println("=== Initializing SNS topics + SQS subscriber queues in " + REGION + " ===\n");

        // 1. Create DLQs first
        dlqQueueUrl = ensureSqsQueue(DLQ_QUEUE, false);
        dlqQueueArn = getSqsArn(dlqQueueUrl);

        sqsDlqQueueUrl = ensureSqsQueue(SQS_DLQ_QUEUE, false);
        sqsDlqQueueArn = getSqsArn(sqsDlqQueueUrl);

        // 2. Create subscriber queues
        inventoryQueueUrl = ensureSqsQueue(INVENTORY_QUEUE, false);
        inventoryQueueArn = getSqsArn(inventoryQueueUrl);

        billingQueueUrl = ensureSqsQueue(BILLING_QUEUE, false);
        billingQueueArn = getSqsArn(billingQueueUrl);

        analyticsQueueUrl = ensureSqsQueue(ANALYTICS_QUEUE, false);
        analyticsQueueArn = getSqsArn(analyticsQueueUrl);

        highvalueQueueUrl = ensureSqsQueue(HIGHVALUE_QUEUE, false);
        highvalueQueueArn = getSqsArn(highvalueQueueUrl);

        fifoQueueUrl = ensureSqsQueue(FIFO_SQS_QUEUE, true);
        fifoQueueArn = getSqsArn(fifoQueueUrl);

        payloadQueueUrl = ensureSqsQueue(PAYLOAD_FILTER_QUEUE, false);
        payloadQueueArn = getSqsArn(payloadQueueUrl);

        wrappedQueueUrl = ensureSqsQueue(WRAPPED_QUEUE, false);
        wrappedQueueArn = getSqsArn(wrappedQueueUrl);

        dlqSourceQueueUrl = ensureSqsQueueWithRedrive(DLQ_SOURCE_QUEUE, sqsDlqQueueArn, 2);
        dlqSourceQueueArn = getSqsArn(dlqSourceQueueUrl);

        // 3. Create SNS topics
        standardTopicArn = ensureSnsTopic(STANDARD_TOPIC, false);
        fifoTopicArn     = ensureSnsTopic(FIFO_TOPIC, true);

        // 4. Set SQS policies (allow SNS to send messages)
        allowSnsToSqs(inventoryQueueUrl, inventoryQueueArn, standardTopicArn);
        allowSnsToSqs(billingQueueUrl, billingQueueArn, standardTopicArn);
        allowSnsToSqs(analyticsQueueUrl, analyticsQueueArn, standardTopicArn);
        allowSnsToSqs(highvalueQueueUrl, highvalueQueueArn, standardTopicArn);
        allowSnsToSqs(fifoQueueUrl, fifoQueueArn, fifoTopicArn);
        allowSnsToSqs(payloadQueueUrl, payloadQueueArn, standardTopicArn);
        allowSnsToSqs(wrappedQueueUrl, wrappedQueueArn, standardTopicArn);
        allowSnsToSqs(dlqSourceQueueUrl, dlqSourceQueueArn, standardTopicArn);

        // 5. Subscribe queues to topics with filter policies
        inventorySubArn = subscribeWithFilter(standardTopicArn, inventoryQueueArn,
                "{\"event_type\": [\"order_placed\"]}", true);

        billingSubArn = subscribeWithFilter(standardTopicArn, billingQueueArn,
                "{\"event_type\": [\"payment_due\"]}", true);

        analyticsSubArn = subscribeNoFilter(standardTopicArn, analyticsQueueArn, true);

        highvalueSubArn = subscribeWithFilter(standardTopicArn, highvalueQueueArn,
                "{\"amount\": [{\"numeric\": [\">=\", 500]}]}", true);

        fifoSubArn = subscribeNoFilter(fifoTopicArn, fifoQueueArn, true);

        payloadSubArn = subscribeWithPayloadFilter(standardTopicArn, payloadQueueArn,
                "{\"source\": [\"mobile-app\"]}", true);

        wrappedSubArn = subscribeNoFilter(standardTopicArn, wrappedQueueArn, false);

        dlqSourceSubArn = subscribeWithFilter(standardTopicArn, dlqSourceQueueArn,
                "{\"event_type\": [\"dlq_test\"]}", true);

        // 6. Attach SNS subscription DLQs
        attachDlqToSubscription(analyticsSubArn);
        attachDlqToSubscription(dlqSourceSubArn);

        printSummary();
    }

    // ─── SNS Topic creation ────────────────────────

    private String ensureSnsTopic(String name, boolean fifo) {
        var topics = snsClient.listTopics().topics();
        for (Topic t : topics) {
            if (t.topicArn().endsWith(":" + name)) {
                System.out.println("  Found topic  : " + name);
                return t.topicArn();
            }
        }
        var reqBuilder = CreateTopicRequest.builder().name(name);
        if (fifo) {
            reqBuilder.attributes(Map.of(
                    "FifoTopic", "true",
                    "ContentBasedDeduplication", "true"
            ));
        }
        String arn = snsClient.createTopic(reqBuilder.build()).topicArn();
        System.out.println("  Created topic: " + name + " → " + arn);
        return arn;
    }

    // ─── SQS Queue creation ────────────────────────

    private String ensureSqsQueue(String name, boolean fifo) {
        try {
            return sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(name).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            var attrs = new HashMap<QueueAttributeName, String>();
            if (fifo) {
                attrs.put(QueueAttributeName.FIFO_QUEUE, "true");
                attrs.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");
            }
            String url = sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(name).attributes(attrs).build()).queueUrl();
            System.out.println("  Created queue: " + name);
            return url;
        }
    }

    private String ensureSqsQueueWithRedrive(String name, String dlqArn, int maxReceiveCount) {
        try {
            String url = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(name).build()).queueUrl();
            sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(url)
                    .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":%d}"
                                    .formatted(dlqArn, maxReceiveCount)))
                    .build());
            return url;
        } catch (QueueDoesNotExistException e) {
            String url = sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(name)
                    .attributes(Map.of(
                            QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":%d}"
                                    .formatted(dlqArn, maxReceiveCount),
                            QueueAttributeName.VISIBILITY_TIMEOUT, "5"
                    ))
                    .build()).queueUrl();
            System.out.println("  Created queue: " + name + " (redrive → " + SQS_DLQ_QUEUE + " after " + maxReceiveCount + " failures)");
            return url;
        }
    }

    private String getSqsArn(String queueUrl) {
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    // ─── SQS Policy (allow SNS → SQS) ─────────────

    private void allowSnsToSqs(String queueUrl, String queueArn, String topicArn) {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"Service": "sns.amazonaws.com"},
                    "Action": "sqs:SendMessage",
                    "Resource": "%s",
                    "Condition": {"ArnEquals": {"aws:SourceArn": "%s"}}
                  }]
                }""".formatted(queueArn, topicArn);

        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, policy))
                .build());
    }

    // ─── SNS Subscriptions ─────────────────────────

    private String subscribeWithFilter(String topicArn, String queueArn, String filterPolicy, boolean raw) {
        SubscribeResponse resp = snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of(
                        "FilterPolicy", filterPolicy,
                        "RawMessageDelivery", String.valueOf(raw)
                ))
                .build());
        return resp.subscriptionArn();
    }

    private String subscribeNoFilter(String topicArn, String queueArn, boolean raw) {
        SubscribeResponse resp = snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", String.valueOf(raw)))
                .build());
        return resp.subscriptionArn();
    }

    private String subscribeWithPayloadFilter(String topicArn, String queueArn, String filterPolicy, boolean raw) {
        SubscribeResponse resp = snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of(
                        "FilterPolicy", filterPolicy,
                        "FilterPolicyScope", "MessageBody",
                        "RawMessageDelivery", String.valueOf(raw)
                ))
                .build());
        return resp.subscriptionArn();
    }

    private void attachDlqToSubscription(String subscriptionArn) {
        if (subscriptionArn == null || subscriptionArn.equals("PendingConfirmation")) return;
        try {
            snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .attributeName("RedrivePolicy")
                    .attributeValue("{\"deadLetterTargetArn\":\"%s\"}".formatted(dlqQueueArn))
                    .build());
        } catch (Exception e) {
            System.out.println("  ⚠ Could not attach DLQ to subscription: " + e.getMessage());
        }
    }

    // ─── Summary ───────────────────────────────────

    private void printSummary() {
        System.out.println("""
                
                  ┌─────────────────────────────────────────────────────────────┐
                  │  INFRASTRUCTURE CREATED                                      │
                  ├─────────────────────────────────────────────────────────────┤
                  │                                                             │
                  │  TOPICS:                                                     │
                  │    Standard : %s
                  │    FIFO     : %s
                  │                                                             │
                  │  SQS SUBSCRIBER QUEUES (attribute-based filters):            │
                  │    Inventory   : filter = event_type:order_placed            │
                  │    Billing     : filter = event_type:payment_due             │
                  │    Analytics   : no filter (gets ALL messages)               │
                  │    HighValue   : filter = amount >= 500                      │
                  │    FIFO        : subscribes to FIFO topic                   │
                  │                                                             │
                  │  NEW QUEUES (advanced demos):                                │
                  │    Payload     : payload-based filter (body: source=mobile)  │
                  │    Wrapped     : raw delivery OFF (shows SNS JSON envelope) │
                  │    DLQ-Source  : SQS redrive → sqs-dlq after 2 failures    │
                  │                                                             │
                  │  DEAD-LETTER QUEUES:                                         │
                  │    SNS DLQ     : catches failed SNS → subscriber deliveries │
                  │    SQS DLQ     : catches SQS consumer processing failures  │
                  │                                                             │
                  └─────────────────────────────────────────────────────────────┘
                """.formatted(standardTopicArn, fifoTopicArn));
    }

    // ─── Getters ───────────────────────────────────

    public SnsClient snsClient()         { return snsClient; }
    public SqsClient sqsClient()         { return sqsClient; }

    public String standardTopicArn()     { return standardTopicArn; }
    public String fifoTopicArn()         { return fifoTopicArn; }

    public String inventoryQueueUrl()    { return inventoryQueueUrl; }
    public String billingQueueUrl()      { return billingQueueUrl; }
    public String analyticsQueueUrl()    { return analyticsQueueUrl; }
    public String highvalueQueueUrl()    { return highvalueQueueUrl; }
    public String fifoQueueUrl()         { return fifoQueueUrl; }
    public String dlqQueueUrl()          { return dlqQueueUrl; }
    public String payloadQueueUrl()      { return payloadQueueUrl; }
    public String wrappedQueueUrl()      { return wrappedQueueUrl; }
    public String sqsDlqQueueUrl()       { return sqsDlqQueueUrl; }
    public String dlqSourceQueueUrl()    { return dlqSourceQueueUrl; }

    public String dlqQueueArn()          { return dlqQueueArn; }
    public String sqsDlqQueueArn()       { return sqsDlqQueueArn; }

    public String inventorySubArn()      { return inventorySubArn; }
    public String billingSubArn()        { return billingSubArn; }
    public String analyticsSubArn()      { return analyticsSubArn; }
    public String highvalueSubArn()      { return highvalueSubArn; }
    public String dlqSourceSubArn()      { return dlqSourceSubArn; }

    public void close() {
        snsClient.close();
        sqsClient.close();
    }
}
