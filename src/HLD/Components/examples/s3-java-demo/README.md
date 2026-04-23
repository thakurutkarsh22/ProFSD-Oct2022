# AWS S3 Java Playground

Interactive CLI that lets you **upload, download, multipart upload, presigned URL, version, encrypt, lifecycle, security policy, and performance-test** against **real AWS S3 buckets** — then verify everything in the AWS Console. Each operation prints a concept explanation before executing.

## Prerequisites

| What | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| AWS CLI | v1 or v2 | `aws --version` |
| AWS Credentials configured | — | `aws sts get-caller-identity` |

## AWS Setup (one-time, ~3 minutes)

### 1. Create an IAM User

1. Go to **AWS Console → IAM → Users → Create user**
2. Name: `s3-demo-user`
3. Attach policy: **AmazonS3FullAccess**
4. Go to **Security credentials → Create access key → CLI**
5. Copy the **Access Key ID** and **Secret Access Key**

### 2. Configure AWS CLI

```bash
aws configure
```

Enter when prompted:
```
AWS Access Key ID:      <your-access-key>
AWS Secret Access Key:  <your-secret-key>
Default region name:    ap-south-1       # or your preferred region
Default output format:  json
```

### 3. Verify access

```bash
aws sts get-caller-identity
# Should print your account ID + ARN
```

### 4. IAM permissions needed

Your IAM user/role needs **AmazonS3FullAccess** (or these specific actions):
- `s3:CreateBucket`, `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
- `s3:ListBucket`, `s3:HeadObject`, `s3:HeadBucket`
- `s3:PutBucketVersioning`, `s3:PutLifecycleConfiguration`, `s3:PutBucketPolicy`
- `s3:PutBucketEncryption`, `s3:GetBucketVersioning`, `s3:GetBucketEncryption`
- `s3:ListBucketVersions`, `s3:GetBucketLocation`, `s3:GetLifecycleConfiguration`
- `s3:GetBucketPolicy`

The app will **auto-create** these 2 buckets on startup:
- `s3-demo-playground-<username>` — Standard bucket for most demos
- `s3-demo-versioned-<username>` — Versioning-enabled bucket

## How to Run

```bash
# From the repo root:
cd src/HLD/Components/examples/s3-java-demo

# Compile and run (first time downloads dependencies, ~30s):
mvn compile exec:java

# Subsequent runs are fast (~1s compile):
mvn compile exec:java
```

The interactive menu will appear. Type a number and press Enter.

### Recommended walkthrough order

```
Step 1:   Press 1   → PUT simple object           (learn: write path, ETag, immutability)
Step 2:   Press 7   → GET object                  (learn: read path, erasure decode)
Step 3:   Press 8   → HEAD object                 (learn: metadata without download)
Step 4:   Press 2   → PUT with metadata            (learn: system vs user-defined metadata)
Step 5:   Press 3   → PUT with storage class       (learn: S3 storage tiers, cost spectrum)
Step 6:   Press 4   → PUT with encryption          (learn: SSE-S3, SSE-KMS, SSE-C)
Step 7:   Press 5   → Multipart upload             (learn: large objects, parallel parts, ETag format)
Step 8:   Press 6   → Presigned PUT URL            (learn: temporary access, no credentials on client)
Step 9:   Press 11  → Presigned GET URL            (learn: shareable download links)
Step 10:  Press 9   → Range GET                    (learn: partial downloads, video seeking)
Step 11:  Press 10  → LIST with prefix             (learn: pagination, 1000/call limit)
Step 12:  Press 12  → DELETE object                (learn: idempotent delete, versioning behavior)
Step 13:  Press 13  → COPY object                  (learn: server-side copy, "rename" pattern)
Step 14:  Press 14  → Full versioning lifecycle    (learn: versions, delete markers, restore)
Step 15:  Press 16  → Consistency demo             (learn: strong read-after-write since 2020)
Step 16:  Press 17  → Set lifecycle rules          (learn: waterfall tiering, multipart cleanup)
Step 17:  Press 19  → Set default encryption       (learn: SSE-S3 + Bucket Key)
Step 18:  Press 20  → Set security policy          (learn: enforce encryption + HTTPS)
Step 19:  Press 22  → Distributed prefix upload    (learn: partition model, 5500/3500 per prefix)
Step 20:  Press 23  → Parallel upload              (learn: multi-threaded throughput)
Step 21:  Press 24  → Bucket stats                 (learn: bucket inspection checklist)
```

## What you can do

```
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
```

## What to observe in AWS Console

After running each operation, open **AWS Console → S3** (in your region) and:

| Operation | What to look for in Console |
|-----------|---------------------------|
| PUT simple | Object appears in bucket, check Properties for ETag |
| PUT with metadata | Click object → Properties → Metadata section shows custom keys |
| PUT with storage class | Properties → Storage class shows chosen tier |
| PUT with encryption | Properties → Server-side encryption shows AES-256 |
| Multipart upload | Object appears, ETag format is "hash-N" (N = parts count) |
| Presigned PUT | Object uploaded without your server touching it |
| GET/HEAD | Check CloudTrail → Event history for GetObject/HeadObject events |
| Range GET | Same object, but only partial bytes transferred |
| LIST | Compare pagination token behavior |
| DELETE (no versioning) | Object disappears |
| DELETE (with versioning) | Object hidden by delete marker; old versions still visible |
| Versioning lifecycle | See all versions + delete markers in the Versions tab |
| Lifecycle rules | Bucket → Management → Lifecycle rules shows all 3 rules |
| Encryption config | Bucket → Properties → Default encryption |
| Bucket policy | Bucket → Permissions → Bucket policy shows JSON |
| Distributed prefixes | Objects spread across 00/, 01/, 02/... prefixes |
| Parallel upload | Many objects created rapidly |

## Concepts demonstrated

Each menu option prints a detailed concept explanation before executing. Concepts covered:

| # | Concept | Doc Section |
|---|---------|-------------|
| 1 | **Write Path** — PUT flow, erasure coding, ETag | §2 Core Architecture |
| 2 | **Object Metadata** — system vs user-defined, 2KB limit | §2 Core Architecture |
| 3 | **Storage Classes** — 8 tiers from Express to Deep Archive | §6 Storage Classes |
| 4 | **Encryption** — SSE-S3, SSE-KMS, SSE-C, TLS | §8 Security Model |
| 5 | **Multipart Upload** — parallel parts, retry, ETag hash-of-hashes | §7 Performance |
| 6 | **Presigned URLs** — temporary access, no credentials on client | §7 Performance |
| 7 | **Read Path** — GET flow, range GET, conditional GET | §2 Core Architecture |
| 8 | **HEAD** — metadata without download, cost-efficient checks | §2 Core Architecture |
| 9 | **Range GET** — partial download, video seeking, parallel reads | §7 Performance |
| 10 | **LIST & Pagination** — 1000/call limit, S3 Inventory alternative | §13 Limitations |
| 11 | **Presigned GET** — shareable download links | §7 Performance |
| 12 | **Delete Semantics** — idempotent, delete markers | §10 Advanced Features |
| 13 | **Copy (Rename)** — server-side, metadata change, cross-account | §10 Advanced Features |
| 14 | **Versioning** — lifecycle, delete markers, restore, permanent delete | §10 Advanced Features |
| 16 | **Strong Consistency** — read-after-write since Dec 2020 | §5 Consistency Model |
| 17 | **Lifecycle Rules** — tiering waterfall, multipart cleanup | §6 Storage Classes |
| 19-20 | **Security Policy** — enforce encryption, HTTPS, Capital One lesson | §8 Security Model |
| 21 | **Event Notifications** — Lambda, SQS, SNS, EventBridge patterns | §9 Event-Driven Architecture |
| 22 | **Prefix Distribution** — partition model, 5500/3500 per prefix limits | §7 Performance |
| 23 | **Parallel Upload** — multi-threaded throughput, horizontal scaling | §7 Performance |

## Project structure

```
s3-java-demo/
├── pom.xml                              # AWS SDK v2 (S3, STS, Transfer Manager) + Gson
└── src/main/java/com/demo/s3/
    ├── S3Playground.java                # Interactive CLI (main class)
    ├── S3Config.java                    # Client bootstrap, creates 2 buckets
    ├── S3Uploader.java                  # PUT, multipart, presigned, metadata, storage class,
    │                                    #   encryption, distributed prefix, parallel upload, copy
    ├── S3Downloader.java                # GET, HEAD, range GET, LIST, presigned GET, delete,
    │                                    #   consistency demo
    ├── S3VersioningDemo.java            # Full versioning lifecycle, list versions, permanent delete
    ├── S3LifecycleDemo.java             # Lifecycle rules, encryption config, bucket policy,
    │                                    #   event notification info, bucket stats
    └── ConceptExplainer.java            # Concept explanations printed before each operation
```

## Changing the region

Edit `S3Config.java`:
```java
private static final Region REGION = Region.AP_SOUTH_1;  // Mumbai (current)
```

## Cleanup

Delete the buckets when done:
```bash
# First empty the buckets (including all versions)
aws s3api list-object-versions --bucket s3-demo-playground-$USER --output json \
  | jq -r '.Versions[]? | "--key \(.Key) --version-id \(.VersionId)"' \
  | xargs -L1 -I{} sh -c 'aws s3api delete-object --bucket s3-demo-playground-$USER {}'

aws s3api list-object-versions --bucket s3-demo-playground-$USER --output json \
  | jq -r '.DeleteMarkers[]? | "--key \(.Key) --version-id \(.VersionId)"' \
  | xargs -L1 -I{} sh -c 'aws s3api delete-object --bucket s3-demo-playground-$USER {}'

aws s3 rb s3://s3-demo-playground-$USER --force
aws s3 rb s3://s3-demo-versioned-$USER --force
```

Also delete the IAM access key from **AWS Console → IAM → Users → s3-demo-user → Security credentials → Delete access key**.

## Cost

S3 has a **free tier of 5 GB storage + 20,000 GET + 2,000 PUT per month** (first 12 months). This demo uses minimal storage and ~100-500 requests per session — effectively **free**.

The multipart demo creates a 10-50 MB file — remember to delete it or let lifecycle rules clean up.
