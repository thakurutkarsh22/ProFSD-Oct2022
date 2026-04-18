package com.demo.sns;

import java.util.Scanner;

/**
 * Interactive CLI to explore every major SNS concept against real AWS.
 *
 * Run with:  mvn compile exec:java
 */
public class SnsPlayground {

    private static SnsConfig config;
    private static SnsPublisher publisher;
    private static SnsSubscriber subscriber;

    public static void main(String[] args) {
        printBanner();

        config     = new SnsConfig();
        config.initialize();
        publisher  = new SnsPublisher(config.snsClient());
        subscriber = new SnsSubscriber(config.snsClient(), config.sqsClient());

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice > ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    // ── PUBLISH ──
                    case "1" -> {
                        ConceptExplainer.publishSingle();
                        System.out.print("  Enter message body: ");
                        String body = scanner.nextLine();
                        publisher.publishSingle(config.standardTopicArn(), body);
                    }
                    case "2" -> {
                        ConceptExplainer.messageAttributes();
                        publisher.publishOrderPlaced(config.standardTopicArn());
                    }
                    case "3" -> {
                        ConceptExplainer.paymentDue();
                        publisher.publishPaymentDue(config.standardTopicArn());
                    }
                    case "4" -> {
                        ConceptExplainer.highValueOrder();
                        publisher.publishHighValueOrder(config.standardTopicArn());
                    }
                    case "5" -> {
                        ConceptExplainer.batchPublish();
                        System.out.print("  How many messages (1-10)? ");
                        int n = Integer.parseInt(scanner.nextLine().trim());
                        publisher.publishBatch(config.standardTopicArn(), n);
                    }
                    case "6" -> {
                        ConceptExplainer.fifoOrdering();
                        publisher.publishFifoOrdered(config.fifoTopicArn());
                    }
                    case "7" -> {
                        ConceptExplainer.fifoDedup();
                        publisher.publishFifoDedupDemo(config.fifoTopicArn());
                    }
                    case "8" -> {
                        ConceptExplainer.fanoutBurst();
                        publisher.publishFanoutBurst(config.standardTopicArn());
                    }
                    case "9" -> {
                        ConceptExplainer.claimCheck();
                        publisher.publishClaimCheck(config.standardTopicArn());
                    }

                    // ── CONSUME / VERIFY ──
                    case "10" -> {
                        ConceptExplainer.drainQueues();
                        subscriber.drainAllQueues(config);
                    }
                    case "11" -> {
                        ConceptExplainer.receiveFromQueue();
                        System.out.println("  Which queue?");
                        System.out.println("    a) inventory     (filter: order_placed)");
                        System.out.println("    b) billing       (filter: payment_due)");
                        System.out.println("    c) analytics     (no filter — gets all)");
                        System.out.println("    d) highvalue     (filter: amount >= 500)");
                        System.out.println("    e) fifo");
                        System.out.println("    f) payload       (body filter: source=mobile-app)");
                        System.out.println("    g) wrapped       (raw=false, SNS JSON envelope)");
                        System.out.println("    h) dlq-source    (SQS redrive source)");
                        System.out.print("  Choice (a-h): ");
                        String q = scanner.nextLine().trim().toLowerCase();
                        String qUrl = switch (q) {
                            case "b" -> config.billingQueueUrl();
                            case "c" -> config.analyticsQueueUrl();
                            case "d" -> config.highvalueQueueUrl();
                            case "e" -> config.fifoQueueUrl();
                            case "f" -> config.payloadQueueUrl();
                            case "g" -> config.wrappedQueueUrl();
                            case "h" -> config.dlqSourceQueueUrl();
                            default  -> config.inventoryQueueUrl();
                        };
                        String label = switch (q) {
                            case "b" -> "billing-queue";
                            case "c" -> "analytics-queue";
                            case "d" -> "highvalue-queue";
                            case "e" -> "fifo-queue";
                            case "f" -> "payload-queue";
                            case "g" -> "wrapped-queue";
                            case "h" -> "dlq-source-queue";
                            default  -> "inventory-queue";
                        };
                        subscriber.receiveFromQueue(qUrl, label, 10);
                    }
                    case "12" -> {
                        ConceptExplainer.queueStats();
                        subscriber.printAllQueueStats(config);
                    }
                    case "13" -> {
                        ConceptExplainer.fifoOrdering();
                        subscriber.receiveFifo(config.fifoQueueUrl());
                    }

                    // ── ADMIN ──
                    case "14" -> {
                        ConceptExplainer.listSubscriptions();
                        subscriber.listSubscriptions(config.standardTopicArn());
                        System.out.println("  ── FIFO Topic ──");
                        subscriber.listSubscriptions(config.fifoTopicArn());
                    }
                    case "15" -> {
                        ConceptExplainer.competingConsumers();
                        System.out.print("  How many consumers (2-5)? ");
                        int nc = Math.min(5, Math.max(2, Integer.parseInt(scanner.nextLine().trim())));
                        System.out.println("\n  Pre-loading 10 messages to analytics-queue via SNS...");
                        publisher.publishFanoutBurst(config.standardTopicArn());
                        System.out.println("\n  Starting " + nc + " competing consumers on analytics-queue...\n");
                        subscriber.runCompetingConsumers(
                                config.analyticsQueueUrl(), "analytics-queue", nc, 15);
                    }
                    case "16" -> {
                        System.out.println("  Purging ALL subscriber queues...\n");
                        subscriber.purgeAllQueues(config);
                    }

                    // ── DLQ DEMOS ──
                    case "17" -> {
                        ConceptExplainer.dlqPublish();
                        System.out.print("  How many DLQ test messages (1-5)? ");
                        int n = Math.min(5, Math.max(1, Integer.parseInt(scanner.nextLine().trim())));
                        publisher.publishDlqTestMessages(config.standardTopicArn(), n);
                    }
                    case "18" -> {
                        ConceptExplainer.dlqSimulate();
                        subscriber.simulateDlqFailures(config.dlqSourceQueueUrl());
                    }
                    case "19" -> {
                        ConceptExplainer.dlqInspect();
                        subscriber.inspectDlqMessages(config.dlqQueueUrl(), config.sqsDlqQueueUrl());
                    }
                    case "20" -> {
                        ConceptExplainer.dlqReplay();
                        System.out.println("  Replay from which DLQ?");
                        System.out.println("    a) SQS redrive DLQ → dlq-source-queue");
                        System.out.println("    b) SNS subscription DLQ → analytics-queue");
                        System.out.print("  Choice (a/b): ");
                        String dlqChoice = scanner.nextLine().trim().toLowerCase();
                        if ("b".equals(dlqChoice)) {
                            subscriber.replayDlqToSource(config.dlqQueueUrl(),
                                    config.analyticsQueueUrl(), "SNS-DLQ");
                        } else {
                            subscriber.replayDlqToSource(config.sqsDlqQueueUrl(),
                                    config.dlqSourceQueueUrl(), "SQS-DLQ");
                        }
                    }

                    // ── ADVANCED DEMOS ──
                    case "21" -> {
                        ConceptExplainer.payloadFilter();
                        publisher.publishPayloadFilterDemo(config.standardTopicArn());
                    }
                    case "22" -> {
                        ConceptExplainer.rawVsWrapped();
                        publisher.publishRawVsWrappedDemo(config.standardTopicArn());
                    }
                    case "23" -> {
                        System.out.println("  Comparing raw vs wrapped message format...\n");
                        subscriber.showRawVsWrapped(config.inventoryQueueUrl(), config.wrappedQueueUrl());
                    }
                    case "24" -> {
                        ConceptExplainer.fifoGroupRouting();
                        System.out.println("  Step 1: Publishing 4 rides × 3 events...\n");
                        publisher.publishFifoGroupRouting(config.fifoTopicArn());
                        System.out.println("\n  Step 2: Running 2 competing consumers on FIFO queue...\n");
                        subscriber.fifoGroupRoutingDemo(config.fifoQueueUrl());
                    }
                    case "25" -> {
                        subscriber.printAllQueueStatsExtended(config);
                    }
                    case "26" -> {
                        ConceptExplainer.snsDlqTrigger();
                        subscriber.triggerSnsDlq(config, publisher);
                    }

                    case "0", "q", "quit", "exit" -> running = false;

                    default -> System.out.println("  Invalid choice. Try again.");
                }
            } catch (Exception e) {
                System.out.println("\n  ERROR: " + e.getMessage());
                System.out.println("  Type : " + e.getClass().getSimpleName() + "\n");
            }

            if (running) {
                System.out.println("\n  Press Enter to continue...");
                scanner.nextLine();
            }
        }

        config.close();
        System.out.println("\nBye! Check AWS Console → SNS & SQS to see your topics and queues.\n");
    }

    private static void printBanner() {
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║          AWS SNS Interactive Playground                    ║
            ║   Publish, Fan-Out, Filter, FIFO, DLQ, Claim-Check       ║
            ║   All against REAL AWS — watch results in the Console!    ║
            ╚═══════════════════════════════════════════════════════════╝
            """);
    }

    private static void printMenu() {
        System.out.println("""
            ┌───────────────────────────────────────────────────────────────┐
            │  PUBLISH (to SNS topics)                                      │
            │    1.  Publish a single message (Standard)                    │
            │    2.  Publish order_placed event (→ inventory queue)         │
            │    3.  Publish payment_due event (→ billing queue)            │
            │    4.  Publish high-value order $1500+ (→ highvalue queue)    │
            │    5.  Publish batch (up to 10 messages)                      │
            │    6.  Publish FIFO ordered events (2 customers × 5 steps)   │
            │    7.  FIFO deduplication demo (same message twice)           │
            │    8.  Fan-out burst (6 mixed events, watch routing)          │
            │    9.  Claim-check pattern (large payload simulation)         │
            │                                                               │
            │  CONSUME / VERIFY (from SQS subscriber queues)               │
            │   10.  Drain ALL queues (show fan-out counts)                 │
            │   11.  Receive from a specific queue                          │
            │   12.  Show queue stats (all queues)                          │
            │   13.  Receive FIFO messages (show ordering proof)            │
            │                                                               │
            │  ADMIN                                                        │
            │   14.  List all subscriptions (show filter policies)          │
            │   15.  Competing consumers demo (multi-threaded)              │
            │   16.  Purge all queues                                       │
            │                                                               │
            │  DLQ (Dead-Letter Queue demos)                                │
            │   17.  Publish DLQ test messages (→ dlq-source-queue)        │
            │   18.  Simulate consumer failures (→ triggers SQS DLQ)       │
            │   19.  Inspect BOTH DLQs (SNS sub DLQ + SQS redrive DLQ)    │
            │   20.  Replay DLQ messages back to source queue              │
            │                                                               │
            │  ADVANCED                                                     │
            │   21.  Payload-based filtering demo (body JSON matching)     │
            │   22.  Publish for raw vs wrapped comparison                  │
            │   23.  Show raw vs wrapped message side-by-side              │
            │   24.  FIFO MessageGroupId routing (lane divider demo)       │
            │   25.  Extended queue stats (all queues + both DLQs)         │
            │   26.  Trigger SNS subscription DLQ (block permission demo)  │
            │    0.  Exit                                                   │
            └───────────────────────────────────────────────────────────────┘
            """);
    }
}
