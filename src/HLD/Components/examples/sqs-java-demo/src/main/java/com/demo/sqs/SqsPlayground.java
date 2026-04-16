package com.demo.sqs;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Scanner;

/**
 * Interactive CLI to explore every major SQS operation against real AWS queues.
 *
 * Run with:  mvn compile exec:java
 */
public class SqsPlayground {

    private static SqsConfig config;
    private static SqsProducer producer;
    private static SqsConsumer consumer;
    private static SqsClient client;

    public static void main(String[] args) {
        printBanner();

        config   = new SqsConfig();
        config.initialize();
        client   = config.client();
        producer = new SqsProducer(client);
        consumer = new SqsConsumer(client);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice > ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    // ── PRODUCE ──
                    case "1" -> {
                        ConceptExplainer.sendSingleMessage();
                        System.out.print("  Enter message body: ");
                        String body = scanner.nextLine();
                        producer.sendSingleMessage(config.standardQueueUrl(), body);
                    }
                    case "2" -> {
                        ConceptExplainer.messageAttributes();
                        producer.sendWithAttributes(config.standardQueueUrl());
                    }
                    case "3" -> {
                        ConceptExplainer.batchOperations();
                        System.out.print("  How many messages (1-10)? ");
                        int n = Integer.parseInt(scanner.nextLine().trim());
                        producer.sendBatch(config.standardQueueUrl(), n);
                    }
                    case "4" -> {
                        ConceptExplainer.delayedMessages();
                        System.out.print("  Delay in seconds (0-900)? ");
                        int delay = Integer.parseInt(scanner.nextLine().trim());
                        producer.sendDelayedMessage(config.standardQueueUrl(), delay);
                    }
                    case "5" -> {
                        ConceptExplainer.deduplication();
                        producer.demonstrateDeduplication(
                                config.fifoQueueUrl(), config.fifoDedupQueueUrl());
                    }
                    case "6" -> {
                        ConceptExplainer.fifoQueue();
                        producer.sendFifoMessages(config.fifoQueueUrl());
                    }

                    // ── CONSUME ──
                    case "7" -> {
                        ConceptExplainer.receiveAndDelete();
                        consumer.receiveAndProcess(config.standardQueueUrl(), 5);
                    }
                    case "8" -> {
                        ConceptExplainer.visibilityTimeout();
                        consumer.peekMessages(config.standardQueueUrl(), 5);
                    }
                    case "9" -> {
                        ConceptExplainer.fifoReceive();
                        consumer.receiveFifo(config.fifoQueueUrl());
                    }

                    // ── DLQ ──
                    case "10" -> {
                        ConceptExplainer.deadLetterQueue();
                        consumer.simulateDlqFlow(
                                config.standardQueueUrl(), config.dlqQueueUrl());
                    }

                    // ── COMPETING CONSUMERS ──
                    case "11" -> {
                        ConceptExplainer.competingConsumers();
                        System.out.print("  How many consumers (2-5)? ");
                        int nc = Math.min(5, Math.max(2, Integer.parseInt(scanner.nextLine().trim())));
                        System.out.print("  How many messages to pre-load? (5-50): ");
                        int nm = Math.min(50, Math.max(5, Integer.parseInt(scanner.nextLine().trim())));
                        System.out.println("\n  Pre-loading " + nm + " messages into Standard queue...");
                        producer.sendBatch(config.standardQueueUrl(), Math.min(nm, 10));
                        if (nm > 10) {
                            for (int i = 10; i < nm; i++) {
                                producer.sendSingleMessage(config.standardQueueUrl(),
                                        "{\"item\":" + (i + 1) + ",\"ts\":\"" + java.time.Instant.now() + "\"}");
                            }
                        }
                        System.out.println("  Starting consumers...\n");
                        consumer.runCompetingConsumers(config.standardQueueUrl(), nc, 15);
                    }
                    case "12" -> {
                        ConceptExplainer.fifoCompetingConsumers();
                        System.out.print("  How many consumers (2-4)? ");
                        int nfc = Math.min(4, Math.max(2, Integer.parseInt(scanner.nextLine().trim())));
                        System.out.println("\n  Pre-loading FIFO messages for 3 customer groups...");
                        String[] groups = {"customer-A", "customer-B", "customer-C"};
                        String[] steps  = {"place-order", "validate", "charge-payment", "ship", "notify"};
                        for (String group : groups) {
                            for (int s = 0; s < steps.length; s++) {
                                client.sendMessage(SendMessageRequest.builder()
                                        .queueUrl(config.fifoQueueUrl())
                                        .messageBody("{\"customer\":\"" + group +
                                                "\",\"action\":\"" + steps[s] +
                                                "\",\"step\":" + (s + 1) + "}")
                                        .messageGroupId(group)
                                        .build());
                            }
                            System.out.println("    Sent 5 ordered msgs for " + group);
                        }
                        System.out.println("  Starting FIFO consumers...\n");
                        consumer.runFifoCompetingConsumers(config.fifoQueueUrl(), nfc, 20);
                    }

                    // ── STATS ──
                    case "13" -> {
                        ConceptExplainer.queueStats();
                        consumer.printQueueStats(config.standardQueueUrl(), "Standard Queue");
                        consumer.printQueueStats(config.fifoQueueUrl(), "FIFO Queue (content-based dedup)");
                        consumer.printQueueStats(config.fifoDedupQueueUrl(), "FIFO Queue (explicit dedup)");
                        consumer.printQueueStats(config.dlqQueueUrl(), "Dead Letter Queue");
                    }

                    // ── PURGE ──
                    case "14" -> {
                        ConceptExplainer.purgeQueue();
                        System.out.print("  Purge which queue? (standard/fifo/dedup/dlq): ");
                        String q = scanner.nextLine().trim().toLowerCase();
                        String url = switch (q) {
                            case "fifo"  -> config.fifoQueueUrl();
                            case "dedup" -> config.fifoDedupQueueUrl();
                            case "dlq"   -> config.dlqQueueUrl();
                            default      -> config.standardQueueUrl();
                        };
                        config.client().purgeQueue(
                                software.amazon.awssdk.services.sqs.model.PurgeQueueRequest.builder()
                                        .queueUrl(url).build());
                        System.out.println("  Purged! (may take up to 60s to reflect in console)");
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
        System.out.println("\nBye! Check AWS Console to see the queues and messages.\n");
    }

    private static void printBanner() {
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════╗
            ║          AWS SQS Interactive Playground           ║
            ║   Send, Receive, DLQ, FIFO — all against real    ║
            ║   AWS queues. Watch results in the AWS Console!   ║
            ╚═══════════════════════════════════════════════════╝
            """);
    }

    private static void printMenu() {
        System.out.println("""
            ┌───────────────────────────────────────────────────────────┐
            │  PRODUCE                                                  │
            │    1. Send a single message (Standard)                    │
            │    2. Send order event with attributes                    │
            │    3. Send batch (up to 10 messages)                      │
            │    4. Send delayed message                                │
            │    5. Deduplication demo (content-based vs explicit ID)   │
            │    6. Send FIFO messages (ordered per customer)           │
            │                                                           │
            │  CONSUME                                                  │
            │    7. Receive & delete (Standard)                         │
            │    8. Peek messages (receive, don't delete)               │
            │    9. Receive FIFO messages (shows ordering)              │
            │                                                           │
            │  DLQ                                                      │
            │   10. Simulate DLQ flow (poison pill)                     │
            │                                                           │
            │  COMPETING CONSUMERS (multi-threaded)                     │
            │   11. Standard queue — N consumers competing              │
            │   12. FIFO queue — N consumers with group locking         │
            │                                                           │
            │  ADMIN                                                    │
            │   13. Show queue stats (all 4 queues)                     │
            │   14. Purge a queue                                       │
            │    0. Exit                                                │
            └───────────────────────────────────────────────────────────┘
            """);
    }
}
