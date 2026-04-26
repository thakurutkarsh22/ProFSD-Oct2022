# Zero-Downtime Schema Migration

> **TL;DR.** You cannot rename, drop, or re-type a column in one step on a live system — old and new code run side-by-side during the deploy. Every schema change is a **multi-phase migration**: **expand** (add new, keep old), **dual-write / backfill**, **migrate readers**, **migrate writers**, **contract** (drop the old). Skip a phase and you get lost writes, broken reads, or 3-hour maintenance windows.

---

## 1. Why "single-step" migrations break

At deploy time, your fleet has both old and new binaries running simultaneously for ~5 minutes (rolling deploy). During that interval:

- **Old code** expects the old schema.
- **New code** expects the new schema.
- **Deploys can be rolled back** — so your schema must be compatible with whatever binary is running.

Add a `NOT NULL` column without a default in one step → the old code still INSERTs without it → every old-code INSERT fails → partial outage for the deploy window. And when you roll back the bad binary, the column is still there but the old code doesn't know about it.

The rule every senior engineer internalises: **schema changes are forward- and backward-compatible across at least one deploy cycle**. That is the whole discipline.

---

## 2. The Expand / Contract (a.k.a. "parallel change") pattern

```
┌──────────────────────────────────────────────────────────────────┐
│                     EXPAND-CONTRACT PIPELINE                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Phase 1: EXPAND                                                 │
│    - Add new column / table / index                              │
│    - Nullable, with default, no app changes yet                  │
│                                                                  │
│  Phase 2: DUAL-WRITE                                             │
│    - Deploy code that writes BOTH old and new                    │
│    - Reads still use old                                         │
│                                                                  │
│  Phase 3: BACKFILL                                               │
│    - Batch job copies historical rows old → new                  │
│    - Verify: for every row, old == new                           │
│                                                                  │
│  Phase 4: DUAL-READ / SHADOW-READ                                │
│    - New code reads new; compares vs old for N days              │
│    - Alerts on mismatch                                          │
│                                                                  │
│  Phase 5: CUTOVER                                                │
│    - Flip reads to new                                           │
│    - Stop writing old (stop the dual-write)                      │
│                                                                  │
│  Phase 6: CONTRACT                                               │
│    - Drop old column / table / index                             │
│    - Usually weeks after cutover                                 │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

Each step is individually reversible. You can stop or roll back between any two phases without data loss.

---

## 3. The cheat-sheet for every kind of change

### 3.1 Add a column

| Phase | Action |
|-------|--------|
| Expand | `ALTER TABLE users ADD COLUMN display_name TEXT NULL` |
| Dual-write | New code writes to `display_name`; old code ignores it (fine, it's nullable) |
| Backfill | Batch `UPDATE users SET display_name = full_name WHERE display_name IS NULL` in chunks |
| Cutover | New code reads `display_name` |
| Contract | Once `full_name` is no longer used: `ALTER TABLE users DROP COLUMN full_name` (next deploy cycle) |

**Gotchas:**
- In MySQL / PostgreSQL, `ADD COLUMN ... DEFAULT 'x'` historically rewrote the whole table → long lock. Modern Postgres (11+) adds it as metadata (fast). MySQL 8 has `ALGORITHM=INSTANT`. *Always* check your DB version's behaviour before running in prod.

### 3.2 Rename a column

**Never** `ALTER TABLE ... RENAME COLUMN` in one step. Instead:

1. Add new column `display_name` (expand).
2. Dual-write: every writer sets both `name` and `display_name`.
3. Backfill `display_name` from `name`.
4. Migrate readers to `display_name`.
5. Stop writing `name`.
6. Drop `name`.

### 3.3 Change a column type (e.g. VARCHAR → INT)

Same as rename but with a converter in the dual-write:

```python
display_name_v2 = parse_new_format(display_name_v1)
row.display_name_old = display_name_v1
row.display_name_new = display_name_v2
```

Includes shadow-compare to verify parity.

### 3.4 Drop a column

1. Stop reading it (deploy & wait for rollout).
2. Stop writing it (deploy & wait).
3. Soft-drop: rename to `zz_deprecated_columnname` for a release.
4. Hard-drop: `ALTER TABLE ... DROP COLUMN zz_deprecated_columnname`.

The intermediate rename is the "oh crap" escape hatch — if anything breaks, you can rename it back fast.

### 3.5 Split a table

Expand: add new sibling table. Dual-write to both. Backfill history. Migrate readers. Stop old writes. Drop old table. Typically 2–4 weeks of calendar time.

### 3.6 Add an index

- **PostgreSQL:** `CREATE INDEX CONCURRENTLY` — no table lock, but takes longer. Must be run outside a transaction.
- **MySQL (InnoDB):** `ALGORITHM=INPLACE, LOCK=NONE` for most index types on 5.6+.
- **Always build a replica first and time the operation** — an unexpected 6-hour index build blocks replication and everything else.

### 3.7 Drop an index

Usually safe to drop, but:

- Benchmark with the index *disabled* first: `SET enable_indexscan = off` / hint queries. If performance is fine, drop.
- Keep the DDL for a few days so you can add it back quickly if wrong.

### 3.8 Change a foreign key / constraint

1. Add the new FK as `NOT VALID` (Postgres; metadata-only).
2. Run `ALTER TABLE ... VALIDATE CONSTRAINT ...` (scans rows but no lock).
3. Remove the old one.

### 3.9 Add `NOT NULL`

Never in one step. 

1. Add the column `NULL` with a default.
2. Backfill to eliminate NULLs.
3. Change code to always provide the value.
4. Add a `CHECK ... IS NOT NULL NOT VALID` (Postgres), validate later.
5. Only then add `NOT NULL` (cheaper because the check already validated).

---

## 4. The sequence diagram of a deploy

```mermaid
sequenceDiagram
    autonumber
    participant DBA
    participant DB as Database
    participant CI as CI/CD
    participant App1 as Old binary
    participant App2 as New binary

    DBA->>DB: ALTER TABLE ADD COLUMN new_col NULL
    Note over DB: Phase 1 done — reversible

    CI->>App1: deploy new binary (dual-write)
    Note over App1,App2: Mixed fleet for ~5 min
    App2->>DB: INSERT sets new_col; old_col too
    App1->>DB: INSERT sets old_col (ignores new)

    CI->>DBA: kick off backfill job
    DBA->>DB: UPDATE new_col = f(old_col) in chunks
    Note over DB: Phase 3 done

    CI->>App2: deploy reader migration (flag-gated)
    App2->>DB: SELECT new_col (compare w/ old_col first 7 days)

    Note over App2: monitor mismatch metric = 0
    CI->>App2: flip flag, stop reading old_col
    CI->>App2: stop dual-write

    DBA->>DB: ALTER TABLE DROP COLUMN old_col
    Note over DB: Phase 6 done
```

Every arrow is reversible except the final drop.

---

## 5. Long-running migrations — batch, don't bulk

A single `UPDATE users SET x = y` on a 500M-row table will lock huge ranges and lag replication. Do it in chunks:

```sql
-- pseudo-code
loop:
    UPDATE users
    SET   display_name = full_name
    WHERE id BETWEEN :start AND :start + 1000
      AND display_name IS NULL;

    commit;
    sleep(50 ms);    -- throttle
    start += 1000;
```

- Chunk size 1K–10K rows, tuned by monitoring replication lag.
- Sleep between chunks — gives replication and query load a breather.
- Use WHERE predicates that hit indexes, not full scans.
- Checkpoint progress so interruption is resumable.

Tools that automate this: **gh-ost** (GitHub's online schema change for MySQL), **pt-online-schema-change** (Percona), **pgroll** (xata), **Liquibase** / **Flyway** for versioning.

---

## 6. Dealing with huge tables — shadow-table pattern

For risky alterations on tables that can't tolerate any lock:

```
1. Create new_table (same schema + changes applied)
2. Add triggers on old_table → copy insert/update/delete to new_table
3. Backfill existing rows in chunks (from old_table snapshot) to new_table
4. Verify checksums / row counts match
5. Atomically rename:
     RENAME TABLE orders TO orders_old, new_table TO orders;
6. Drop orders_old after a week
```

This is exactly what `gh-ost` / `pt-online-schema-change` do internally.

---

## 7. Non-relational migrations (bonus)

- **Cassandra:** schema change is a `nodetool` operation; need all nodes on the same schema version (monitor `schema_agreement`). Use versioned columns or separate tables for big changes.
- **DynamoDB:** no schema for attributes — but GSIs are costly to create (read/write capacity). Plan capacity headroom.
- **MongoDB:** application-level expand/contract because schema is per-document. Write converters inline: `if doc.version == 1 upgrade_to_v2(doc)`.

---

## 8. Coordinating with application deploys

Two rules:

1. **DDL precedes code that needs it.** Column must exist before the code that SELECTs it runs on *any* node.
2. **Code that needs the old must be gone before you DROP.** Otherwise rollback is dangerous.

Ordering becomes:

```
deploy A → add column (no code dependency)  -----+
deploy B → dual-write code                       |  forward-compatible
…                                                |
deploy C → reader migration (flag-gated)         |
…                                                |
deploy D → flip flag                             v
deploy E → stop writing old column
deploy F → drop column
```

If you need to roll back, you roll back *one deploy* — the fleet stays in a valid state.

---

## 9. Feature flags + schema migrations

Use a **feature flag** for the reader flip. Bake the migration code into the binary; expose which path to use via config. This decouples **code deploy** from **migration cutover**, letting you:

- Canary (5%) the new read path for a day.
- Roll back instantly without redeploying.
- Test in staging without branching infrastructure.

---

## 10. Common pitfalls

| Pitfall | Consequence |
|---------|-------------|
| `ALTER TABLE ... RENAME COLUMN` in one step | Deploy window = outage |
| `NOT NULL` before backfill | Legacy rows fail validation |
| Backfill in a single transaction on a big table | Locks rows forever; replication lag; OOM |
| Skipping the dual-write phase | Lost writes during deploy |
| Dropping a column the day readers are flipped | Rollback of reader flip → app expects column that was dropped |
| Not alerting on shadow-read mismatches | Silent data drift; nobody notices the migration is wrong |
| Using an ORM auto-migration tool in prod without review | `DROP COLUMN` accidentally approved → data gone |
| Forgetting replication lag during backfill | Replicas fall behind, reads get stale |

---

## 11. Interview talking points

- **Always say "expand-contract" or "parallel change".** Those are the recognised terms.
- **Walk six phases explicitly.** Expand, dual-write, backfill, dual-read + verify, cutover, contract. Describing all six signals you've done this in production.
- **Batch the backfill.** Size the chunks, pause for replication lag.
- **Use a feature flag for the reader flip.** Call this out.
- **Deploy compatibility.** "Every migration step must be safe with both old and new binaries in the fleet — rolling deploy assumption."
- **Cite a tool.** gh-ost / pt-osc / pgroll / Liquibase. Demonstrates pragmatism.
- **Mention observability.** Lag, mismatch counter, query latency during backfill.

---

## 12. Related reading

- [../01-Fundamentals/03-Databases.md](../01-Fundamentals/03-Databases.md) — transactions, locks, isolation.
- [../02-BuildingBlocks/02-DatabaseScaling.md](../02-BuildingBlocks/02-DatabaseScaling.md) — sharding / resharding implications.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — CDC for backfill and dual-write.
- [12-SplittingTheMonolith.md](12-SplittingTheMonolith.md) — same pattern applied to moving data between services.
