package com.demo.s3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class S3Uploader {

    private final S3Client client;
    private final S3Presigner presigner;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public S3Uploader(S3Client client, S3Presigner presigner) {
        this.client = client;
        this.presigner = presigner;
    }

    // ──────────────────────────────────────────────
    //  1) Simple PUT — upload a small text object
    // ──────────────────────────────────────────────
    public void putObject(String bucket, String key, String content) {
        PutObjectResponse resp = client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromString(content));

        System.out.println("  PUT successful:");
        System.out.println("    Bucket  : " + bucket);
        System.out.println("    Key     : " + key);
        System.out.println("    ETag    : " + resp.eTag());
        System.out.println("    Size    : " + content.length() + " bytes");
        if (resp.versionId() != null) {
            System.out.println("    VersionId: " + resp.versionId());
        }
    }

    // ──────────────────────────────────────────────
    //  2) PUT with custom metadata (user-defined)
    // ──────────────────────────────────────────────
    public void putWithMetadata(String bucket) {
        String key = "orders/order-" + UUID.randomUUID().toString().substring(0, 8) + ".json";

        Map<String, String> order = Map.of(
                "orderId", "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "customerId", "CUST-42",
                "amount", "249.99",
                "currency", "USD",
                "timestamp", Instant.now().toString()
        );

        Map<String, String> metadata = Map.of(
                "x-source", "checkout-service",
                "x-priority", "high",
                "x-environment", "production",
                "x-correlation-id", UUID.randomUUID().toString()
        );

        PutObjectResponse resp = client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/json")
                        .metadata(metadata)
                        .build(),
                RequestBody.fromString(gson.toJson(order)));

        System.out.println("  PUT with metadata:");
        System.out.println("    Key      : " + key);
        System.out.println("    ETag     : " + resp.eTag());
        System.out.println("    Metadata : " + metadata);
        System.out.println("\n  Check AWS Console → bucket → object → Properties → Metadata");
    }

    // ──────────────────────────────────────────────
    //  3) PUT with specific storage class
    // ──────────────────────────────────────────────
    public void putWithStorageClass(String bucket, StorageClass storageClass) {
        String key = "tiered/" + storageClass.toString().toLowerCase() + "-sample-" + Instant.now().getEpochSecond() + ".txt";
        String content = "This object is stored with storage class: " + storageClass;

        PutObjectResponse resp = client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .storageClass(storageClass)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromString(content));

        System.out.println("  PUT with storage class:");
        System.out.println("    Key           : " + key);
        System.out.println("    Storage Class : " + storageClass);
        System.out.println("    ETag          : " + resp.eTag());
        System.out.println("\n  Check Console → object → Properties → Storage class");
    }

    // ──────────────────────────────────────────────
    //  4) PUT with server-side encryption (SSE-S3)
    // ──────────────────────────────────────────────
    public void putWithEncryption(String bucket) {
        String key = "secure/encrypted-" + Instant.now().getEpochSecond() + ".json";
        Map<String, String> sensitiveData = Map.of(
                "ssn", "XXX-XX-1234",
                "cardLast4", "5678",
                "name", "John Doe"
        );

        PutObjectResponse resp = client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/json")
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromString(gson.toJson(sensitiveData)));

        System.out.println("  PUT with SSE-S3 encryption:");
        System.out.println("    Key        : " + key);
        System.out.println("    Encryption : " + resp.serverSideEncryptionAsString());
        System.out.println("    ETag       : " + resp.eTag());
        System.out.println("\n  Since Jan 2023, SSE-S3 is the default. This is explicit for clarity.");
        System.out.println("  For SSE-KMS, pass .serverSideEncryption(SSE_AWS_KMS) + .ssekmsKeyId(keyArn)");
    }

    // ──────────────────────────────────────────────
    //  5) Multipart upload (simulated large file)
    // ──────────────────────────────────────────────
    public void multipartUpload(String bucket, int totalSizeMB) {
        String key = "large-files/multipart-" + Instant.now().getEpochSecond() + ".bin";
        int partSizeMB = 5;
        int totalParts = (int) Math.ceil((double) totalSizeMB / partSizeMB);

        System.out.println("  Starting multipart upload:");
        System.out.println("    Key        : " + key);
        System.out.println("    Total size : " + totalSizeMB + " MB");
        System.out.println("    Part size  : " + partSizeMB + " MB");
        System.out.println("    Parts      : " + totalParts);

        // Step 1: Initiate
        CreateMultipartUploadResponse initResp = client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/octet-stream")
                        .build());
        String uploadId = initResp.uploadId();
        System.out.println("\n  Step 1 — CreateMultipartUpload:");
        System.out.println("    Upload ID: " + uploadId);

        // Step 2: Upload parts
        List<CompletedPart> completedParts = new ArrayList<>();
        System.out.println("\n  Step 2 — Uploading parts:");

        for (int partNum = 1; partNum <= totalParts; partNum++) {
            int currentPartSize = (partNum == totalParts)
                    ? (totalSizeMB % partSizeMB == 0 ? partSizeMB : totalSizeMB % partSizeMB)
                    : partSizeMB;

            byte[] data = new byte[currentPartSize * 1024 * 1024];
            Arrays.fill(data, (byte) ('A' + (partNum - 1) % 26));

            UploadPartResponse uploadResp = client.uploadPart(
                    UploadPartRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .uploadId(uploadId)
                            .partNumber(partNum)
                            .build(),
                    RequestBody.fromByteBuffer(ByteBuffer.wrap(data)));

            completedParts.add(CompletedPart.builder()
                    .partNumber(partNum)
                    .eTag(uploadResp.eTag())
                    .build());

            System.out.println("    Part " + partNum + "/" + totalParts
                    + "  size=" + currentPartSize + "MB"
                    + "  ETag=" + uploadResp.eTag());
        }

        // Step 3: Complete
        CompleteMultipartUploadResponse completeResp = client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder()
                                .parts(completedParts)
                                .build())
                        .build());

        System.out.println("\n  Step 3 — CompleteMultipartUpload:");
        System.out.println("    Location : " + completeResp.location());
        System.out.println("    ETag     : " + completeResp.eTag());
        System.out.println("    Key      : " + completeResp.key());
        System.out.println("\n  Multipart ETag format: \"hash-N\" where N = number of parts.");
        System.out.println("  This is NOT the content MD5! It's a hash-of-hashes.");
    }

    // ──────────────────────────────────────────────
    //  6) Generate presigned PUT URL
    // ──────────────────────────────────────────────
    public void generatePresignedPutUrl(String bucket) {
        String key = "uploads/presigned-" + Instant.now().getEpochSecond() + ".txt";
        int expiryMinutes = 15;

        PresignedPutObjectRequest presignedReq = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expiryMinutes))
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("text/plain")
                                .build())
                        .build());

        String presignedUrl = presignedReq.url().toString();

        System.out.println("  Generated presigned PUT URL:");
        System.out.println("    Key     : " + key);
        System.out.println("    Expires : " + expiryMinutes + " minutes");
        System.out.println("    Method  : " + presignedReq.httpRequest().method());
        System.out.println("\n    URL (truncated): " + presignedUrl.substring(0, Math.min(120, presignedUrl.length())) + "...");

        // Actually use the presigned URL to upload
        System.out.println("\n  Now uploading via presigned URL (no AWS credentials needed)...");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(presignedUrl).openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "text/plain");
            conn.setDoOutput(true);

            String content = "Uploaded via presigned URL at " + Instant.now();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("    HTTP Response: " + responseCode + " " + conn.getResponseMessage());

            if (responseCode == 200) {
                System.out.println("    Upload via presigned URL succeeded!");
                System.out.println("    The client never had AWS credentials — URL WAS the authorization.");
            }
            conn.disconnect();
        } catch (Exception e) {
            System.out.println("    Error uploading via presigned URL: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  7) Batch upload with distributed prefixes
    //     (performance optimization pattern)
    // ──────────────────────────────────────────────
    public void distributedPrefixUpload(String bucket, int count) {
        System.out.println("  Uploading " + count + " objects with distributed prefixes...");
        System.out.println("  (Hashing key to spread across partitions for higher throughput)\n");

        int prefixCount = 8;
        Map<String, Integer> prefixDistribution = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            String logicalKey = "events/event-" + i + ".json";
            String prefix = String.format("%02x", Math.abs(logicalKey.hashCode()) % prefixCount);
            String actualKey = prefix + "/" + logicalKey;

            prefixDistribution.merge(prefix, 1, Integer::sum);

            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(actualKey)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromString("{\"id\":" + i + ",\"ts\":\"" + Instant.now() + "\"}"));

            if ((i + 1) % 10 == 0) {
                System.out.println("    Uploaded " + (i + 1) + "/" + count + " objects");
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("\n  Completed in " + elapsed + " ms");
        System.out.println("  Prefix distribution (for partition spread):");
        prefixDistribution.forEach((prefix, c) ->
                System.out.println("    prefix=" + prefix + "  objects=" + c));
        System.out.println("\n  Each prefix gets its own 3,500 PUT/s and 5,500 GET/s limit.");
        System.out.println("  " + prefixCount + " prefixes → " + (prefixCount * 3500) + " PUT/s and " + (prefixCount * 5500) + " GET/s total!");
    }

    // ──────────────────────────────────────────────
    //  8) Parallel upload with thread pool
    // ──────────────────────────────────────────────
    public void parallelUpload(String bucket, int objectCount, int threads) {
        System.out.println("  Parallel upload: " + objectCount + " objects, " + threads + " threads\n");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long startTime = System.currentTimeMillis();

        List<Future<String>> futures = IntStream.range(0, objectCount)
                .mapToObj(i -> executor.submit(() -> {
                    String key = "parallel/" + String.format("%02x", i % 16) + "/obj-" + i + ".json";
                    client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .contentType("application/json")
                                    .build(),
                            RequestBody.fromString("{\"id\":" + i + ",\"thread\":\"" + Thread.currentThread().getName() + "\"}"));
                    return key;
                }))
                .toList();

        int success = 0, failed = 0;
        for (Future<String> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
                success++;
            } catch (Exception e) {
                failed++;
                System.out.println("    FAILED: " + e.getMessage());
            }
        }

        executor.shutdown();
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("  Results:");
        System.out.println("    Success  : " + success);
        System.out.println("    Failed   : " + failed);
        System.out.println("    Time     : " + elapsed + " ms");
        System.out.println("    Throughput: " + String.format("%.1f", (double) success / elapsed * 1000) + " obj/sec");
    }

    // ──────────────────────────────────────────────
    //  9) Copy object (within same bucket)
    // ──────────────────────────────────────────────
    public void copyObject(String bucket, String sourceKey, String destKey) {
        CopyObjectResponse resp = client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destKey)
                .build());

        System.out.println("  Copy complete:");
        System.out.println("    Source : " + sourceKey);
        System.out.println("    Dest   : " + destKey);
        System.out.println("    ETag   : " + resp.copyObjectResult().eTag());
        System.out.println("\n  S3 has no 'rename'. Rename = COPY + DELETE original.");
    }
}
