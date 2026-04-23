package com.demo.s3;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class S3VersioningDemo {

    private final S3Client client;

    public S3VersioningDemo(S3Client client) {
        this.client = client;
    }

    // ──────────────────────────────────────────────
    //  1) Full versioning lifecycle: create → overwrite → delete → restore
    // ──────────────────────────────────────────────
    public void fullVersioningLifecycle(String bucket) {
        String key = "versioned/document-" + Instant.now().getEpochSecond() + ".txt";

        System.out.println("  ═══ VERSIONING LIFECYCLE DEMO ═══\n");
        System.out.println("  Bucket: " + bucket + " (versioning ENABLED)\n");

        // Step 1: Create v1
        System.out.println("  Step 1 — PUT version 1:");
        PutObjectResponse v1 = client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromString("This is version 1 of the document."));
        System.out.println("    VersionId: " + v1.versionId());
        System.out.println("    Content  : 'This is version 1 of the document.'");

        // Step 2: Overwrite → creates v2
        System.out.println("\n  Step 2 — PUT version 2 (overwrite same key):");
        PutObjectResponse v2 = client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromString("This is version 2 — updated content."));
        System.out.println("    VersionId: " + v2.versionId());
        System.out.println("    Content  : 'This is version 2 — updated content.'");

        // Step 3: Overwrite again → v3
        System.out.println("\n  Step 3 — PUT version 3:");
        PutObjectResponse v3 = client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromString("Version 3 — final draft."));
        System.out.println("    VersionId: " + v3.versionId());

        // Step 4: GET returns latest (v3)
        System.out.println("\n  Step 4 — GET (no version specified) → returns LATEST:");
        String latestContent = readContent(bucket, key, null);
        System.out.println("    Content  : '" + latestContent + "'");

        // Step 5: GET specific old version
        System.out.println("\n  Step 5 — GET with versionId (reading v1):");
        String v1Content = readContent(bucket, key, v1.versionId());
        System.out.println("    Content  : '" + v1Content + "'");
        System.out.println("    Old versions are fully accessible!");

        // Step 6: List all versions
        System.out.println("\n  Step 6 — List all versions:");
        listVersions(bucket, key);

        // Step 7: Delete (creates delete marker)
        System.out.println("\n  Step 7 — DELETE (creates delete marker, doesn't remove data):");
        DeleteObjectResponse delResp = client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        System.out.println("    Delete marker version: " + delResp.versionId());
        System.out.println("    deleteMarker: " + delResp.deleteMarker());

        // Step 8: Regular GET now returns 404
        System.out.println("\n  Step 8 — GET after delete → 404:");
        try {
            readContent(bucket, key, null);
            System.out.println("    ERROR: Should have gotten 404!");
        } catch (Exception e) {
            System.out.println("    NoSuchKey (404) — as expected. Delete marker hides the object.");
        }

        // Step 9: But specific versions still exist
        System.out.println("\n  Step 9 — GET v2 by versionId → still accessible:");
        String v2Content = readContent(bucket, key, v2.versionId());
        System.out.println("    Content  : '" + v2Content + "'");

        // Step 10: Restore by deleting the delete marker
        System.out.println("\n  Step 10 — RESTORE: delete the delete marker:");
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .versionId(delResp.versionId())
                .build());
        System.out.println("    Deleted delete marker: " + delResp.versionId());

        String restored = readContent(bucket, key, null);
        System.out.println("    GET now returns: '" + restored + "'");
        System.out.println("    Object restored! Latest version (v3) is back.");

        // Final: list all versions
        System.out.println("\n  Final — All versions:");
        listVersions(bucket, key);

        System.out.println("\n  ═══ KEY TAKEAWAYS ═══");
        System.out.println("  • Every PUT creates a new version (old versions preserved)");
        System.out.println("  • DELETE creates a 'delete marker' (data NOT removed)");
        System.out.println("  • Access any version by versionId");
        System.out.println("  • Remove delete marker to 'undelete'");
        System.out.println("  • ALL versions count toward storage cost!");
        System.out.println("  • Use lifecycle rules to expire old versions automatically");
    }

    // ──────────────────────────────────────────────
    //  2) List all object versions
    // ──────────────────────────────────────────────
    public void listVersions(String bucket, String prefix) {
        ListObjectVersionsResponse resp = client.listObjectVersions(
                ListObjectVersionsRequest.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build());

        List<ObjectVersion> versions = resp.versions();
        List<DeleteMarkerEntry> deleteMarkers = resp.deleteMarkers();

        System.out.println("    Versions (" + versions.size() + "):");
        for (ObjectVersion v : versions) {
            System.out.println("      " + (v.isLatest() ? "► CURRENT" : "  old    ")
                    + "  versionId=" + v.versionId()
                    + "  size=" + v.size()
                    + "  modified=" + v.lastModified());
        }

        if (!deleteMarkers.isEmpty()) {
            System.out.println("    Delete Markers (" + deleteMarkers.size() + "):");
            for (DeleteMarkerEntry dm : deleteMarkers) {
                System.out.println("      " + (dm.isLatest() ? "► ACTIVE " : "  old    ")
                        + "  versionId=" + dm.versionId()
                        + "  modified=" + dm.lastModified());
            }
        }
    }

    // ──────────────────────────────────────────────
    //  3) Permanently delete a specific version
    // ──────────────────────────────────────────────
    public void permanentlyDeleteVersion(String bucket, String key, String versionId) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .versionId(versionId)
                .build());

        System.out.println("  Permanently deleted:");
        System.out.println("    Key       : " + key);
        System.out.println("    VersionId : " + versionId);
        System.out.println("    This version is GONE. No recovery possible.");
        System.out.println("    (Unlike a normal DELETE which just creates a delete marker)");
    }

    private String readContent(String bucket, String key, String versionId) {
        var reqBuilder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (versionId != null) reqBuilder.versionId(versionId);

        try (var resp = client.getObject(reqBuilder.build())) {
            return new BufferedReader(new InputStreamReader(resp, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (NoSuchKeyException e) {
            throw new RuntimeException("NoSuchKey");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
