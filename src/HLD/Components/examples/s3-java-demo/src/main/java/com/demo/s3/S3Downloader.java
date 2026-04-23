package com.demo.s3;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

public class S3Downloader {

    private final S3Client client;
    private final S3Presigner presigner;

    public S3Downloader(S3Client client, S3Presigner presigner) {
        this.client = client;
        this.presigner = presigner;
    }

    // ──────────────────────────────────────────────
    //  1) Simple GET — download and display object
    // ──────────────────────────────────────────────
    public void getObject(String bucket, String key) {
        try (ResponseInputStream<GetObjectResponse> resp = client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())) {

            GetObjectResponse metadata = resp.response();
            String content = new BufferedReader(new InputStreamReader(resp, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            System.out.println("  GET successful:");
            System.out.println("    Key          : " + key);
            System.out.println("    Content-Type : " + metadata.contentType());
            System.out.println("    Size         : " + metadata.contentLength() + " bytes");
            System.out.println("    ETag         : " + metadata.eTag());
            System.out.println("    Last Modified: " + metadata.lastModified());
            System.out.println("    Storage Class: " + metadata.storageClassAsString());
            if (metadata.versionId() != null) {
                System.out.println("    Version ID   : " + metadata.versionId());
            }
            System.out.println("    Encryption   : " + metadata.serverSideEncryptionAsString());
            System.out.println("\n    Content (first 500 chars):");
            System.out.println("    " + content.substring(0, Math.min(500, content.length())));
        } catch (NoSuchKeyException e) {
            System.out.println("  ERROR: Key not found: " + key);
        } catch (Exception e) {
            System.out.println("  ERROR: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  2) HEAD — get metadata without downloading
    // ──────────────────────────────────────────────
    public void headObject(String bucket, String key) {
        try {
            HeadObjectResponse resp = client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

            System.out.println("  HEAD (metadata only, no download):");
            System.out.println("    Key          : " + key);
            System.out.println("    Content-Type : " + resp.contentType());
            System.out.println("    Size         : " + resp.contentLength() + " bytes");
            System.out.println("    ETag         : " + resp.eTag());
            System.out.println("    Last Modified: " + resp.lastModified());
            System.out.println("    Storage Class: " + resp.storageClassAsString());
            System.out.println("    Encryption   : " + resp.serverSideEncryptionAsString());
            if (resp.versionId() != null) {
                System.out.println("    Version ID   : " + resp.versionId());
            }
            if (!resp.metadata().isEmpty()) {
                System.out.println("    Custom metadata:");
                resp.metadata().forEach((k, v) -> System.out.println("      " + k + " = " + v));
            }
            System.out.println("\n  HEAD is free (no data transfer). Use it to check existence/metadata.");
        } catch (NoSuchKeyException e) {
            System.out.println("  Key does not exist: " + key);
        }
    }

    // ──────────────────────────────────────────────
    //  3) Range GET — download partial content
    // ──────────────────────────────────────────────
    public void rangeGet(String bucket, String key, long startByte, long endByte) {
        try (ResponseInputStream<GetObjectResponse> resp = client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .range("bytes=" + startByte + "-" + endByte)
                        .build())) {

            String content = new BufferedReader(new InputStreamReader(resp, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            System.out.println("  Range GET:");
            System.out.println("    Key        : " + key);
            System.out.println("    Range      : bytes=" + startByte + "-" + endByte);
            System.out.println("    Received   : " + resp.response().contentLength() + " bytes");
            System.out.println("    Content-Range: " + resp.response().contentRange());
            System.out.println("\n    Content:");
            System.out.println("    " + content);
            System.out.println("\n  Range GETs are used for: resume downloads, parallel reads, video seeking");
        } catch (Exception e) {
            System.out.println("  ERROR: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  4) LIST objects with prefix
    // ──────────────────────────────────────────────
    public void listObjects(String bucket, String prefix, int maxKeys) {
        ListObjectsV2Response resp = client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(maxKeys)
                        .build());

        System.out.println("  LIST objects:");
        System.out.println("    Bucket  : " + bucket);
        System.out.println("    Prefix  : " + (prefix.isEmpty() ? "(all)" : prefix));
        System.out.println("    Results : " + resp.contents().size());
        System.out.println("    Truncated: " + resp.isTruncated());
        System.out.println();

        System.out.printf("    %-50s %10s  %s%n", "KEY", "SIZE", "LAST MODIFIED");
        System.out.println("    " + "-".repeat(80));

        for (S3Object obj : resp.contents()) {
            System.out.printf("    %-50s %10d  %s%n",
                    obj.key().length() > 50 ? "..." + obj.key().substring(obj.key().length() - 47) : obj.key(),
                    obj.size(),
                    obj.lastModified());
        }

        if (resp.isTruncated()) {
            System.out.println("\n    More objects available. Use ContinuationToken for pagination.");
            System.out.println("    LIST returns max 1,000 per call. For 50M objects = 50K calls!");
            System.out.println("    Better: Use S3 Inventory for large-scale listing.");
        }
    }

    // ──────────────────────────────────────────────
    //  5) Generate presigned GET URL
    // ──────────────────────────────────────────────
    public void generatePresignedGetUrl(String bucket, String key) {
        int expiryMinutes = 15;

        PresignedGetObjectRequest presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expiryMinutes))
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build())
                        .build());

        String url = presigned.url().toString();

        System.out.println("  Presigned GET URL:");
        System.out.println("    Key     : " + key);
        System.out.println("    Expires : " + expiryMinutes + " minutes");
        System.out.println("    Method  : GET");
        System.out.println("\n    URL (paste in browser to download — no credentials needed!):");
        System.out.println("    " + url.substring(0, Math.min(150, url.length())) + "...");
        System.out.println("\n  Use case: let users download files directly from S3,");
        System.out.println("  bypassing your server. Saves bandwidth + reduces latency.");
    }

    // ──────────────────────────────────────────────
    //  6) Delete single object
    // ──────────────────────────────────────────────
    public void deleteObject(String bucket, String key) {
        DeleteObjectResponse resp = client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());

        System.out.println("  DELETE:");
        System.out.println("    Key        : " + key);
        System.out.println("    Completed  : yes");
        if (resp.versionId() != null) {
            System.out.println("    Delete marker version: " + resp.versionId());
            System.out.println("    (With versioning, DELETE creates a 'delete marker' — data is NOT removed)");
        }
        System.out.println("    Note: S3 DELETE is idempotent — deleting non-existent key returns 204.");
    }

    // ──────────────────────────────────────────────
    //  7) Demonstrate strong read-after-write consistency
    // ──────────────────────────────────────────────
    public void demonstrateConsistency(String bucket) {
        String key = "consistency-test/object-" + Instant.now().getEpochSecond() + ".txt";

        System.out.println("  Testing strong read-after-write consistency...\n");

        // Write
        System.out.println("  Step 1: PUT new object");
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromString("version-1"));
        System.out.println("    Written: 'version-1'");

        // Immediately read
        System.out.println("  Step 2: Immediate GET (no delay)");
        try (var resp = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            String content = new BufferedReader(new InputStreamReader(resp, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining());
            System.out.println("    Read: '" + content + "'");
            System.out.println("    " + (content.equals("version-1") ? "CONSISTENT" : "STALE!"));
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
        }

        // Overwrite
        System.out.println("\n  Step 3: Overwrite (PUT same key with new content)");
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromString("version-2"));
        System.out.println("    Written: 'version-2'");

        // Immediately read again
        System.out.println("  Step 4: Immediate GET (no delay)");
        try (var resp = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            String content = new BufferedReader(new InputStreamReader(resp, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining());
            System.out.println("    Read: '" + content + "'");
            System.out.println("    " + (content.equals("version-2") ? "CONSISTENT" : "STALE!"));
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
        }

        // Delete and verify
        System.out.println("\n  Step 5: DELETE then immediate GET");
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        System.out.println("    Deleted");
        try (var resp = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            System.out.println("    STALE! Object still returned after DELETE.");
        } catch (NoSuchKeyException e) {
            System.out.println("    GET returned 404 (NoSuchKey) — CONSISTENT!");
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
        }

        System.out.println("\n  Since Dec 2020, S3 provides strong read-after-write consistency");
        System.out.println("  for ALL operations (PUT, DELETE, LIST) — no performance penalty.");
    }
}
