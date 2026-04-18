package com.demo.kinesis;

/**
 * Prints a concept explanation before each menu action executes, so the
 * user learns the WHY as they operate the CLI.
 *
 * Keep each explanation short enough to read in 10–20 seconds.
 */
public class ConceptExplainer {

    private static void box(String title, String body) {
        String line = "─".repeat(76);
        System.out.println();
        System.out.println("┌" + line + "┐");
        System.out.println("│  " + pad("CONCEPT: " + title, 74) + "│");
        System.out.println("├" + line + "┤");
        for (String ln : body.split("\n")) {
            System.out.println("│  " + pad(ln, 74) + "│");
        }
        System.out.println("└" + line + "┘");
    }

    private static String pad(String s, int w) {
        if (s.length() >= w) return s.substring(0, w);
        return s + " ".repeat(w - s.length());
    }

    public static void putSingle() {
        box("PutRecord — one record, one HTTP round-trip", """
            Kinesis record = { PartitionKey, Data(≤1MiB), [ExplicitHashKey] }.
            PartitionKey is MD5-hashed → picks the destination shard.
            Response carries ShardId + SequenceNumber (monotonic per shard).
            Use PutRecord for low-rate producers; use PutRecords for batches.""");
    }

    public static void putClickEvent() {
        box("Structured event + per-user ordering", """
            We set PartitionKey = userId so all events for one user land
            on the SAME shard → strictly ordered by SequenceNumber.
            Events for different users spread across shards (parallelism).
            This is the 'Kafka partition key' pattern.""");
    }

    public static void putBatchUniform() {
        box("PutRecords with GOOD partition keys", """
            Each record gets PartitionKey = 'user_<random>'.
            MD5 of each PK falls uniformly across the hash space.
            → Load is spread across ALL open shards.
            Note the shard distribution in the output — should be ~even.""");
    }

    public static void putBatchHot() {
        box("PutRecords with a HOT partition key (production failure!)", """
            Every record has PartitionKey = 'hot_user'.
            MD5('hot_user') is a single fixed hash → one shard gets ALL records.
            In production: throttling, WriteProvisionedThroughputExceeded errors,
            consumer lag on that shard. THIS is the #1 real-world Kinesis pitfall.
            Fix: better PartitionKey (salted, hierarchical) OR On-Demand mode.""");
    }

    public static void putExplicitHashKey() {
        box("ExplicitHashKey — force routing to a specific shard", """
            You supply a 128-bit integer; Kinesis routes by THAT, ignoring PK.
            Use case: co-locate records from multiple producers onto same shard
            without relying on PK-MD5 coincidence.
            We compute the middle of the first shard's hash range so you see
            exactly which shard receives the record.""");
    }

    public static void stressTest() {
        box("Stress test — watch throttles and shard distribution", """
            Sends N records as fast as possible. Watch for:
              • Failed record count in PutRecords response
              • WriteProvisionedThroughputExceeded in CloudWatch
            At 1,000 rec/s/shard the throttle kicks in → FailedRecordCount > 0.
            In CloudWatch, IncomingBytes & IncomingRecords should spike.""");
    }

    public static void listShards() {
        box("Shards = units of throughput + ordering + billing", """
            A stream is a named collection of shards.
            Each shard owns a contiguous slice of the 128-bit MD5 hash space
            (HashKeyRange: starting..ending). Writes with PK hashing into
            that range go to that shard. Closed shards appear here too — they
            are post-split/merge parents still readable until retention expires.""");
    }

    public static void readFromStart() {
        box("Shard iterator: TRIM_HORIZON = oldest record in retention", """
            GetShardIterator gives you a short-lived token (5 min TTL).
            GetRecords(token) returns records + nextShardIterator.
            TRIM_HORIZON replays EVERYTHING still retained — Kinesis's killer
            feature vs SQS. Retention default=24h, max=365d.""");
    }

    public static void readLatest() {
        box("Shard iterator: LATEST = tail -f", """
            Only records arriving AFTER SubscribeToShard/GetShardIterator.
            Good for: new consumer joining a stream without re-processing backlog.
            Bad for: first-time setup (will miss old records).""");
    }

    public static void replay() {
        box("AT_TIMESTAMP — the replay superpower", """
            Start reading from records whose ApproximateArrivalTimestamp >= T.
            Use case: a consumer bug corrupted output since 2h ago — re-read
            last 2h into a fix. Kafka & Kinesis can; SQS cannot.
            Requires: retention window still covers T.""");
    }

    public static void iteratorAge() {
        box("IteratorAgeMilliseconds — the ONE metric to alarm on", """
            lagMs = (now - arrival time of oldest unread record).
            Growing monotonically → consumers can't keep up → data loss imminent
            when lag approaches retention window.
            Alert threshold = 5 min or 10% of retention, whichever smaller.""");
    }

    public static void competingConsumers() {
        box("Competing consumers — one worker per shard", """
            This is what KCL does for you automatically:
              1. Discover open shards via ListShards
              2. Assign each shard to ONE worker (lease)
              3. Worker calls GetShardIterator + GetRecords in a loop
              4. Worker checkpoints progress to a DynamoDB lease table
            We skip step 2 and 4 here — each shard is read by exactly one
            thread for LATEST, no persistent checkpoint. Run a PutRecords
            in another terminal to see records arrive LIVE.""");
    }

    public static void splitShard() {
        box("SplitShard — double capacity in a hash sub-range", """
            Parent shard's hash range [A..B] is split at midpoint M.
            Parent becomes CLOSED (no new writes, still readable for retention).
            Two children created: [A..M] and [M+1..B].
            KCL won't read children until parent is drained (SHARD_END).
            Provisioned mode only; On-Demand splits automatically.""");
    }

    public static void mergeShards() {
        box("MergeShards — combine two adjacent shards", """
            Must be ADJACENT (one shard's end = other's start - 1).
            Both become CLOSED, one child covers the union of hash ranges.
            Use to scale DOWN cost when traffic has dropped.""");
    }

    public static void retention() {
        box("Retention — how long records stay in the stream", """
            Default: 24h (free).
            Extended: up to 7d (small hourly fee).
            Long-term: up to 365d (tiered pricing).
            Increase is always safe; DECREASE is irreversible — old data purged.""");
    }

    public static void cloudwatch() {
        box("CloudWatch — the production visibility story", """
            Kinesis publishes metrics every 1 minute:
              IncomingBytes / IncomingRecords          (write volume)
              GetRecords.IteratorAgeMilliseconds       (consumer lag)
              WriteProvisionedThroughputExceeded       (hot shard!)
              ReadProvisionedThroughputExceeded        (consumer throttled)
            Set alarms on these; they are the first signals of production pain.""");
    }

    public static void describe() {
        box("DescribeStreamSummary — cheap metadata read", """
            Prefer this over DescribeStream which enumerates shards (expensive
            for large streams). Use ListShards paginated for shard enumeration.""");
    }

    public static void purge() {
        box("Kinesis has no PurgeStream — we delete + recreate", """
            Unlike SQS, Kinesis does not offer a purge API. The only way to
            drop all records is to delete the stream and re-create it.
            Takes ~30s. Downstream consumers will see ResourceNotFound briefly.""");
    }
}
