package com.demo.s3;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Bootstraps S3 client and creates demo buckets if they don't already exist.
 * Uses default credential chain (~/.aws/credentials or env vars).
 */
public class S3Config {

    private static final Region REGION = Region.AP_SOUTH_1;

    public static final String DEMO_BUCKET     = "s3-demo-playground-" + System.getenv().getOrDefault("USER", "dev");
    public static final String VERSIONED_BUCKET = "s3-demo-versioned-" + System.getenv().getOrDefault("USER", "dev");

    private final S3Client client;
    private final S3Presigner presigner;

    public S3Config() {
        this.client = S3Client.builder()
                .region(REGION)
                .build();
        this.presigner = S3Presigner.builder()
                .region(REGION)
                .build();
    }

    public void initialize() {
        System.out.println("=== Initializing S3 buckets in " + REGION + " ===\n");

        ensureBucket(DEMO_BUCKET);
        ensureBucketWithVersioning(VERSIONED_BUCKET);

        System.out.println("\n  Demo bucket     : " + DEMO_BUCKET);
        System.out.println("  Versioned bucket: " + VERSIONED_BUCKET);
        System.out.println();
    }

    private void ensureBucket(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            System.out.println("  Bucket exists: " + bucketName);
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(CreateBucketConfiguration.builder()
                            .locationConstraint(REGION.id())
                            .build())
                    .build());
            client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName).build());
            System.out.println("  Created bucket: " + bucketName);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .createBucketConfiguration(CreateBucketConfiguration.builder()
                                .locationConstraint(REGION.id())
                                .build())
                        .build());
                client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                        .bucket(bucketName).build());
                System.out.println("  Created bucket: " + bucketName);
            } else {
                throw e;
            }
        }
    }

    private void ensureBucketWithVersioning(String bucketName) {
        ensureBucket(bucketName);
        client.putBucketVersioning(PutBucketVersioningRequest.builder()
                .bucket(bucketName)
                .versioningConfiguration(VersioningConfiguration.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build())
                .build());
        System.out.println("  Versioning ENABLED on: " + bucketName);
    }

    public S3Client client()           { return client; }
    public S3Presigner presigner()     { return presigner; }
    public String demoBucket()         { return DEMO_BUCKET; }
    public String versionedBucket()    { return VERSIONED_BUCKET; }
    public Region region()             { return REGION; }

    public void close() {
        client.close();
        presigner.close();
    }
}
