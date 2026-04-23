package com.demo.s3;

import software.amazon.awssdk.services.s3.model.StorageClass;

import java.util.Scanner;

/**
 * Interactive CLI to explore every major S3 operation against real AWS buckets.
 *
 * Run with:  mvn compile exec:java
 */
public class S3Playground {

    private static S3Config config;
    private static S3Uploader uploader;
    private static S3Downloader downloader;
    private static S3VersioningDemo versioning;
    private static S3LifecycleDemo lifecycle;

    public static void main(String[] args) {
        printBanner();

        config      = new S3Config();
        config.initialize();
        uploader    = new S3Uploader(config.client(), config.presigner());
        downloader  = new S3Downloader(config.client(), config.presigner());
        versioning  = new S3VersioningDemo(config.client());
        lifecycle   = new S3LifecycleDemo(config.client());

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice > ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {

                    // ── UPLOAD (PUT) ──
                    case "1" -> {
                        ConceptExplainer.putObject();
                        System.out.print("  Enter object key (e.g., test/hello.txt): ");
                        String key = scanner.nextLine().trim();
                        System.out.print("  Enter content: ");
                        String content = scanner.nextLine();
                        uploader.putObject(config.demoBucket(), key, content);
                    }
                    case "2" -> {
                        ConceptExplainer.putWithMetadata();
                        uploader.putWithMetadata(config.demoBucket());
                    }
                    case "3" -> {
                        ConceptExplainer.storageClasses();
                        System.out.println("  Available classes: STANDARD, STANDARD_IA, ONEZONE_IA, INTELLIGENT_TIERING, GLACIER_IR");
                        System.out.print("  Choose storage class: ");
                        String cls = scanner.nextLine().trim().toUpperCase();
                        StorageClass sc = switch (cls) {
                            case "STANDARD_IA" -> StorageClass.STANDARD_IA;
                            case "ONEZONE_IA" -> StorageClass.ONEZONE_IA;
                            case "INTELLIGENT_TIERING" -> StorageClass.INTELLIGENT_TIERING;
                            case "GLACIER_IR" -> StorageClass.GLACIER_IR;
                            default -> StorageClass.STANDARD;
                        };
                        uploader.putWithStorageClass(config.demoBucket(), sc);
                    }
                    case "4" -> {
                        ConceptExplainer.encryption();
                        uploader.putWithEncryption(config.demoBucket());
                    }
                    case "5" -> {
                        ConceptExplainer.multipartUpload();
                        System.out.print("  Total size in MB (10-50, uses 5MB parts): ");
                        int size = Math.min(50, Math.max(10, Integer.parseInt(scanner.nextLine().trim())));
                        uploader.multipartUpload(config.demoBucket(), size);
                    }
                    case "6" -> {
                        ConceptExplainer.presignedUrl();
                        uploader.generatePresignedPutUrl(config.demoBucket());
                    }

                    // ── DOWNLOAD (GET) ──
                    case "7" -> {
                        ConceptExplainer.getObject();
                        System.out.print("  Enter object key to download: ");
                        String key = scanner.nextLine().trim();
                        downloader.getObject(config.demoBucket(), key);
                    }
                    case "8" -> {
                        ConceptExplainer.headObject();
                        System.out.print("  Enter object key: ");
                        String key = scanner.nextLine().trim();
                        downloader.headObject(config.demoBucket(), key);
                    }
                    case "9" -> {
                        ConceptExplainer.rangeGet();
                        System.out.print("  Enter object key: ");
                        String key = scanner.nextLine().trim();
                        System.out.print("  Start byte: ");
                        long start = Long.parseLong(scanner.nextLine().trim());
                        System.out.print("  End byte: ");
                        long end = Long.parseLong(scanner.nextLine().trim());
                        downloader.rangeGet(config.demoBucket(), key, start, end);
                    }
                    case "10" -> {
                        ConceptExplainer.listObjects();
                        System.out.print("  Prefix (empty for all): ");
                        String prefix = scanner.nextLine().trim();
                        downloader.listObjects(config.demoBucket(), prefix, 50);
                    }
                    case "11" -> {
                        ConceptExplainer.presignedUrl();
                        System.out.print("  Enter object key for download URL: ");
                        String key = scanner.nextLine().trim();
                        downloader.generatePresignedGetUrl(config.demoBucket(), key);
                    }

                    // ── DELETE ──
                    case "12" -> {
                        ConceptExplainer.deleteObject();
                        System.out.print("  Enter object key to delete: ");
                        String key = scanner.nextLine().trim();
                        downloader.deleteObject(config.demoBucket(), key);
                    }

                    // ── COPY ──
                    case "13" -> {
                        ConceptExplainer.copyObject();
                        System.out.print("  Source key: ");
                        String src = scanner.nextLine().trim();
                        System.out.print("  Destination key: ");
                        String dst = scanner.nextLine().trim();
                        uploader.copyObject(config.demoBucket(), src, dst);
                    }

                    // ── VERSIONING ──
                    case "14" -> {
                        ConceptExplainer.versioning();
                        versioning.fullVersioningLifecycle(config.versionedBucket());
                    }
                    case "15" -> {
                        System.out.print("  Enter prefix to list versions: ");
                        String prefix = scanner.nextLine().trim();
                        versioning.listVersions(config.versionedBucket(), prefix);
                    }

                    // ── CONSISTENCY ──
                    case "16" -> {
                        ConceptExplainer.consistency();
                        downloader.demonstrateConsistency(config.demoBucket());
                    }

                    // ── LIFECYCLE & SECURITY ──
                    case "17" -> {
                        ConceptExplainer.lifecycleRules();
                        lifecycle.setLifecycleRules(config.demoBucket());
                    }
                    case "18" -> {
                        lifecycle.showLifecycleRules(config.demoBucket());
                    }
                    case "19" -> {
                        ConceptExplainer.securityPolicy();
                        lifecycle.setDefaultEncryption(config.demoBucket());
                    }
                    case "20" -> {
                        ConceptExplainer.securityPolicy();
                        lifecycle.setSecurityPolicy(config.demoBucket());
                    }
                    case "21" -> {
                        ConceptExplainer.eventNotifications();
                        lifecycle.showEventNotificationConfig(config.demoBucket());
                    }

                    // ── PERFORMANCE ──
                    case "22" -> {
                        ConceptExplainer.distributedPrefixes();
                        System.out.print("  How many objects (10-100): ");
                        int n = Math.min(100, Math.max(10, Integer.parseInt(scanner.nextLine().trim())));
                        uploader.distributedPrefixUpload(config.demoBucket(), n);
                    }
                    case "23" -> {
                        ConceptExplainer.parallelUpload();
                        System.out.print("  How many objects (10-100): ");
                        int n = Math.min(100, Math.max(10, Integer.parseInt(scanner.nextLine().trim())));
                        System.out.print("  How many threads (2-8): ");
                        int t = Math.min(8, Math.max(2, Integer.parseInt(scanner.nextLine().trim())));
                        uploader.parallelUpload(config.demoBucket(), n, t);
                    }

                    // ── ADMIN ──
                    case "24" -> {
                        ConceptExplainer.bucketStats();
                        System.out.println("\n  === Demo Bucket ===");
                        lifecycle.showBucketStats(config.demoBucket());
                        System.out.println("\n  === Versioned Bucket ===");
                        lifecycle.showBucketStats(config.versionedBucket());
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
        System.out.println("\nBye! Check AWS Console → S3 to see your buckets and objects.\n");
    }

    private static void printBanner() {
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════╗
            ║           AWS S3 Interactive Playground                ║
            ║   Upload, Download, Version, Encrypt, Lifecycle —     ║
            ║   all against real S3 buckets. Watch in AWS Console!  ║
            ╚═══════════════════════════════════════════════════════╝
            """);
    }

    private static void printMenu() {
        System.out.println("""
            ┌─────────────────────────────────────────────────────────────┐
            │  UPLOAD (PUT)                                               │
            │    1.  PUT object (simple text)                             │
            │    2.  PUT with custom metadata                             │
            │    3.  PUT with storage class (Standard, IA, Glacier...)    │
            │    4.  PUT with encryption (SSE-S3)                         │
            │    5.  Multipart upload (simulated large file)              │
            │    6.  Generate presigned PUT URL (+ upload via it)         │
            │                                                             │
            │  DOWNLOAD (GET)                                             │
            │    7.  GET object (download + display)                      │
            │    8.  HEAD object (metadata only, no download)             │
            │    9.  Range GET (partial download)                         │
            │   10.  LIST objects (with prefix filter)                    │
            │   11.  Generate presigned GET URL                           │
            │                                                             │
            │  DELETE & COPY                                              │
            │   12.  DELETE object                                        │
            │   13.  COPY object (server-side, "rename" pattern)          │
            │                                                             │
            │  VERSIONING                                                 │
            │   14.  Full versioning lifecycle (create→overwrite→delete→  │
            │        restore)                                             │
            │   15.  List object versions                                 │
            │                                                             │
            │  CONSISTENCY                                                │
            │   16.  Demonstrate strong read-after-write consistency      │
            │                                                             │
            │  LIFECYCLE & SECURITY                                       │
            │   17.  Set lifecycle rules (tiering waterfall)              │
            │   18.  Show current lifecycle rules                         │
            │   19.  Set default encryption (SSE-S3 + Bucket Key)         │
            │   20.  Set bucket policy (enforce encryption + HTTPS)       │
            │   21.  Event notifications (educational — show config)      │
            │                                                             │
            │  PERFORMANCE                                                │
            │   22.  Distributed prefix upload (partition optimization)   │
            │   23.  Parallel upload (multi-threaded throughput)          │
            │                                                             │
            │  ADMIN                                                      │
            │   24.  Show bucket stats (both buckets)                     │
            │    0.  Exit                                                 │
            └─────────────────────────────────────────────────────────────┘
            """);
    }
}
