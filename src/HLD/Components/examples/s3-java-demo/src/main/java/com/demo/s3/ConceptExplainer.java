package com.demo.s3;

/**
 * Prints educational concept explanations before each S3 operation.
 * Maps directly to the concepts in the S3 deep-dive documentation (06-S3.md).
 */
public class ConceptExplainer {

    public static void putObject() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 PUT Object — The Write Path                           │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Client ── PUT /bucket/key ──► Frontend Fleet ──► Index Service     │
        │                                     │                               │
        │                               Erasure encode                        │
        │                               (split into K data + M parity shards) │
        │                                     │                               │
        │                      ┌──────────────┼──────────────┐                │
        │                      ▼              ▼              ▼                │
        │                   Node A         Node B         Node C              │
        │                   (AZ-1)         (AZ-2)         (AZ-3)              │
        │                                     │                               │
        │                      ◄── Quorum ACKs ──►                            │
        │                                     │                               │
        │                      Metadata committed via consensus               │
        │                      (key → shard locations)                        │
        │                                     │                               │
        │  Client ◄── 200 OK + ETag ──────────┘                              │
        │                                                                     │
        │  KEY POINTS:                                                        │
        │  • Objects are IMMUTABLE. Modification = full re-upload.            │
        │  • ETag = MD5 hash (single PUT) or hash-of-hashes (multipart)      │
        │  • Max single PUT: 5 GB. Use multipart for > 100 MB.               │
        │  • Since Jan 2023, all objects encrypted at rest by default (SSE-S3)│
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void putWithMetadata() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Object Metadata — System + User-Defined                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Every S3 object has two types of metadata:                         │
        │                                                                     │
        │  1. SYSTEM METADATA (set by S3):                                    │
        │     • Content-Type         (MIME type)                              │
        │     • Content-Length       (size in bytes)                           │
        │     • Last-Modified        (timestamp)                              │
        │     • ETag                 (hash for integrity)                     │
        │     • x-amz-server-side-encryption  (encryption algo)              │
        │     • x-amz-version-id     (if versioning enabled)                 │
        │                                                                     │
        │  2. USER-DEFINED METADATA (you set, prefixed x-amz-meta-):         │
        │     • x-amz-meta-source       = "checkout-service"                 │
        │     • x-amz-meta-priority     = "high"                             │
        │     • x-amz-meta-correlation-id = "uuid-here"                      │
        │                                                                     │
        │  LIMITS:                                                            │
        │  • User metadata total: 2 KB max                                   │
        │  • Keys are lowercased by S3                                       │
        │  • Cannot be modified without re-uploading the object              │
        │    (or use CopyObject with MetadataDirective=REPLACE)              │
        │                                                                     │
        │  USE CASE: Store tracing IDs, source info, business tags           │
        │  without modifying the object body.                                │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void storageClasses() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Storage Classes — The Cost Spectrum                      │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  HOT ◄──────────────────────────────────────────────────────► COLD │
        │                                                                     │
        │  Express     Standard    Standard-IA   Glacier     Deep Archive    │
        │  One Zone                               Instant                     │
        │  $0.16/GB    $0.023/GB   $0.0125/GB    $0.004/GB   $0.00099/GB    │
        │  <10ms       <100ms      <100ms         ms          12-48 hrs     │
        │  1 AZ        3+ AZs     3+ AZs        3+ AZs      3+ AZs        │
        │                                                                     │
        │  GOTCHAS:                                                           │
        │  • Standard-IA: minimum 128 KB charge (small objects cost more)    │
        │  • Standard-IA: minimum 30-day charge (delete early = pay 30d)     │
        │  • Glacier: retrieval fees can exceed storage savings              │
        │  • Deep Archive: minimum 180-day charge                            │
        │  • Intelligent Tiering: $0.0025/1000 objects/month monitoring fee  │
        │                                                                     │
        │  PRO TIP: Storage cost is rarely the biggest S3 expense.           │
        │  Egress ($0.09/GB) and API calls often dominate the bill.          │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void encryption() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Server-Side Encryption                                │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Three modes of server-side encryption:                             │
        │                                                                     │
        │  1. SSE-S3 (AES-256):                                              │
        │     • S3 manages keys — zero effort from you                       │
        │     • Default since Jan 2023 for all new objects                   │
        │     • Free, no extra charges                                       │
        │                                                                     │
        │  2. SSE-KMS:                                                        │
        │     • AWS KMS manages keys — you control rotation, policies        │
        │     • CloudTrail audit trail for every key usage                    │
        │     • $0.03 per 10,000 requests (can add up!)                      │
        │     • Use Bucket Keys to reduce KMS costs by 99%                   │
        │                                                                     │
        │  3. SSE-C:                                                          │
        │     • YOU provide the encryption key with every request            │
        │     • S3 encrypts/decrypts but never stores your key               │
        │     • You manage key lifecycle (lose key = lose data)              │
        │                                                                     │
        │  IN TRANSIT: Always TLS 1.2+. Enforce via bucket policy:           │
        │  "Condition": {"Bool": {"aws:SecureTransport": "false"}}           │
        │  → Denies all HTTP (non-HTTPS) requests                            │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void multipartUpload() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Multipart Upload — For Large Objects                     │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Client                                      S3                    │
        │    │                                          │                    │
        │    │── CreateMultipartUpload ────────────────▶│                    │
        │    │◀── Upload ID: "abc123" ─────────────────│                    │
        │    │                                          │                    │
        │    │── Upload Part 1 (parallel) ────────────▶│  ┌────────────┐    │
        │    │── Upload Part 2 (parallel) ────────────▶│  │ Temp       │    │
        │    │── Upload Part 3 (parallel) ────────────▶│  │ storage    │    │
        │    │◀── ETag per part ───────────────────────│  └────────────┘    │
        │    │                                          │                    │
        │    │── CompleteMultipartUpload ──────────────▶│── Assemble ──►    │
        │    │◀── 200 OK (final ETag) ─────────────────│                    │
        │                                                                     │
        │  RULES:                                                             │
        │  • Required for objects > 5 GB. Recommended for > 100 MB.          │
        │  • Part size: 5 MB – 5 GB. Max 10,000 parts.                      │
        │  • Parts can be uploaded in parallel → saturate bandwidth           │
        │  • Individual part retry (not whole file!)                          │
        │  • Multipart ETag format: "hash-N" (NOT content MD5!)              │
        │                                                                     │
        │  CLEANUP GOTCHA:                                                    │
        │  Incomplete multipart uploads still cost $$$.                      │
        │  Always set lifecycle rule: AbortIncompleteMultipartUpload Days=7  │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void presignedUrl() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Presigned URLs — Secure Temporary Access                 │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Backend                    Client                   S3            │
        │    │◀── "I want to upload" ──│                       │            │
        │    │                          │                       │            │
        │    │── Generate presigned ───┤                       │            │
        │    │   URL (SigV4 signature) │                       │            │
        │    │                          │                       │            │
        │    │── Return URL ──────────▶│                       │            │
        │    │                          │── PUT directly ─────▶│            │
        │    │                          │◀── 200 OK ──────────│            │
        │                                                                     │
        │  SECURITY:                                                          │
        │  • URL expires after N minutes (you set, max 7 days with SigV4)   │
        │  • Method-locked (PUT URL can't be used for GET)                   │
        │  • Key-locked (URL only works for the specified object key)         │
        │  • No AWS credentials ever touch the client                        │
        │                                                                     │
        │  USE CASES:                                                         │
        │  • File uploads from mobile/web → directly to S3 (skip your server)│
        │  • Shareable download links (temporary access to private objects)  │
        │  • Reduces bandwidth and latency (client ↔ S3, not via backend)   │
        │                                                                     │
        │  ANTI-PATTERN: Proxying S3 through your server. Use presigned URLs │
        │  instead! Your server never touches the file bytes.                │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void distributedPrefixes() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Distributed Prefixes — S3 Partition Performance          │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  S3 partitions by KEY PREFIX. Each prefix gets:                     │
        │    • 5,500 GET/HEAD per second                                     │
        │    • 3,500 PUT/POST/DELETE per second                              │
        │                                                                     │
        │  BAD (all same prefix → hot partition):                            │
        │    logs/2024/01/event1.json  ┐                                     │
        │    logs/2024/01/event2.json  ├── All hit SAME partition            │
        │    logs/2024/01/event3.json  ┘   Max 3,500 PUT/s!                  │
        │                                                                     │
        │  GOOD (hash prefix → distributed):                                 │
        │    00/logs/2024/01/event1.json  → Partition 1                      │
        │    03/logs/2024/01/event2.json  → Partition 2                      │
        │    07/logs/2024/01/event3.json  → Partition 3                      │
        │    ...                                                              │
        │    8 prefixes = 28,000 PUT/s + 44,000 GET/s                        │
        │                                                                     │
        │  S3 auto-partitions based on traffic, but it's GRADUAL.            │
        │  Sudden spikes → 503 Slow Down until S3 catches up.               │
        │  Pre-distributing with hash prefixes avoids this entirely.         │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void getObject() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 GET Object — The Read Path                            │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Client ── GET /bucket/key ──► Frontend ──► Index Service           │
        │                                               │                     │
        │                                       Lookup key in index           │
        │                                       (partitioned by prefix)       │
        │                                               │                     │
        │                                       Get shard locations           │
        │                                               │                     │
        │                              Read K of N shards (parallel)          │
        │                                               │                     │
        │                              Erasure decode → reconstruct object    │
        │                                               │                     │
        │  Client ◄── 200 OK + object bytes ────────────┘                    │
        │                                                                     │
        │  FEATURES:                                                          │
        │  • Range GET: download partial content (bytes=0-999)               │
        │    → Used for video seeking, resume downloads, parallel reads      │
        │  • Conditional GET: If-None-Match (ETag), If-Modified-Since        │
        │    → Returns 304 Not Modified if unchanged (saves bandwidth)       │
        │  • Strong consistency: always returns latest committed version      │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void headObject() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: HEAD Object — Metadata Without Download                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  HEAD is identical to GET but returns ONLY headers (no body).      │
        │                                                                     │
        │  Returns: Content-Type, Content-Length, ETag, Last-Modified,       │
        │           Storage-Class, Encryption, Version-ID, User-Metadata     │
        │                                                                     │
        │  USE CASES:                                                         │
        │  • Check if object exists (without downloading)                    │
        │  • Get file size before downloading                                 │
        │  • Verify Content-Type before processing                            │
        │  • Read custom metadata (x-amz-meta-*)                             │
        │                                                                     │
        │  COST: Same as GET for requests, but NO data transfer charges.     │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void rangeGet() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Range GET — Partial Object Download                      │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  GET with "Range: bytes=0-999" header:                             │
        │                                                                     │
        │  Full object (1 GB):                                               │
        │  ┌──────────────────────────────────────────────────────────┐      │
        │  │████████████████████████████████████████████████████████████│      │
        │  └──────────────────────────────────────────────────────────┘      │
        │                                                                     │
        │  Range GET (bytes 0-999):                                          │
        │  ┌────┐                                                             │
        │  │████│  ← Only download 1 KB                                      │
        │  └────┘                                                             │
        │                                                                     │
        │  USE CASES:                                                         │
        │  • Video streaming (seek to position without downloading whole)    │
        │  • Resume interrupted downloads                                    │
        │  • Parallel download (split into ranges, merge locally)            │
        │  • Read CSV/Parquet headers without full download                  │
        │                                                                     │
        │  S3 returns Content-Range header: "bytes 0-999/1073741824"         │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void listObjects() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: LIST Objects — Pagination & Performance                  │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  LIST returns objects matching a prefix, up to 1,000 per call.     │
        │                                                                     │
        │  For 50 million objects:                                            │
        │    50M / 1,000 = 50,000 API calls (sequential pagination!)         │
        │    At ~100ms each = 83 minutes to list everything                  │
        │                                                                     │
        │  BETTER ALTERNATIVES for large buckets:                             │
        │  • S3 Inventory: daily/weekly CSV/Parquet of all keys              │
        │  • Maintain your own index in DynamoDB                              │
        │  • S3 Storage Lens for analytics                                   │
        │                                                                     │
        │  LIST uses V2 API (ListObjectsV2) with ContinuationToken.         │
        │  V1 (ListObjects) uses Marker — deprecated, avoid.                │
        │                                                                     │
        │  Strong consistency: LIST immediately reflects PUT/DELETE.          │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void versioning() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Versioning                                            │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  PUT v1 → PUT v2 → PUT v3 → DELETE                                │
        │                                                                     │
        │  Version History:                                                   │
        │  ┌──────────────────────────────────────────────────┐              │
        │  │ Delete Marker │ (current)  │ ← GET returns 404   │              │
        │  │ v3            │ hidden     │                      │              │
        │  │ v2            │ hidden     │                      │              │
        │  │ v1            │ hidden     │                      │              │
        │  └──────────────────────────────────────────────────┘              │
        │                                                                     │
        │  KEY BEHAVIORS:                                                     │
        │  • Every PUT creates a new version (old versions preserved)        │
        │  • DELETE creates a "delete marker" (data NOT actually removed)    │
        │  • GET ?versionId=v1 → returns that specific version              │
        │  • Delete the delete marker → "undelete" (restores latest)        │
        │  • Delete with versionId → permanently removes that version        │
        │                                                                     │
        │  COST: ALL versions count toward storage!                          │
        │  → Use lifecycle rules to expire old versions automatically.       │
        │                                                                     │
        │  PROTECTION: Combined with Object Lock → ransomware defense.       │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void lifecycleRules() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Lifecycle Rules — Automated Cost Optimization         │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Lifecycle rules auto-transition objects down the cost ladder:      │
        │                                                                     │
        │  Day 0     ──► STANDARD ($0.023/GB)                                │
        │  Day 30    ──► STANDARD_IA ($0.0125/GB)    ← 46% savings          │
        │  Day 90    ──► GLACIER ($0.004/GB)         ← 83% savings          │
        │  Day 365   ──► DEEP_ARCHIVE ($0.00099/GB)  ← 96% savings          │
        │  Day 730   ──► DELETE                                              │
        │                                                                     │
        │  MUST-HAVE RULES:                                                   │
        │  1. AbortIncompleteMultipartUpload (7 days)                        │
        │     → Stops hidden costs from failed uploads                       │
        │  2. NoncurrentVersionExpiration (90 days)                           │
        │     → Prevents unlimited version accumulation                      │
        │                                                                     │
        │  WATERFALL MODEL: Objects can only transition DOWN                  │
        │  (Standard → IA → Glacier → Deep Archive, never up)               │
        │                                                                     │
        │  GOTCHA: Objects < 128 KB won't auto-transition                    │
        │  GOTCHA: Min storage duration charges apply                        │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void securityPolicy() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Bucket Policies — Resource-Based Access Control       │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  S3 has 5 security layers:                                         │
        │                                                                     │
        │  1. IAM Policies       → WHO can do WHAT (identity-based)          │
        │  2. Bucket Policies    → Resource-based (attached to bucket)       │
        │  3. ACLs (legacy)      → Per-object permissions (avoid these)      │
        │  4. Block Public Access → Account-wide safety net                  │
        │  5. VPC Endpoints      → Keep traffic off public internet          │
        │                                                                     │
        │  PRODUCTION MUST-HAVES:                                             │
        │  • Deny unencrypted uploads (enforce SSE)                          │
        │  • Deny HTTP (enforce HTTPS/TLS)                                   │
        │  • Block Public Access at ACCOUNT level                            │
        │  • Least-privilege IAM (no s3:* wildcards!)                        │
        │                                                                     │
        │  LESSON FROM CAPITAL ONE BREACH (2019):                            │
        │  • Overly permissive IAM role on EC2                               │
        │  • SSRF vulnerability → stole temp credentials                     │
        │  • Downloaded 106M customer records from S3                        │
        │  • Fine: $80 million                                               │
        │  Prevention: VPC endpoints, IMDSv2, least-privilege IAM            │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void eventNotifications() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Event Notifications — Event-Driven Architecture       │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │           ┌──────────┐                                             │
        │           │  S3      │                                             │
        │           │  Bucket  │                                             │
        │           └────┬─────┘                                             │
        │                │  Object created/deleted/restored                   │
        │       ┌────────┼────────┬──────────────┐                           │
        │       ▼        ▼        ▼              ▼                           │
        │  ┌────────┐┌────────┐┌────────┐┌─────────────┐                    │
        │  │ Lambda ││  SQS   ││  SNS   ││ EventBridge │                    │
        │  └────────┘└────────┘└────────┘└─────────────┘                    │
        │                                                                     │
        │  DELIVERY GUARANTEES:                                               │
        │  ⚠ AT-LEAST-ONCE (duplicates possible!)                            │
        │  ⚠ Ordering NOT guaranteed                                         │
        │  ⚠ 5-minute propagation delay for config changes                   │
        │  ⚠ Max 100 notification rules per bucket                           │
        │                                                                     │
        │  → Handlers MUST be idempotent                                     │
        │  → Use 'sequencer' field to detect ordering                        │
        │                                                                     │
        │  COMMON PATTERNS:                                                   │
        │  • Image upload → Lambda resize → S3 thumbnails                    │
        │  • CSV upload → SQS → Spark ETL → Parquet in data lake            │
        │  • Any upload → EventBridge → Macie PII scan + GuardDuty          │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void consistency() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Strong Read-After-Write Consistency                   │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  BEFORE Dec 2020 (Eventual Consistency):                           │
        │  PUT new → immediate GET could return 404                          │
        │  DELETE  → immediate GET could still return the object              │
        │  Workarounds: DynamoDB-backed layers (S3Guard, EMRFS)              │
        │                                                                     │
        │  AFTER Dec 2020 (Strong Consistency — automatic, free):            │
        │  PUT new → immediate GET returns it ✓                              │
        │  Overwrite → immediate GET returns new version ✓                   │
        │  DELETE   → immediate GET returns 404 ✓                            │
        │  LIST     → immediately reflects changes ✓                         │
        │                                                                     │
        │  HOW: Metadata service uses consensus (Paxos/Raft) across AZs.    │
        │  Reads always go through the consistent metadata layer.            │
        │  No extra latency — consistency was already on the write path.     │
        │                                                                     │
        │  NOTE: This is WITHIN a region. Cross-region replication is still  │
        │  async (eventual consistency across regions).                       │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void copyObject() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Copy Object — Server-Side Copy                        │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  S3 copies data server-side — bytes never leave AWS.               │
        │                                                                     │
        │  USE CASES:                                                         │
        │  • "Rename" (S3 has no rename — it's COPY + DELETE)                │
        │  • Change storage class (copy to same key with new class)          │
        │  • Change encryption (copy with new encryption settings)           │
        │  • Update metadata (copy with MetadataDirective=REPLACE)           │
        │  • Cross-account copy (with proper IAM permissions)                │
        │                                                                     │
        │  LIMITS:                                                            │
        │  • Single COPY: up to 5 GB. For larger, use multipart copy.       │
        │  • Cross-region copy incurs data transfer charges.                 │
        │                                                                     │
        │  PERFORMANCE: "Rename folder" with 1M objects = 1M COPY + 1M      │
        │  DELETE = 2M API calls. Design key schema to avoid renames!        │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void parallelUpload() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Parallel Upload — Maximizing S3 Throughput               │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  S3 scales horizontally. More parallel connections = more throughput│
        │                                                                     │
        │  Single-threaded:          Multi-threaded (8 threads):             │
        │  ┌─────────┐              ┌─────────┐ ┌─────────┐                 │
        │  │ Thread 1 │              │ Thread 1 │ │ Thread 2 │                 │
        │  │ obj 1    │              │ obj 1    │ │ obj 2    │                 │
        │  │ obj 2    │              └─────────┘ └─────────┘                 │
        │  │ obj 3    │              ┌─────────┐ ┌─────────┐                 │
        │  │ ...      │              │ Thread 3 │ │ Thread 4 │                 │
        │  └─────────┘              │ obj 3    │ │ obj 4    │                 │
        │  ~30 obj/sec              └─────────┘ └─────────┘                 │
        │                            ~200+ obj/sec                            │
        │                                                                     │
        │  COMBINE WITH:                                                      │
        │  • Distributed prefixes → avoid partition hot spots                │
        │  • Multipart upload → parallel parts for large files               │
        │  • Transfer Acceleration → route via CloudFront edge + backbone    │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void deleteObject() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: S3 Delete — Idempotent, Versioning-Aware                 │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  WITHOUT versioning:                                                │
        │    DELETE key → permanently removes the object                      │
        │    DELETE non-existent key → returns 204 (not an error!)            │
        │                                                                     │
        │  WITH versioning:                                                   │
        │    DELETE key → creates a DELETE MARKER (data preserved)            │
        │    GET key → 404 (hidden by marker)                                │
        │    GET key?versionId=X → still returns data                        │
        │    DELETE key?versionId=X → permanently removes that version       │
        │                                                                     │
        │  DELETE is idempotent — calling it multiple times is safe.         │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }

    public static void bucketStats() {
        System.out.println("""
        ┌─────────────────────────────────────────────────────────────────────┐
        │  CONCEPT: Bucket Inspection — Versioning, Encryption, Stats        │
        ├─────────────────────────────────────────────────────────────────────┤
        │                                                                     │
        │  Key things to check on any bucket:                                │
        │  • Versioning status (Enabled, Suspended, or not set)              │
        │  • Default encryption algorithm                                    │
        │  • Region/location constraint                                      │
        │  • Object count and total size                                     │
        │  • Lifecycle rules configured                                      │
        │  • Bucket policy (who has access?)                                 │
        │  • Block Public Access settings                                    │
        │                                                                     │
        │  For production, also monitor:                                      │
        │  • S3 Storage Lens (org-wide analytics)                            │
        │  • CloudWatch: 4xx/5xx error rates, request latency                │
        │  • CloudTrail: who accessed what, when                             │
        │                                                                     │
        └─────────────────────────────────────────────────────────────────────┘
        """);
    }
}
