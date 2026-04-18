package com.demo.sns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.time.Instant;
import java.util.*;

public class SnsPublisher {

    private final SnsClient client;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SnsPublisher(SnsClient client) {
        this.client = client;
    }

    // ──────────────────────────────────────────────
    //  1) Publish a single message to Standard topic
    // ──────────────────────────────────────────────
    public void publishSingle(String topicArn, String body) {
        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(body)
                .build());

        System.out.println("  Published message:");
        System.out.println("    MessageId : " + resp.messageId());
        System.out.println("    Body      : " + body);
        System.out.println("\n  → This message was pushed to ALL subscribers (no filter on this publish).");
        System.out.println("  → Check analytics-queue (gets everything). Inventory/billing depend on filters.");
    }

    // ──────────────────────────────────────────────
    //  2) Publish with message attributes (for filtering)
    // ──────────────────────────────────────────────
    public void publishOrderPlaced(String topicArn) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "customerId", "CUST-42",
                "amount", 129.99,
                "items", List.of("laptop-stand", "usb-hub"),
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> attrs = Map.of(
                "event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("order_placed").build(),
                "amount", MessageAttributeValue.builder()
                        .dataType("Number").stringValue("129.99").build(),
                "region", MessageAttributeValue.builder()
                        .dataType("String").stringValue("us-east-1").build()
        );

        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(gson.toJson(order))
                .messageAttributes(attrs)
                .build());

        System.out.println("  Published order event:");
        System.out.println("    MessageId  : " + resp.messageId());
        System.out.println("    OrderId    : " + orderId);
        System.out.println("    Amount     : $129.99");
        System.out.println("    Attributes : event_type=order_placed, amount=129.99, region=us-east-1");
        System.out.println("\n  FILTER ROUTING:");
        System.out.println("    ✓ inventory-queue  → YES (event_type matches 'order_placed')");
        System.out.println("    ✗ billing-queue    → NO  (filter wants 'payment_due')");
        System.out.println("    ✓ analytics-queue  → YES (no filter, gets everything)");
        System.out.println("    ✗ highvalue-queue  → NO  (amount 129.99 < 500)");
    }

    // ──────────────────────────────────────────────
    //  3) Publish payment_due event (reaches billing queue)
    // ──────────────────────────────────────────────
    public void publishPaymentDue(String topicArn) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payment = Map.of(
                "orderId", orderId,
                "amount", 299.99,
                "currency", "USD",
                "dueDate", "2026-04-20",
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> attrs = Map.of(
                "event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("payment_due").build(),
                "amount", MessageAttributeValue.builder()
                        .dataType("Number").stringValue("299.99").build()
        );

        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(gson.toJson(payment))
                .messageAttributes(attrs)
                .build());

        System.out.println("  Published payment_due event:");
        System.out.println("    MessageId  : " + resp.messageId());
        System.out.println("    OrderId    : " + orderId);
        System.out.println("    Amount     : $299.99");
        System.out.println("\n  FILTER ROUTING:");
        System.out.println("    ✗ inventory-queue  → NO  (filter wants 'order_placed')");
        System.out.println("    ✓ billing-queue    → YES (event_type matches 'payment_due')");
        System.out.println("    ✓ analytics-queue  → YES (no filter)");
        System.out.println("    ✗ highvalue-queue  → NO  (amount 299.99 < 500)");
    }

    // ──────────────────────────────────────────────
    //  4) Publish high-value order (reaches highvalue queue)
    // ──────────────────────────────────────────────
    public void publishHighValueOrder(String topicArn) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        double amount = 1500.00 + new Random().nextInt(5000);

        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "customerId", "CUST-VIP-7",
                "amount", amount,
                "items", List.of("macbook-pro", "airpods-max", "magic-keyboard"),
                "flagged", "high-value-review",
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> attrs = Map.of(
                "event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("order_placed").build(),
                "amount", MessageAttributeValue.builder()
                        .dataType("Number").stringValue(String.valueOf(amount)).build()
        );

        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(gson.toJson(order))
                .messageAttributes(attrs)
                .build());

        System.out.println("  Published HIGH-VALUE order:");
        System.out.println("    MessageId  : " + resp.messageId());
        System.out.println("    OrderId    : " + orderId);
        System.out.println("    Amount     : $" + String.format("%.2f", amount));
        System.out.println("\n  FILTER ROUTING:");
        System.out.println("    ✓ inventory-queue  → YES (event_type = order_placed)");
        System.out.println("    ✗ billing-queue    → NO  (filter wants 'payment_due')");
        System.out.println("    ✓ analytics-queue  → YES (no filter)");
        System.out.println("    ✓ highvalue-queue  → YES (amount $" + String.format("%.2f", amount) + " >= 500)");
    }

    // ──────────────────────────────────────────────
    //  5) Publish batch (up to 10 messages)
    // ──────────────────────────────────────────────
    public void publishBatch(String topicArn, int count) {
        int n = Math.min(count, 10);
        List<PublishBatchRequestEntry> entries = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String eventType = (i % 2 == 0) ? "order_placed" : "payment_due";
            double amount = 50 + (i * 100);
            entries.add(PublishBatchRequestEntry.builder()
                    .id("msg-" + i)
                    .message("{\"batch_item\":" + (i + 1) + ",\"event\":\"" + eventType + "\",\"amount\":" + amount + "}")
                    .messageAttributes(Map.of(
                            "event_type", MessageAttributeValue.builder()
                                    .dataType("String").stringValue(eventType).build(),
                            "amount", MessageAttributeValue.builder()
                                    .dataType("Number").stringValue(String.valueOf(amount)).build()
                    ))
                    .build());
        }

        PublishBatchResponse resp = client.publishBatch(PublishBatchRequest.builder()
                .topicArn(topicArn)
                .publishBatchRequestEntries(entries)
                .build());

        System.out.println("  Batch published " + resp.successful().size() + " messages:");
        resp.successful().forEach(s ->
                System.out.println("    ✓ id=" + s.id() + "  MessageId=" + s.messageId()));

        if (!resp.failed().isEmpty()) {
            System.out.println("  Failed:");
            resp.failed().forEach(f ->
                    System.out.println("    ✗ id=" + f.id() + "  error=" + f.message()));
        }

        System.out.println("\n  PublishBatch sends up to 10 msgs in ONE API call.");
        System.out.println("  Cost: 1 API call instead of " + n + ". That's a " + n + "x cost reduction!");
    }

    // ──────────────────────────────────────────────
    //  6) Publish to FIFO topic (ordered messages)
    // ──────────────────────────────────────────────
    public void publishFifoOrdered(String fifoTopicArn) {
        String[] customers = {"customer-A", "customer-B"};
        String[] steps = {"place-order", "validate-payment", "charge-card", "reserve-inventory", "ship"};

        System.out.println("  Publishing ordered events for 2 customers (5 steps each):\n");

        for (String customer : customers) {
            for (int i = 0; i < steps.length; i++) {
                Map<String, Object> event = Map.of(
                        "customer", customer,
                        "action", steps[i],
                        "step", i + 1,
                        "timestamp", Instant.now().toString()
                );

                PublishResponse resp = client.publish(PublishRequest.builder()
                        .topicArn(fifoTopicArn)
                        .message(gson.toJson(event))
                        .messageGroupId(customer)
                        .build());

                System.out.println("    [" + customer + "] Step " + (i + 1) + ": " + steps[i]
                        + "  (SequenceNumber=" + resp.sequenceNumber() + ")");
            }
            System.out.println();
        }

        System.out.println("  KEY INSIGHT:");
        System.out.println("  → customer-A's steps are ORDERED (1→2→3→4→5)");
        System.out.println("  → customer-B's steps are ORDERED (1→2→3→4→5)");
        System.out.println("  → But A and B are INDEPENDENT (processed in parallel)");
        System.out.println("  → MessageGroupId = customer ID → natural sharding");
    }

    // ──────────────────────────────────────────────
    //  7) FIFO Deduplication demo
    // ──────────────────────────────────────────────
    public void publishFifoDedupDemo(String fifoTopicArn) {
        String dedupId = "dedup-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"event\":\"payment_confirmed\",\"orderId\":\"ORD-999\"}";

        System.out.println("  Sending SAME message TWICE with identical dedup ID...\n");
        System.out.println("    DeduplicationId: " + dedupId);
        System.out.println("    Body: " + body + "\n");

        PublishResponse resp1 = client.publish(PublishRequest.builder()
                .topicArn(fifoTopicArn)
                .message(body)
                .messageGroupId("dedup-test")
                .messageDeduplicationId(dedupId)
                .build());
        System.out.println("    Publish #1 → MessageId: " + resp1.messageId()
                + "  Seq: " + resp1.sequenceNumber());

        PublishResponse resp2 = client.publish(PublishRequest.builder()
                .topicArn(fifoTopicArn)
                .message(body)
                .messageGroupId("dedup-test")
                .messageDeduplicationId(dedupId)
                .build());
        System.out.println("    Publish #2 → MessageId: " + resp2.messageId()
                + "  Seq: " + resp2.sequenceNumber());

        System.out.println("\n  RESULT:");
        if (resp1.sequenceNumber().equals(resp2.sequenceNumber())) {
            System.out.println("    ✓ Same SequenceNumber! Second message was DEDUPLICATED.");
            System.out.println("    → SNS accepted it (no error) but did NOT deliver it.");
        } else {
            System.out.println("    Different SequenceNumbers — both were delivered.");
            System.out.println("    (This can happen if >5 min passed or dedup scope is per-group)");
        }
        System.out.println("    → 5-minute dedup window. Same dedupId within 5 min = silently dropped.");
    }

    // ──────────────────────────────────────────────
    //  8) Fan-out burst (send multiple types rapidly)
    // ──────────────────────────────────────────────
    public void publishFanoutBurst(String topicArn) {
        System.out.println("  Sending 6 events of mixed types to demonstrate fan-out routing:\n");

        record EventDef(String type, double amount) {}
        List<EventDef> events = List.of(
                new EventDef("order_placed", 89.99),
                new EventDef("payment_due", 199.00),
                new EventDef("order_placed", 750.00),
                new EventDef("order_placed", 2500.00),
                new EventDef("payment_due", 45.50),
                new EventDef("order_placed", 320.00)
        );

        int inventoryCount = 0, billingCount = 0, analyticsCount = 0, highvalueCount = 0;

        for (int i = 0; i < events.size(); i++) {
            EventDef e = events.get(i);
            Map<String, MessageAttributeValue> attrs = Map.of(
                    "event_type", MessageAttributeValue.builder()
                            .dataType("String").stringValue(e.type).build(),
                    "amount", MessageAttributeValue.builder()
                            .dataType("Number").stringValue(String.valueOf(e.amount)).build()
            );

            PublishResponse resp = client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message("{\"event\":" + (i + 1) + ",\"type\":\"" + e.type
                            + "\",\"amount\":" + e.amount + "}")
                    .messageAttributes(attrs)
                    .build());

            boolean toInventory = e.type.equals("order_placed");
            boolean toBilling   = e.type.equals("payment_due");
            boolean toHighValue = e.amount >= 500;

            if (toInventory) inventoryCount++;
            if (toBilling) billingCount++;
            analyticsCount++;
            if (toHighValue) highvalueCount++;

            System.out.println("    Event " + (i + 1) + ": " + e.type + " $" + e.amount
                    + "  → INV:" + (toInventory ? "✓" : "✗")
                    + " BILL:" + (toBilling ? "✓" : "✗")
                    + " ANALYTICS:✓"
                    + " HIGH$:" + (toHighValue ? "✓" : "✗"));
        }

        System.out.println("\n  EXPECTED QUEUE COUNTS:");
        System.out.println("    inventory-queue : " + inventoryCount + " messages");
        System.out.println("    billing-queue   : " + billingCount + " messages");
        System.out.println("    analytics-queue : " + analyticsCount + " messages (ALL)");
        System.out.println("    highvalue-queue : " + highvalueCount + " messages");
        System.out.println("\n  → Verify with option 12 (queue stats) or in AWS Console!");
    }

    // ──────────────────────────────────────────────
    //  9) Claim-Check pattern demo
    // ──────────────────────────────────────────────
    public void publishClaimCheck(String topicArn) {
        String largePayload = "x".repeat(300_000);
        System.out.println("  Simulating a large payload (" + largePayload.length() + " bytes)...");
        System.out.println("  SNS limit is 256 KB. This payload is " + (largePayload.length() / 1024) + " KB → TOO BIG!\n");

        System.out.println("  CLAIM-CHECK PATTERN:");
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │  1. Store large payload in S3:                           │");
        System.out.println("  │     s3.putObject('data-bucket', 'orders/ORD-123.json')   │");
        System.out.println("  │                                                          │");
        System.out.println("  │  2. Publish REFERENCE to SNS (tiny message):             │");
        System.out.println("  │     {\"s3_bucket\":\"data-bucket\",                          │");
        System.out.println("  │      \"s3_key\":\"orders/ORD-123.json\"}                      │");
        System.out.println("  │                                                          │");
        System.out.println("  │  3. Consumer reads SNS message → fetches from S3         │");
        System.out.println("  └──────────────────────────────────────────────────────────┘\n");

        Map<String, Object> claimCheck = Map.of(
                "pattern", "claim-check",
                "s3_bucket", "my-data-bucket",
                "s3_key", "orders/ORD-" + UUID.randomUUID().toString().substring(0, 8) + ".json",
                "original_size_bytes", largePayload.length(),
                "content_type", "application/json"
        );

        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(gson.toJson(claimCheck))
                .messageAttributes(Map.of(
                        "event_type", MessageAttributeValue.builder()
                                .dataType("String").stringValue("order_placed").build(),
                        "amount", MessageAttributeValue.builder()
                                .dataType("Number").stringValue("999").build(),
                        "payload_location", MessageAttributeValue.builder()
                                .dataType("String").stringValue("s3").build()
                ))
                .build());

        System.out.println("  Published claim-check reference:");
        System.out.println("    MessageId : " + resp.messageId());
        System.out.println("    Size      : " + gson.toJson(claimCheck).length() + " bytes (vs " + largePayload.length() + " original)");
        System.out.println("\n  In production, consumer checks payload_location attribute:");
        System.out.println("    if payload_location == 's3' → fetch from S3 before processing");
    }

    // ──────────────────────────────────────────────
    //  10) Publish for DLQ trigger demo
    // ──────────────────────────────────────────────
    public void publishDlqTestMessages(String topicArn, int count) {
        System.out.println("  Publishing " + count + " DLQ-test messages to standard topic...\n");

        for (int i = 0; i < count; i++) {
            Map<String, Object> payload = Map.of(
                    "test", "dlq-trigger-" + (i + 1),
                    "purpose", "This message will fail consumer processing to demonstrate DLQ",
                    "timestamp", Instant.now().toString()
            );

            Map<String, MessageAttributeValue> attrs = Map.of(
                    "event_type", MessageAttributeValue.builder()
                            .dataType("String").stringValue("dlq_test").build()
            );

            PublishResponse resp = client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(gson.toJson(payload))
                    .messageAttributes(attrs)
                    .build());

            System.out.println("    Published DLQ test msg " + (i + 1)
                    + "  MessageId: " + resp.messageId());
        }

        System.out.println("\n  These messages route to dlq-source-queue (filter: event_type=dlq_test)");
        System.out.println("  That queue has maxReceiveCount=2, visibilityTimeout=5s");
        System.out.println("  Use option 19 to simulate consumer failures → messages go to SQS DLQ.");
    }

    // ──────────────────────────────────────────────
    //  11) Publish for payload-based filter demo
    // ──────────────────────────────────────────────
    public void publishPayloadFilterDemo(String topicArn) {
        System.out.println("  Publishing 3 messages with different body content...\n");

        Map<String, Object> mobileOrder = Map.of(
                "source", "mobile-app",
                "orderId", "ORD-MOB-" + UUID.randomUUID().toString().substring(0, 6),
                "platform", "iOS",
                "timestamp", Instant.now().toString()
        );

        Map<String, Object> webOrder = Map.of(
                "source", "web-portal",
                "orderId", "ORD-WEB-" + UUID.randomUUID().toString().substring(0, 6),
                "browser", "Chrome",
                "timestamp", Instant.now().toString()
        );

        Map<String, Object> apiOrder = Map.of(
                "source", "partner-api",
                "orderId", "ORD-API-" + UUID.randomUUID().toString().substring(0, 6),
                "partner", "Flipkart",
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> genericAttr = Map.of(
                "event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("order_placed").build(),
                "amount", MessageAttributeValue.builder()
                        .dataType("Number").stringValue("100").build()
        );

        for (Map<String, Object> payload : List.of(mobileOrder, webOrder, apiOrder)) {
            PublishResponse resp = client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(gson.toJson(payload))
                    .messageAttributes(genericAttr)
                    .build());

            String src = (String) payload.get("source");
            boolean matchesPayloadFilter = "mobile-app".equals(src);

            System.out.println("    Source: " + src);
            System.out.println("    MessageId: " + resp.messageId());
            System.out.println("    payload-queue (body filter: source=mobile-app): "
                    + (matchesPayloadFilter ? "MATCH" : "SKIP"));
            System.out.println();
        }

        System.out.println("  PAYLOAD-BASED vs ATTRIBUTE-BASED FILTERING:");
        System.out.println("    Attribute-based: matches MessageAttributes (metadata outside body)");
        System.out.println("    Payload-based:   matches fields INSIDE the JSON body");
        System.out.println("    Set via: FilterPolicyScope = 'MessageBody' on subscription");
        System.out.println("\n  Check payload-queue: should have ONLY the mobile-app message.");
        System.out.println("  Check analytics-queue: should have ALL 3 messages.");
    }

    // ──────────────────────────────────────────────
    //  12) Publish for raw vs wrapped comparison
    // ──────────────────────────────────────────────
    public void publishRawVsWrappedDemo(String topicArn) {
        Map<String, Object> order = Map.of(
                "orderId", "ORD-WRAP-" + UUID.randomUUID().toString().substring(0, 6),
                "amount", 250.00,
                "item", "keyboard",
                "timestamp", Instant.now().toString()
        );

        Map<String, MessageAttributeValue> attrs = Map.of(
                "event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("order_placed").build(),
                "amount", MessageAttributeValue.builder()
                        .dataType("Number").stringValue("250").build()
        );

        PublishResponse resp = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(gson.toJson(order))
                .messageAttributes(attrs)
                .build());

        System.out.println("  Published 1 message to standard topic:");
        System.out.println("    MessageId: " + resp.messageId());
        System.out.println("    Body: " + gson.toJson(order));
        System.out.println("\n  This message arrives at TWO queues:");
        System.out.println("    inventory-queue   (RawMessageDelivery=true)  → body = your JSON");
        System.out.println("    wrapped-queue     (RawMessageDelivery=false) → body = SNS JSON envelope");
        System.out.println("\n  Use option 21 to see the difference side-by-side.");
    }

    // ──────────────────────────────────────────────
    //  13) Publish for FIFO message group routing demo
    // ──────────────────────────────────────────────
    public void publishFifoGroupRouting(String fifoTopicArn) {
        String[] rides = {"ride-A", "ride-B", "ride-C", "ride-D"};
        String[] events = {"payment_processed", "payment_confirmed", "receipt_generated"};

        System.out.println("  Publishing events for 4 rides (3 payment events each)...\n");

        int total = 0;
        for (String ride : rides) {
            for (int i = 0; i < events.length; i++) {
                Map<String, Object> payload = Map.of(
                        "ride_id", ride,
                        "event", events[i],
                        "step", i + 1,
                        "amount", 150 + (total * 10),
                        "timestamp", Instant.now().toString()
                );

                PublishResponse resp = client.publish(PublishRequest.builder()
                        .topicArn(fifoTopicArn)
                        .message(gson.toJson(payload))
                        .messageGroupId(ride)
                        .build());

                System.out.println("    [" + ride + "] Step " + (i + 1) + ": " + events[i]
                        + "  (Seq=" + resp.sequenceNumber() + ")");
                total++;
            }
        }

        System.out.println("\n  TOTAL: " + total + " messages across 4 MessageGroupIds");
        System.out.println("\n  KEY CONCEPT (from the doc's deep dive):");
        System.out.println("    Same GroupId (ride-A)  → same consumer, strict order: 1→2→3");
        System.out.println("    Different GroupIds     → processed in PARALLEL by different consumers");
        System.out.println("    ride-A's Step 1 doesn't block ride-B's Step 1");
        System.out.println("\n  Use option 22 to see how FIFO SQS distributes groups across consumers.");
    }
}
