package com.demo.s3;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;

public class S3LifecycleDemo {

    private final S3Client client;

    public S3LifecycleDemo(S3Client client) {
        this.client = client;
    }

    // ──────────────────────────────────────────────
    //  1) Set lifecycle rules (waterfall tiering)
    // ──────────────────────────────────────────────
    public void setLifecycleRules(String bucket) {
        System.out.println("  Setting lifecycle rules on bucket: " + bucket + "\n");

        LifecycleRule tieringRule = LifecycleRule.builder()
                .id("tiered-storage-waterfall")
                .filter(LifecycleRuleFilter.builder().prefix("logs/").build())
                .status(ExpirationStatus.ENABLED)
                .transitions(List.of(
                        Transition.builder()
                                .days(30)
                                .storageClass(TransitionStorageClass.STANDARD_IA)
                                .build(),
                        Transition.builder()
                                .days(90)
                                .storageClass(TransitionStorageClass.GLACIER)
                                .build(),
                        Transition.builder()
                                .days(365)
                                .storageClass(TransitionStorageClass.DEEP_ARCHIVE)
                                .build()))
                .expiration(LifecycleExpiration.builder().days(730).build())
                .build();

        LifecycleRule cleanupMultipart = LifecycleRule.builder()
                .id("abort-incomplete-multipart")
                .filter(LifecycleRuleFilter.builder().prefix("").build())
                .status(ExpirationStatus.ENABLED)
                .abortIncompleteMultipartUpload(
                        AbortIncompleteMultipartUpload.builder().daysAfterInitiation(7).build())
                .build();

        LifecycleRule expireOldVersions = LifecycleRule.builder()
                .id("expire-old-versions")
                .filter(LifecycleRuleFilter.builder().prefix("").build())
                .status(ExpirationStatus.ENABLED)
                .noncurrentVersionExpiration(
                        NoncurrentVersionExpiration.builder().noncurrentDays(90).build())
                .noncurrentVersionTransitions(List.of(
                        NoncurrentVersionTransition.builder()
                                .noncurrentDays(30)
                                .storageClass(TransitionStorageClass.STANDARD_IA)
                                .build()))
                .build();

        client.putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(bucket)
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(List.of(tieringRule, cleanupMultipart, expireOldVersions))
                                .build())
                        .build());

        System.out.println("  ✓ Lifecycle rules set:\n");
        System.out.println("  Rule 1: 'tiered-storage-waterfall' (prefix: logs/)");
        System.out.println("    Day 0-29   → STANDARD ($0.023/GB)");
        System.out.println("    Day 30-89  → STANDARD_IA ($0.0125/GB)");
        System.out.println("    Day 90-364 → GLACIER ($0.004/GB)");
        System.out.println("    Day 365+   → DEEP_ARCHIVE ($0.00099/GB)");
        System.out.println("    Day 730    → DELETE");
        System.out.println();
        System.out.println("  Rule 2: 'abort-incomplete-multipart'");
        System.out.println("    Abort incomplete multipart uploads after 7 days");
        System.out.println("    (Prevents hidden storage costs from failed uploads)");
        System.out.println();
        System.out.println("  Rule 3: 'expire-old-versions'");
        System.out.println("    Non-current versions → IA after 30 days → deleted after 90 days");
        System.out.println("    (Keeps versioning useful without unbounded cost growth)");
    }

    // ──────────────────────────────────────────────
    //  2) Show current lifecycle configuration
    // ──────────────────────────────────────────────
    public void showLifecycleRules(String bucket) {
        try {
            GetBucketLifecycleConfigurationResponse resp = client.getBucketLifecycleConfiguration(
                    GetBucketLifecycleConfigurationRequest.builder().bucket(bucket).build());

            List<LifecycleRule> rules = resp.rules();
            System.out.println("  Lifecycle rules for bucket: " + bucket);
            System.out.println("  Total rules: " + rules.size() + "\n");

            for (LifecycleRule rule : rules) {
                System.out.println("  Rule: " + rule.id());
                System.out.println("    Status  : " + rule.status());
                System.out.println("    Filter  : " + rule.filter());

                if (!rule.transitions().isEmpty()) {
                    System.out.println("    Transitions:");
                    for (Transition t : rule.transitions()) {
                        System.out.println("      After " + t.days() + " days → " + t.storageClass());
                    }
                }
                if (rule.expiration() != null && rule.expiration().days() != null) {
                    System.out.println("    Expiration: delete after " + rule.expiration().days() + " days");
                }
                if (rule.abortIncompleteMultipartUpload() != null) {
                    System.out.println("    Abort multipart: after "
                            + rule.abortIncompleteMultipartUpload().daysAfterInitiation() + " days");
                }
                if (rule.noncurrentVersionExpiration() != null) {
                    System.out.println("    Non-current expiration: "
                            + rule.noncurrentVersionExpiration().noncurrentDays() + " days");
                }
                System.out.println();
            }
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                System.out.println("  No lifecycle rules configured for bucket: " + bucket);
            } else {
                throw e;
            }
        }
    }

    // ──────────────────────────────────────────────
    //  3) Set bucket encryption (default SSE-S3)
    // ──────────────────────────────────────────────
    public void setDefaultEncryption(String bucket) {
        client.putBucketEncryption(PutBucketEncryptionRequest.builder()
                .bucket(bucket)
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build())
                                .bucketKeyEnabled(true)
                                .build())
                        .build())
                .build());

        System.out.println("  Default encryption set on: " + bucket);
        System.out.println("    Algorithm  : AES-256 (SSE-S3)");
        System.out.println("    Bucket Key : ENABLED (reduces KMS costs)");
        System.out.println("\n  Since Jan 2023, ALL new objects are encrypted by default.");
        System.out.println("  This explicit config ensures it and enables Bucket Key optimization.");
    }

    // ──────────────────────────────────────────────
    //  4) Set bucket policy (deny unencrypted uploads)
    // ──────────────────────────────────────────────
    public void setSecurityPolicy(String bucket) {
        String accountId;
        try {
            accountId = software.amazon.awssdk.services.sts.StsClient.create()
                    .getCallerIdentity().account();
        } catch (Exception e) {
            accountId = "ACCOUNT_ID";
        }

        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Sid": "DenyUnencryptedUploads",
                      "Effect": "Deny",
                      "Principal": "*",
                      "Action": "s3:PutObject",
                      "Resource": "arn:aws:s3:::%s/*",
                      "Condition": {
                        "StringNotEquals": {
                          "s3:x-amz-server-side-encryption": "AES256"
                        }
                      }
                    },
                    {
                      "Sid": "DenyInsecureTransport",
                      "Effect": "Deny",
                      "Principal": "*",
                      "Action": "s3:*",
                      "Resource": [
                        "arn:aws:s3:::%s",
                        "arn:aws:s3:::%s/*"
                      ],
                      "Condition": {
                        "Bool": {
                          "aws:SecureTransport": "false"
                        }
                      }
                    }
                  ]
                }
                """.formatted(bucket, bucket, bucket);

        client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy(policy)
                .build());

        System.out.println("  Bucket policy set on: " + bucket);
        System.out.println();
        System.out.println("  Rule 1: Deny PutObject without SSE-S3 encryption header");
        System.out.println("    → Forces all uploads to be encrypted");
        System.out.println();
        System.out.println("  Rule 2: Deny all operations over HTTP (non-TLS)");
        System.out.println("    → Forces HTTPS for all access");
        System.out.println();
        System.out.println("  These are production best practices from the Capital One breach lesson.");
    }

    // ──────────────────────────────────────────────
    //  5) Enable S3 event notifications (show config)
    // ──────────────────────────────────────────────
    public void showEventNotificationConfig(String bucket) {
        System.out.println("  S3 Event Notification Configuration (educational — not setting real notifications)\n");
        System.out.println("  To configure event notifications, you need a target (Lambda, SQS, or SNS).");
        System.out.println("  Here's what the configuration looks like:\n");

        System.out.println("""
                  // Lambda notification for image uploads
                  LambdaFunctionConfiguration lambdaConfig = LambdaFunctionConfiguration.builder()
                      .id("image-processing")
                      .lambdaFunctionArn("arn:aws:lambda:region:account:function:resize-image")
                      .events(Event.S3_OBJECT_CREATED)
                      .filter(NotificationConfigurationFilter.builder()
                          .key(S3KeyFilter.builder()
                              .filterRules(FilterRule.builder().name("prefix").value("images/").build(),
                                           FilterRule.builder().name("suffix").value(".jpg").build())
                              .build())
                          .build())
                      .build();
                
                  // SQS notification for all deletes
                  QueueConfiguration sqsConfig = QueueConfiguration.builder()
                      .id("delete-audit")
                      .queueArn("arn:aws:sqs:region:account:delete-audit-queue")
                      .events(Event.S3_OBJECT_REMOVED)
                      .build();
                
                  // EventBridge (catches everything, route with rules)
                  EventBridgeConfiguration eventBridge = EventBridgeConfiguration.builder().build();
                """);

        System.out.println("  Event types available:");
        System.out.println("    s3:ObjectCreated:*  — Put, Post, Copy, CompleteMultipartUpload");
        System.out.println("    s3:ObjectRemoved:*  — Delete, DeleteMarkerCreated");
        System.out.println("    s3:ObjectRestore:*  — Post, Completed (Glacier restore)");
        System.out.println("    s3:Replication:*    — Replication failures");
        System.out.println("    s3:LifecycleTransition, s3:IntelligentTiering");
        System.out.println("\n  Delivery: AT-LEAST-ONCE (duplicates possible!)");
        System.out.println("  → Handlers MUST be idempotent");
        System.out.println("  → Use 'sequencer' field for ordering");
    }

    // ──────────────────────────────────────────────
    //  6) Show bucket stats
    // ──────────────────────────────────────────────
    public void showBucketStats(String bucket) {
        System.out.println("  Bucket: " + bucket + "\n");

        // Versioning status
        GetBucketVersioningResponse versioning = client.getBucketVersioning(
                GetBucketVersioningRequest.builder().bucket(bucket).build());
        System.out.println("    Versioning : " + (versioning.status() != null ? versioning.status() : "Not enabled"));

        // Encryption
        try {
            GetBucketEncryptionResponse encryption = client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucket).build());
            encryption.serverSideEncryptionConfiguration().rules().forEach(rule ->
                    System.out.println("    Encryption : " + rule.applyServerSideEncryptionByDefault().sseAlgorithm()));
        } catch (S3Exception e) {
            System.out.println("    Encryption : Default (SSE-S3)");
        }

        // Location
        GetBucketLocationResponse location = client.getBucketLocation(
                GetBucketLocationRequest.builder().bucket(bucket).build());
        System.out.println("    Region     : " + location.locationConstraintAsString());

        // Object count (sample via listing)
        ListObjectsV2Response listResp = client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).maxKeys(1000).build());
        int count = listResp.contents().size();
        long totalSize = listResp.contents().stream().mapToLong(S3Object::size).sum();
        System.out.println("    Objects    : " + count + (listResp.isTruncated() ? "+" : ""));
        System.out.println("    Total size : " + formatBytes(totalSize));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
