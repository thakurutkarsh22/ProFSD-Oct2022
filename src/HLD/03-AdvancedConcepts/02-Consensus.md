# Consensus Algorithms & Leader Election

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

---

## Why Consensus Matters

Distributed systems need multiple machines to **agree on a single value** — who the leader is, whether a transaction committed, what the next log entry should be, who owns a lock. Without agreement, you get split-brain, data loss, and duplicate work.

Consensus is the problem of getting a set of unreliable processes to agree on one value, even when:

- Processes **crash** (fail-stop) or run arbitrarily slowly.
- The **network drops, delays, duplicates, or reorders** messages.
- **Partitions** temporarily split the cluster.

### The FLP Impossibility (why this is hard)

Fischer, Lynch, Paterson (1985) proved that in a purely **asynchronous** network with even one faulty process, **no deterministic consensus algorithm can guarantee termination**. Real systems work around this with:

- **Timeouts / randomized backoff** (partial synchrony) — Raft, Paxos.
- **Failure detectors** — "suspect" a node after heartbeat gap.
- **Quorums** — make progress as long as a majority responds.

### What consensus buys you

| Property | Meaning |
|---|---|
| **Agreement** | All non-faulty nodes decide the same value. |
| **Validity**  | The decided value was proposed by some node. |
| **Termination** | Every non-faulty node eventually decides. |
| **Integrity** | A node decides at most once. |

---

## 1. The Core Problem

```
3 nodes need to agree: "Should we commit transaction T?"

  ┌──────┐    ┌──────┐    ┌──────┐
  │Node A│    │Node B│    │Node C│
  │ YES  │    │  ???  │   │ YES  │
  └──────┘    └──────┘    └──────┘
                │
           Network partition!
           Node B can't communicate.

  Questions:
  - Can A and C proceed without B?          → YES, they form a majority (2/3).
  - What if B rejoins with a different answer?  → It must adopt the majority's.
  - What if A crashes mid-vote?             → C + B (after heal) can still decide.
  - What if the network splits 1-1-1?        → Nobody has quorum → no progress (safe).
```

The key insight: **correctness > liveness**. A correct consensus system will rather **stall** than pick the wrong value.

---

## 2. Quorums and the Majority Rule

Almost every practical consensus algorithm uses **majority quorums** (`⌊N/2⌋ + 1`). Any two majorities must overlap in at least one node — that overlap guarantees no two conflicting values are ever chosen.

```
CLUSTER SIZE vs FAULT TOLERANCE (crash faults only)

  Nodes (N)   Quorum (Q)   Can Tolerate (F)
  ─────────   ──────────   ─────────────────
     3            2              1
     5            3              2
     7            4              3
     9            5              4

  Rule: N = 2F + 1   (odd numbers are optimal)

WHY NOT 4 NODES?
  4 nodes → quorum is 3 → still only tolerates 1 failure,
  BUT adds more network chatter and a higher chance of 2 nodes failing.
  Odd cluster sizes give the best fault tolerance per machine.

SPLIT-BRAIN PREVENTION
                    ┌─────────┐
  Partition →       │ 2 nodes │ ← no quorum, cannot elect leader
                    └─────────┘
                    ┌─────────┐
                    │ 3 nodes │ ← HAS quorum, elects leader, serves writes
                    └─────────┘
  Only one side can ever achieve majority → at most one leader.
```

For **Byzantine** faults (malicious / lying nodes), you need `3F + 1` nodes (e.g., PBFT, Tendermint). Most internal infra assumes crash faults, not Byzantine.

---

## 3. Raft — The Understandable Consensus Algorithm

Raft (Ongaro & Ousterhout, 2014) was explicitly designed to be easier to understand than Paxos. It's what powers **etcd, Consul, CockroachDB, TiKV, MongoDB (≥ 3.2), RabbitMQ Quorum Queues, Redis Raft**.

Raft decomposes consensus into three sub-problems:

1. **Leader Election** — pick one node to serialize all writes.
2. **Log Replication** — the leader appends entries to followers.
3. **Safety** — guarantee no two leaders commit conflicting entries.

### 3.1 Node States

```
RAFT: 3 STATES FOR EACH NODE

  ┌───────────┐  election    ┌───────────┐  wins majority   ┌──────────┐
  │ FOLLOWER  │──timeout────►│ CANDIDATE │─────────────────►│  LEADER  │
  └───────────┘              └───────────┘                  └──────────┘
       ▲                           │ ▲                             │
       │                           │ │ split vote → retry          │
       │ discovers                 │ └─ with new random timeout    │
       │ higher term               │                               │
       │                           ▼                               │
       └────────── loses ──── FOLLOWER ◄──── discovers new ────────┘
                  election                   leader/term
```

### 3.2 Leader Election (Step by Step, In Depth)

Before the steps, two concepts you must understand first:

**Term** — a monotonically increasing integer that acts as Raft's logical clock. Every election starts a new term. Terms let nodes distinguish old information from new: if you see a term higher than yours, your view is stale and you must update. There is **at most one leader per term**. Terms are persisted to disk so a restarted node never "forgets" what term it was on.

**Election timeout** — a per-follower countdown (typical range **150–300 ms**) that fires when the follower hasn't heard from a leader. The timeout is **randomized** within the range, freshly picked each time, so that if the leader dies, followers wake up at different times and one clearly wins.

#### STEP 1 — Normal operation: the heartbeat contract

```
Leader (term=1)  ──AppendEntries(empty)──►  Follower A   (every ~50ms)
                 ──AppendEntries(empty)──►  Follower B
                 ──AppendEntries(empty)──►  Follower C
```

- **Why heartbeats?** They're how the leader says "I'm alive; don't start an election."
- **Why AppendEntries (and not a dedicated Heartbeat RPC)?** Raft reuses the same RPC used for log replication — an empty AppendEntries is the heartbeat. This keeps the protocol minimal: if the leader has new entries, the heartbeat also replicates them.
- **Why ~50 ms?** Two timing rules the designer must respect:
  - `heartbeat_interval << election_timeout` — so a healthy leader resets followers' timers well before they fire. A common ratio is **5–10×** (50 ms heartbeat, 150–300 ms timeout).
  - `election_timeout << MTBF` — so the cluster spends negligible time in elections.
- **Every follower resets its election timer** each time it receives a valid AppendEntries. That's the entire liveness mechanism.

#### STEP 2 — Leader crashes → followers time out at different times

```
Leader ✗ DEAD  (crashed, GC pause, or network-isolated)

Follower A:  picked 210 ms timeout → fires at T+210
Follower B:  picked 170 ms timeout → fires FIRST at T+170    ← wins the race
Follower C:  picked 280 ms timeout → fires at T+280
```

- **Why randomize?** If every follower used the same 200 ms timeout, all three would become candidates simultaneously every election → split vote → retry → split vote → livelock. Randomization **staggers** them so the first-to-timeout gets a head-start on collecting votes before anyone else wakes up.
- **The math:** given a 150–300 ms range and 3 followers, the probability that two timeouts fire within one RTT (say, 20 ms) of each other is low; if they do, step 5's split-vote recovery handles it.
- **Does the leader know it's dead?** Sometimes yes (kernel panic), often no (network partition). The follower side decides based on *absence of heartbeats*, not the leader's self-awareness.

#### STEP 3 — First to time out becomes CANDIDATE

Follower B's timer fires. B transitions to CANDIDATE and, in one atomic burst:

```
1. currentTerm++              (was 1, now 2)   — persist to disk
2. votedFor = self            (casts its own vote for term 2) — persist
3. reset election timer       (new random value)
4. send RequestVote RPCs to ALL other peers in parallel
```

**The RequestVote RPC payload:**

| Field | Meaning |
|---|---|
| `term` | Candidate's current term (2). |
| `candidateId` | The candidate's node ID. |
| `lastLogIndex` | Index of candidate's last log entry. |
| `lastLogTerm` | Term of candidate's last log entry. |

The last two fields are **critical** — they're how a voter decides whether the candidate's log is "up-to-date enough" to be safely promoted (see Step 4). Without them, a node that missed recent writes could become leader and silently erase committed data.

**Why persist `currentTerm` and `votedFor` before sending the RPC?** If the candidate crashes mid-election and restarts, it must remember that it already voted in term 2 — otherwise it could vote a second time for a different candidate in the same term and two leaders could be elected.

#### STEP 4 — Voters apply the voting rules

Each receiver (A and C) runs this logic, in order:

```
on receiving RequestVote(term=T, lastLogIndex=LI, lastLogTerm=LT):

  1. if T < my currentTerm:
        → REJECT (candidate is stale)

  2. if T > my currentTerm:
        currentTerm = T
        votedFor    = null         ← fresh term, I haven't voted yet
        state       = FOLLOWER     ← if I was a candidate/leader, step down

  3. if votedFor is null OR votedFor == candidateId:
        if candidate's log is AT LEAST AS UP-TO-DATE as mine:
           votedFor = candidateId   ← persist to disk!
           reset election timer
           → GRANT vote
        else:
           → REJECT (log not current enough)
     else:
        → REJECT (already voted this term)
```

**The "at least as up-to-date" rule — precisely:**

Compare `(candidate.lastLogTerm, candidate.lastLogIndex)` with `(my.lastLogTerm, my.lastLogIndex)` using **lexicographic order**:

- If `candidate.lastLogTerm > my.lastLogTerm` → candidate is more up-to-date ✓
- If `candidate.lastLogTerm == my.lastLogTerm AND candidate.lastLogIndex >= my.lastLogIndex` → OK ✓
- Otherwise → REJECT.

Why this exact rule? It's what guarantees **Leader Completeness** (every committed entry survives leadership changes): an entry is only committed when a majority has it, and any future candidate needs a majority of votes — the intersection of those two majorities contains at least one node that has the entry and will refuse to vote for a candidate missing it.

**Worked outcome for our example:**

```
A evaluates RequestVote(term=2, lastLogIdx=..., lastLogTerm=...):
   2 > A.currentTerm (1)          → update term to 2, votedFor=null
   votedFor is null               ✓
   B's log ≥ A's log              ✓
   → GRANT vote, persist votedFor=B

C evaluates the same:             → GRANT vote
```

#### STEP 5 — Candidate B collects a majority → LEADER

```
B's tally (term=2):
  1 (itself) + 1 (A) + 1 (C) = 3 votes
  majority of 3 = 2 → ACHIEVED

B transitions: CANDIDATE → LEADER
  • Sends heartbeats IMMEDIATELY (don't wait for next tick,
    else some follower might time out and start term 3)
  • Initializes per-follower state: nextIndex[], matchIndex[]
  • Starts accepting client writes
```

The new leader also commits a **no-op entry** in its own term to quickly establish the current term's presence in its log (this is an optimization — it accelerates safe read-your-writes and ensures any previously-uncommitted entries from older terms can be committed transitively).

#### STEP 6 — What can go wrong, and how Raft handles it

**(a) Split vote.** A and C both time out at nearly the same instant and both become candidates in term 2:

```
A → term 2, votes for self, asks B and C for votes
C → term 2, votes for self, asks A and B for votes

A already voted for self in term 2 → REJECTS C
C already voted for self in term 2 → REJECTS A
B receives RequestVote from both — grants to whichever arrived first,
  rejects the other (votedFor is set).

Best case: B's vote + self vote = 2 of 3 → one wins.
Worst case: B is down/slow, nobody gets majority → election times out.
Both A and C pick NEW random timeouts and retry in term 3.
Randomness makes a second-round collision exponentially unlikely.
```

**(b) A legitimate leader still exists and a candidate is just impatient.**
If a candidate sends RequestVote to a node that just received a heartbeat from the current leader, the voter will still honor the rules: if the candidate's term is **equal** to the current term, the voter has likely already voted for the real leader and will reject. If the candidate's term is **greater**, the voter steps down (its view was stale) — the old leader will also step down on the next round-trip for the same reason. This is self-healing.

**(c) A partitioned follower keeps incrementing its term.**
Imagine a follower is network-isolated from the leader but can still contact one other follower. It times out → candidate → term++, term++, term++… When the partition heals, this node will have an absurdly high term. It sends RequestVote, and:
- Other nodes update their term but reject the vote (its log is behind → fails the up-to-date check).
- The current leader sees the high term, steps down, and the cluster runs a clean election in the new term.

This is sometimes called the **disruptive server** problem. Raft's **PreVote** extension (used in etcd, Consul) fixes it by adding a pre-election phase: a candidate first asks "would you vote for me?" without bumping its term; it only increments the term if a majority says yes.

**(d) Candidate receives AppendEntries from a leader of the same or higher term, mid-election.**
It means someone else is already leader. The candidate immediately steps down to FOLLOWER and accepts the entries.

**(e) Candidate wins but its heartbeat is delayed.**
Another follower times out and starts a new election in term 3. The new leader (term 2) sees a RequestVote with term 3, steps down. This is wasted work but safe — the cluster just elects again in term 3.

#### Quick mental model

> Raft elections are like a **three-way race with randomized starting guns**. The first to the line broadcasts its number (the term) and asks everyone to confirm. Voters have a simple rule: vote for the first candidate with a term at least as high as yours **and** a log at least as up-to-date as yours. Majority wins. Ties get a do-over with fresh random timers.

### 3.3 Log Replication

Each log entry has `{index, term, command}`. The leader is the single writer.

```
          Leader (L)            Follower F1            Follower F2
Client:     │                         │                     │
"SET x=5" ─►│  append to local log    │                     │
            │  index=7, term=2        │                     │
            │                         │                     │
            │──AppendEntries──────────►│                     │
            │   prevLogIndex=6         │                     │
            │   prevLogTerm=2          │                     │
            │   entries=[{7, SET x=5}] │                     │
            │──AppendEntries──────────────────────────────►│
            │                         │                     │
            │           Log consistency check passes        │
            │                         │                     │
            │◄──────────ACK───────────│                     │
            │◄───────────────────────ACK───────────────────│
            │                                               │
            │  Majority (2 of 3, incl. leader) ✓            │
            │  → COMMIT index 7, apply to state machine     │
            │                                               │
            │──Next AppendEntries (commitIndex=7)──────────►│
            │──Next AppendEntries (commitIndex=7)──────────►│
            │                                               │
            │  Followers apply committed entry to their     │
            │  state machines.                              │
            └──────Response to client: OK───────────────────►
```

#### 3.3.1 What if the leader dies MID-replication?

This is the most important edge case. Let's walk through the exact scenario:

> **Setup:** 3-node cluster. Leader L has appended `{index=7, term=2, SET x=5}` to its own log. Followers F1 and F2 are still at index 6. Then L crashes — possibly before sending AppendEntries at all, possibly after only F1 received it.

The outcome depends on **how far replication got** when L died. In every case: **no committed entry is ever lost, but uncommitted entries may or may not survive.**

```
TIMELINE

  before crash:       L has index 7 (uncommitted)
                      F1, F2 at index 6
  ─── L crashes ───
  after crash:        F1 and F2 elect a new leader among themselves
                      L eventually rejoins as FOLLOWER
```

**Sub-case A — L crashed BEFORE any AppendEntries was delivered**

```
  L: [1..6][7]           ← entry 7 exists only on L
  F1:[1..6]
  F2:[1..6]

1. L dies. F1/F2 time out and run an election.
2. New leader (say F1) has last index = 6. Its log is "up-to-date"
   as far as F1 and F2 know (they both match). Election succeeds.
3. F1 starts accepting new writes. Maybe the client retries and
   a new entry appears at index 7 with term=3:
     F1: [1..6][7@term=3]
     F2: [1..6][7@term=3]
4. L reboots, rejoins as a follower.
     L has:   [1..6][7@term=2]     ← its stale uncommitted entry
     F1 has:  [1..6][7@term=3]
5. F1 sends AppendEntries with prevLogIndex=6, prevLogTerm=2,
   entries=[{7, term=3}]. L's consistency check passes (index 6
   matches). L's entry at index 7 has term=2, but the new entry
   has term=3 — L OVERWRITES index 7 with the new entry.

Result: L's old index 7 is silently discarded. ✓ Safe, because
        it was never committed, never ACKed to any client.
```

**Sub-case B — AppendEntries reached F1, not F2, then L dies**

```
  L:  [1..6][7]   ← sent, received ACK from F1, no ACK yet from F2
  F1: [1..6][7]   ← got it
  F2: [1..6]      ← didn't get it

Entry 7 is on a MINORITY (L + F1 = 2 of 3). L was about to commit
(since 2/3 = majority) — but did it actually commit before dying?

Two micro-sub-cases:

  B1: L committed locally but crashed before telling anyone.
      L's commitIndex was advanced to 7 in memory, but nobody
      else knows, and L didn't ACK the client yet (or did it?).

  B2: L crashed right after F1's ACK, before updating commitIndex.
      No one has "committed" anything.

Either way, F1 has index 7 at term=2 and F2 doesn't.
Election: F1 and F2 can both become candidates.
  - If F1 wins (its log is more up-to-date than F2's, so F2 will
    vote for F1) → F1 becomes leader, still has index 7.
    F1 replicates index 7 to F2 → entry 7 is now on 2 of 3 → commits.
    ENTRY SURVIVES.
  - If F2 times out first and becomes candidate → F2 asks F1 for a vote.
    F1 sees F2's lastLogIndex=6 < F1's lastLogIndex=7 → REJECTS.
    F2 cannot win. Eventually F1 wins. Same result as above.

So: if at least one surviving node has the entry, it will win the
election (up-to-date rule) and the entry will be preserved.
```

**Sub-case C — both F1 and F2 received the entry, but L crashed before marking it committed** *(the famous "Figure 8" case)*

```
  L:  [1..6][7@term=2]   ← replicated to majority, but L crashed
  F1: [1..6][7@term=2]   ← before advancing commitIndex and
  F2: [1..6][7@term=2]   ← notifying followers.

Entry 7 is on ALL THREE nodes but formally uncommitted.
Is it safe to consider committed? Not quite — there's a subtle trap.

Raft's rule (Figure 8 of the paper):
  ┌─────────────────────────────────────────────────────────────────┐
  │  A leader may ONLY commit entries from its CURRENT TERM by      │
  │  counting replicas. Entries from previous terms are committed   │
  │  INDIRECTLY, by committing a later entry that follows them.     │
  └─────────────────────────────────────────────────────────────────┘

Why the restriction? Because a future scenario (leader churn +
log overwriting) could theoretically un-replicate a "committed"
entry from a prior term. The paper has a worked counter-example.

Practical consequence: when a new leader wins in term 3, it sees
index 7 (term=2) in its log. It will NOT immediately commit it by
quorum-counting. Instead, the leader appends a NO-OP entry at
index 8 in term 3. Once index 8 is replicated to a majority, the
log-matching property commits index 7 transitively.

That's why new leaders always start with a no-op commit in their
own term — it's the mechanism that safely promotes
previously-uncommitted-but-majority-replicated entries.

Result: ENTRY SURVIVES, and becomes committed once the no-op
        (or any later real write) commits in the new term.
```

**What does the client see in all these cases?**

The client only sees "OK" after the old leader commits AND responds. In every sub-case above, the leader died **before** that response was sent. So from the client's perspective:

- The request **timed out** (no ACK received).
- The client **does not know** whether the write took effect — it might have (sub-case C), might not (sub-case A), might be in limbo until the next election settles (sub-case B).

**This is why every write path to a Raft system must be idempotent.** The client retries with the **same request ID**; the new leader either sees a duplicate request ID (and returns the cached result) or treats it as fresh and executes once. Without idempotency keys, a naive retry can produce **duplicate writes** when sub-case C happened and the client assumed the request failed.

**Summary table:**

| Sub-case | Replication reached | After election, entry is… | Data loss? |
|---|---|---|---|
| A | 0 followers (only old leader) | Discarded when old leader rejoins as follower | Effectively "never happened" — client got no OK |
| B | 1 follower (minority, incl. leader) | Survives (up-to-date rule keeps it) and gets re-replicated | No — entry preserved even though never committed |
| C | Majority, but not yet committed | Survives; committed transitively via new leader's no-op | No — safely promoted by Figure-8 rule |
| Any of the above | Was **committed** before crash | **Guaranteed** to be in every future leader's log | **NEVER** — that's Leader Completeness |

**The bottom line:**

- **Committed entries are never lost.** That is Raft's strongest guarantee (Leader Completeness).
- **Uncommitted entries may be discarded** — but the client was never told they succeeded, so no safety invariant is violated.
- **Availability:** the cluster is unavailable for writes during the election (typically a few hundred ms), then resumes automatically.
- **Client responsibility:** always retry with idempotency keys, because "I didn't hear OK" is ambiguous.

#### 3.3.2 How Raft's Design Actually Enforces This

The outcomes in 3.3.1 aren't hand-wavy — they are the *logical consequence* of five concrete design decisions in the Raft protocol. Here's the machinery that guarantees them.

##### (1) Persistent state vs. volatile state

Raft draws a hard line between what MUST be written to stable storage *before* responding to an RPC, and what can live in memory.

| State | Persistent? | Purpose |
|---|---|---|
| `currentTerm` | **Yes** (fsync) | Prevents double-voting after crash-restart |
| `votedFor` | **Yes** (fsync) | Same — a node must not vote twice in one term |
| `log[]` (entries) | **Yes** (fsync) | A node that ACKs an entry and crashes must still have it on reboot |
| `commitIndex` | No (volatile) | Can be recomputed from the leader's signal |
| `lastApplied` | No (volatile) | Can be replayed from the log |
| `nextIndex[]`, `matchIndex[]` (leader only) | No (volatile) | Rebuilt at the start of each leader's term |

**Why this matters for the crash scenario:**

- If follower F1 ACKs index 7 then crashes, on reboot it **still has entry 7** in its log — it didn't "forget." This is what preserves sub-case B.
- If the leader persists an entry *but* crashes before replicating, the entry is still in L's log on reboot — but L is no longer the leader, so when it rejoins it will be **truncated** by the new leader's AppendEntries. That's sub-case A.
- If candidate C votes for someone and crashes before the election completes, on reboot C remembers `votedFor=X` and refuses to vote for anyone else in that term. Without fsync'ing `votedFor`, you could elect two leaders in the same term.

**The fsync cost is real.** High-performance Raft implementations (etcd, CockroachDB) batch multiple entries into a single fsync to amortize the cost, but they never skip it.

##### (2) The AppendEntries consistency check (enforces Log Matching)

Every AppendEntries RPC carries **`prevLogIndex` + `prevLogTerm`** — the index/term of the entry *immediately before* the new entries being sent. The follower rejects the RPC unless its local log has that exact (index, term) pair.

```
Leader sends:  AppendEntries(prevLogIndex=6, prevLogTerm=2, entries=[{7, ...}])

Follower checks:
  if my log[6].term == 2:
      ACCEPT → append entries
  else:
      REJECT → send failure response with info to help leader back up
```

This is an **inductive invariant**: if the check succeeds for every entry appended, then by induction the follower's log is byte-identical to the leader's up to the new entries. That's the **Log Matching Property** — the reason you can compare two logs with just `(lastIndex, lastTerm)` instead of a full diff.

##### (3) `nextIndex[]` and the "walk backwards" recovery

A new leader has no idea what each follower's log contains (that volatile state was wiped). It guesses:

```
On becoming leader:
  for each follower i:
      nextIndex[i]  = leader.lastLogIndex + 1        // optimistic
      matchIndex[i] = 0                              // conservative
```

The leader sends `AppendEntries(prevLogIndex = nextIndex[i] - 1, prevLogTerm = log[nextIndex[i]-1].term, entries = log[nextIndex[i]..])`.

If the consistency check fails, the follower returns **failure** and the leader decrements `nextIndex[i]` and retries:

```
  while follower rejects:
      nextIndex[i]--
      resend AppendEntries with the new prevLogIndex/prevLogTerm
  once accepted:
      overwrite any conflicting entries on the follower
      append the rest of the leader's log
```

This is exactly how the **old leader's stale index 7** gets erased in sub-case A. The old leader rejoins as a follower. The new leader's AppendEntries has `{index=7, term=3}`. The old leader's log at index 7 has `term=2`. Per the rules, **the follower overwrites its conflicting tail** (the old term=2 entry) with the leader's authoritative term=3 entry.

> **Key rule (Raft paper §5.3):** "If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all that follow it."

Note that the leader never overwrites *its own* log (the **Leader Append-Only** property). Only *followers* truncate — and only when directed by a leader with a higher or equal term.

##### (4) The commit rule (the Figure-8 fix)

This is the most subtle and most important rule in Raft. State verbatim from the paper:

> **A leader may only commit an entry from its current term by counting replicas. Entries from previous terms are committed only indirectly, once an entry from the current term has been committed.**

**Why?** Imagine a leader L1 in term 2 replicates entry E to a majority but crashes before committing. L2 is elected in term 3. L2 sees E on a majority and is tempted to "finish the commit" by counting replicas. But there's a known counter-example (Figure 8 of the paper) where doing so leads to a committed entry later being overwritten — violating Leader Completeness.

**The fix** is to require the new leader to establish its *own* term's presence first. In practice, this is implemented as a **no-op entry** the leader appends at election time:

```
On becoming leader in term T:
  append NO_OP{index = lastLogIndex+1, term = T} to my log
  replicate it normally
  once the no-op commits (majority ACK in term T):
      → all prior-term entries in my log are now safely committed
      → start accepting client writes
```

This is the mechanism that makes sub-case C safe. The new leader inherits index 7 (term=2), commits a no-op at index 8 (term=3), and once that no-op reaches a majority, index 7 commits transitively via Log Matching.

##### (5) The "up-to-date" vote rule (enforces Leader Completeness)

Recap from 3.2: a voter only grants its vote if the candidate's `(lastLogTerm, lastLogIndex)` is **lexicographically ≥** its own.

**Why this rule, and why this specific comparison?** It's a quorum-intersection argument:

- An entry is committed only when it's on a **majority** of nodes.
- A candidate needs a **majority** of votes to win.
- Any two majorities in a cluster of `N` nodes must share **at least one node**.
- That overlapping node has the committed entry and will refuse to vote for any candidate whose log is less up-to-date than its own.
- Therefore the winning candidate must have every committed entry — **Leader Completeness**.

This is what saves sub-case B. Even though index 7 is on a minority, the node that has it (F1) will not vote for F2 (which doesn't). F1 becomes leader; the entry survives.

##### (6) Putting it all together — the crash scenario, mechanism by mechanism

Let's trace sub-case B again (L has index 7 committed locally; F1 has it; F2 doesn't; L crashes) but now name each rule as it fires:

```
1. L and F1 fsync'd entry 7 to disk before acknowledging.
   ↑ Rule (1) persistence — entry isn't lost on crash.

2. L crashes. F1 and F2 both time out, run an election in term 3.

3. F2 becomes candidate first, sends RequestVote to F1.
   F1 checks: F2.lastLogIndex = 6 < F1.lastLogIndex = 7  → REJECT.
   ↑ Rule (5) up-to-date — preserves the entry.

4. F2's election fails. F1 eventually becomes candidate.
   F1 sends RequestVote(term=3, lastLogIndex=7, lastLogTerm=2) to F2.
   F2 checks: F1.lastLogIndex ≥ F2.lastLogIndex → GRANT.
   F1 wins (self + F2 = 2 of 3).

5. F1 appends NO_OP{index=8, term=3} and replicates.
   ↑ Rule (4) no-op commit — promotes index 7.

6. F1 sends AppendEntries(prevIdx=7, prevTerm=2, entries=[{8, noop}]) to F2.
   F2's log[7]? It doesn't have index 7 → consistency check FAILS.
   F2 returns failure.
   ↑ Rule (2) consistency check — catches the gap.

7. F1 decrements nextIndex[F2] to 7, resends with
   AppendEntries(prevIdx=6, prevTerm=?, entries=[{7, term=2}, {8, noop}]).
   F2's log[6] matches → accepts, appends both entries.
   ↑ Rule (3) walk-backwards — repairs the follower.

8. Now index 7 is on F1 and F2 (majority in term 3 via no-op).
   Index 8 (no-op) commits → index 7 committed transitively.
   ↑ Rule (4) again — the commit finally becomes durable.

9. L reboots, rejoins as follower. Its log has {index=7, term=2}
   and F1's has {index=7, term=2} — they MATCH. No truncation.
   If L had a divergent uncommitted tail, it would be truncated by
   Rule (3) overwrite-on-conflict.
```

Every step is governed by a specific rule. There's nothing hand-wavy — the protocol is a closed system where these invariants compose to produce the safety guarantees listed in §3.4.

##### (7) The invariants Raft maintains at all times

| # | Invariant | Enforced by |
|---|---|---|
| I1 | At most one leader per term | Majority vote + `votedFor` persistence (Rule 1 & 5) |
| I2 | Leader never rewrites its own log | Leader Append-Only property |
| I3 | Identical logs up to any matching `(index, term)` | AppendEntries consistency check (Rule 2) |
| I4 | Committed entries survive all future leader changes | Up-to-date vote rule (Rule 5) |
| I5 | State machines apply the same sequence of commands | Commit rule (Rule 4) + Log Matching |

If *all five* invariants hold at every step of every RPC, the system is linearizable. The Raft paper (and its TLA+ spec) proves that the rules above are sufficient to maintain all five.

##### (8) What Raft explicitly does NOT do (and why that's fine)

- **Raft does not try to keep uncommitted entries.** If a minority has an entry and the leader dies, the entry may or may not survive — but it was never committed, so no invariant is at stake.
- **Raft does not guarantee exactly-once client execution.** That's the client's job via idempotency keys. Raft guarantees the *replicated log* is consistent; how the app interprets retries is outside its contract.
- **Raft does not do Byzantine-fault tolerance.** It assumes nodes may crash or be slow but not lie. If nodes can lie, you need PBFT/Tendermint with `3F+1` nodes.
- **Raft does not minimize latency.** Every write requires a majority round-trip. Techniques like leader leases, follower reads, and batching reduce average-case latency but don't change the fundamental quorum requirement.

### 3.4 Safety Properties (the important ones)

| Property | What it guarantees |
|---|---|
| **Election Safety** | At most one leader per term. |
| **Leader Append-Only** | A leader never overwrites or deletes its own log entries. |
| **Log Matching** | If two logs have an entry with same index+term, all preceding entries are identical. |
| **Leader Completeness** | If an entry is committed in term T, it is present in every leader of term > T. |
| **State Machine Safety** | If a node applies an entry at index i, no other node applies a different entry at i. |

**The "up-to-date" rule** is the key to Leader Completeness: a candidate must have all committed entries, so voters only vote for candidates whose last `(term, index)` is ≥ their own.

### 3.5 Handling Tricky Cases

- **Log divergence** after a leader change: new leader forces followers to overwrite non-committed tail by walking `nextIndex` backwards until logs match.
- **Network partition of leader**: old leader in minority partition can't commit (no majority), a new leader is elected in the majority partition; when the old one rejoins, it sees a higher term and steps down.
- **Read-only queries**: either route through the leader (strong consistency) or allow follower reads with `ReadIndex` / leases (bounded staleness).

---

## 4. Paxos — The OG Consensus Algorithm

Paxos (Lamport, 1998) is theoretically seminal and still used in production (Google Chubby, Spanner, Megastore). It's famous for being "hard to understand" — in practice every production system uses **Multi-Paxos**, which is structurally similar to Raft.

### 4.1 Roles

- **Proposer** — suggests a value.
- **Acceptor** — votes on proposals; stores accepted values.
- **Learner** — learns the chosen value.

A single physical node usually plays multiple roles.

### 4.2 Basic Paxos — Two Phases

```
PHASE 1 — PREPARE (get a promise)
  Proposer picks proposal number N (monotonically increasing).
  Proposer ─► Acceptors : PREPARE(N)

  Acceptor: if N > any promised N' → promise not to accept < N
                                    and return (highest accepted value, if any)

PHASE 2 — ACCEPT (propose the value)
  If Proposer got promises from a MAJORITY:
    value V = any value returned with highest accepted N (else free to pick)
    Proposer ─► Acceptors : ACCEPT(N, V)

  Acceptor: if it hasn't promised a higher N' → accept (N, V)

  If majority accepts → V is CHOSEN.
  Learners are informed.
```

### 4.3 Multi-Paxos (what Chubby/Spanner actually use)

Running Basic Paxos per entry is expensive (2 round-trips). **Multi-Paxos** elects a **distinguished proposer** (effectively a leader) who skips Phase 1 for subsequent entries and just runs Phase 2 per log entry — one round-trip per write, same as Raft.

```
Basic Paxos:  PREPARE → ACCEPT            per value (2 RTTs)
Multi-Paxos:  PREPARE once (per leader)   then ACCEPT, ACCEPT, ACCEPT...
Raft:          Leader election            then AppendEntries, ...
```

### 4.4 Raft vs Paxos (practical differences)

| Aspect | Paxos / Multi-Paxos | Raft |
|---|---|---|
| Specification | Abstract, flexible | Prescriptive, one way to do it |
| Leader | "Distinguished proposer" (optional) | **Mandatory**, central to design |
| Log | Entries can commit out of order | Log is contiguous, commit in order |
| Understandability | Notoriously hard | Designed for teachability |
| Proven safety | Yes (TLA+ spec) | Yes (TLA+ spec, simpler) |
| Used by | Google Chubby, Spanner, Cassandra LWT, Megastore | etcd, Consul, CockroachDB, TiKV, MongoDB |

Rule of thumb for interviews: **"Both give you linearizable replicated state machines. Raft is what you'd build today unless you're Google."**

---

## 5. ZAB — ZooKeeper Atomic Broadcast

ZooKeeper doesn't use Paxos or Raft. It uses **ZAB** (Zookeeper Atomic Broadcast), which is similar in spirit to Multi-Paxos but optimized for **primary-backup** replication of a state machine.

```
ZAB PHASES

  1. LEADER ELECTION   — Fast Leader Election picks a prospective leader
                         (the one with the most recent history).

  2. DISCOVERY          — Leader gathers followers' latest epoch + history.

  3. SYNCHRONIZATION   — Leader brings followers up to its log
                         (SNAP / DIFF / TRUNC).

  4. BROADCAST          — Normal operation: 2-phase commit per transaction
                         PROPOSAL → ACK (from quorum) → COMMIT
```

Key differences vs Raft:

- ZAB uses **epochs** (like Raft terms) but enforces strict FIFO order of proposals per leader.
- Followers that fall behind receive a **snapshot + tail of log**, not just incremental entries.
- Writes are **totally ordered** and delivered in `zxid` order — every client sees the same sequence.

---

## 6. Leader Election Mechanisms (Beyond Raft/Paxos)

Not every system needs full consensus. Often you just need **"one node does this job at a time."** AWS calls out [leases as the most widely used mechanism at Amazon](https://aws.amazon.com/builders-library/leader-election-in-distributed-systems/).

### 6.1 Lease-Based Election (AWS-style)

```
         ┌───────────────────────────────┐
         │  Coordination Store (DynamoDB,│
         │  etcd, ZK, Redis, Consul)     │
         │                                │
         │  key: "job-worker"             │
         │  holder: "node-A"              │
         │  expiresAt: T + 30s            │
         └───────────────────────────────┘
              ▲                    ▲
   CAS renew  │                    │ try-acquire if expired
              │                    │
     ┌──────────┐              ┌──────────┐
     │ Leader A │              │  Candidate B │
     │ heartbeat│              │              │
     └──────────┘              └──────────┘

RULES (from AWS Builders' Library)
  • Leader must check remaining lease time BEFORE every side-effecting op.
  • Don't heartbeat in a background thread with no coupling to the work thread
    (a stuck worker + happy heartbeater = lease held but no progress).
  • Assume slow networks, GC pauses, retries — the lease can expire any moment.
  • Use LOCAL elapsed time, never wall-clock (clock skew will bite you).
```

Pros: simple, works on any key/value store with CAS + TTL.
Cons: hard to implement truly correctly (the paper above details the subtleties).

### 6.2 Bully Algorithm (textbook)

Highest-ID node wins. On failure detection, any node broadcasts an ELECTION; higher-ID nodes respond and take over; the winner announces VICTORY. Simple, assumes reliable IDs, O(N²) messages, rarely used in practice.

### 6.3 Ring Algorithm (textbook)

Nodes arranged in a logical ring. Election token circulates, collects IDs, highest wins. Also mostly academic.

### 6.4 Sharded Leadership (the real-world trick)

A single leader is a **scalability bottleneck and a single point of failure**. The cure is sharding: each data shard has its own Raft/Paxos group with its own leader. That's exactly how **DynamoDB, Spanner, CockroachDB, EBS, Kafka KRaft, Vitess** scale.

```
  Shard 1: leader = node-A  (Raft group A,B,C)
  Shard 2: leader = node-B  (Raft group B,C,D)
  Shard 3: leader = node-C  (Raft group C,D,A)
  ...
  Failure of any single node only affects a subset of shards,
  and leadership for those shards fails over to another replica.
```

---

## 7. Consensus & Coordination Tools — Compared

These are the off-the-shelf systems you'll actually use. Don't build your own.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                  COORDINATION SERVICE COMPARISON                            │
├────────────┬──────────┬────────────┬───────────────┬───────────────────────┤
│ System     │ Consensus│ API Style  │ Sweet Spot    │ Known Users            │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ ZooKeeper  │ ZAB      │ Hierarchical│ Heavy reads,  │ Kafka (classic),       │
│            │          │ znodes +    │ watches,       │ HBase, Hadoop, Solr   │
│            │          │ watches     │ Java shops     │                        │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ etcd       │ Raft     │ gRPC KV +   │ Cloud-native,  │ Kubernetes, CoreOS,    │
│            │          │ leases,     │ linearizable   │ Rook, M3             │
│            │          │ watches     │ KV store       │                        │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ Consul     │ Raft     │ HTTP KV +   │ Service disc., │ HashiCorp stack,      │
│            │          │ health,     │ multi-DC,      │ Nomad, Vault          │
│            │          │ DNS         │ health checks  │                        │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ Chubby     │ Multi-   │ Lock +      │ Coarse locks,  │ Google (GFS, Bigtable, │
│ (Google)   │ Paxos    │ small-file  │ name service   │ MapReduce)            │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ Spanner    │ Multi-   │ SQL + TT    │ Globally       │ Google Ads, Gmail     │
│            │ Paxos +  │ timestamps  │ consistent     │                        │
│            │ TrueTime │             │ SQL            │                        │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ Kafka      │ KRaft    │ Internal    │ Kafka metadata │ Modern Kafka clusters │
│ Controller │ (Raft)   │             │ (replaces ZK)  │                        │
├────────────┼──────────┼────────────┼───────────────┼───────────────────────┤
│ DynamoDB   │ Lease-   │ Conditional │ General-purpose│ Any AWS workload      │
│ locks      │ based    │ writes      │ lease election │                        │
└────────────┴──────────┴────────────┴───────────────┴───────────────────────┘
```

### ZooKeeper — how it works internally

```
CLIENT MODEL
  • Hierarchical namespace: /services/search/node-0001
  • znodes: persistent | ephemeral | sequential
  • WATCHES: one-shot notifications on changes
  • SESSIONS: tied to TCP connection + session timeout

EPHEMERAL + SEQUENTIAL = LEADER ELECTION RECIPE
  1. Every candidate creates /election/leader-{seq}  (ephemeral+sequential)
  2. ZK assigns sequence numbers atomically: 0001, 0002, 0003...
  3. The node with the LOWEST number is the leader.
  4. Each other node WATCHES only the znode immediately smaller than itself
     (avoids herd effect).
  5. When the leader's session ends, its ephemeral znode disappears
     → the next-lowest node gets a watch event → becomes leader.

INTERNAL ARCHITECTURE
  Clients → Leader (all writes) ─ZAB broadcast─► Followers ─► Quorum ACK ─► commit
         ↘ Followers (reads, watches)
  Observers: non-voting members for read scalability.
```

### etcd — how it works internally

```
  • Written in Go, gRPC API, uses Raft (from the excellent `go.etcd.io/raft` library).
  • MVCC KV store with revision numbers (every write → new revision).
  • LEASES: attach a TTL to keys; heartbeats keep them alive → election primitive.
  • WATCHES: stream of KV events since a revision (reliable, server-side filtered).
  • LINEARIZABLE reads via ReadIndex (route through leader) or serializable reads
    from any follower for lower latency.
  • Used as Kubernetes' source of truth — every K8s object is an etcd key.
```

etcd leader election primitive (`clientv3/concurrency.Election`):
1. Create a key `/election/<prefix>/<lease-id>` with a lease.
2. The key with the smallest CreateRevision is the leader.
3. Others watch the key immediately preceding theirs.
4. Leader keeps lease alive → on failure the key disappears → next node is promoted.

### Chubby (Google) — inner workings

Chubby is less a "raw" consensus service and more a **coarse-grained distributed lock + tiny file service** built on Multi-Paxos. Lessons Google published from building it shaped ZooKeeper's design.

- Typical cell: 5 replicas, one elected master via Paxos.
- Clients acquire **advisory locks** on small files (a few KB).
- **Sessions + leases + KeepAlives** — same pattern AWS recommends today.
- Sequencers: the master can hand out lock sequencers clients attach to RPCs so downstream services can verify the caller really holds the lock (defends against stale leaders).
- Lessons: most applications use Chubby not because they need distributed agreement, but because they need a **highly available, consistent name service / config store**.

### Spanner — Paxos + TrueTime

Spanner shards the keyspace; each shard ("tablet") is replicated via **Multi-Paxos**. The twist is **TrueTime** — GPS+atomic-clock-backed API that returns a bounded interval `[earliest, latest]` rather than a timestamp. Spanner waits out the uncertainty (`commit-wait`) before acknowledging writes, giving globally-consistent external consistency (linearizability) across continents.

### Kafka KRaft — Raft inside Kafka

Modern Kafka (≥ 3.3) replaces its ZooKeeper dependency with **KRaft**: the controllers form a Raft quorum that stores cluster metadata (topics, partitions, ACLs, leadership). Benefits: one fewer system to operate, faster metadata operations, and scale to millions of partitions.

---

## 8. What Coordination Services Are Actually Used For

```
┌─────────────────────────────────────────────────────────────────┐
│          COMMON USE CASES (ZK / etcd / Consul / Chubby)          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. LEADER ELECTION                                              │
│     Create ephemeral "leader" key → first wins                   │
│     Watchers promoted when leader's session dies                 │
│                                                                  │
│  2. DISTRIBUTED CONFIGURATION                                    │
│     Store config as KV, clients watch for updates               │
│     Config change → all services notified in <1s                 │
│                                                                  │
│  3. SERVICE DISCOVERY                                            │
│     Services register ephemeral nodes on start                   │
│     Clients list nodes + watch for membership changes            │
│                                                                  │
│  4. DISTRIBUTED LOCKING                                          │
│     Lease-backed key = lock. Lock released when lease expires    │
│     or owner deletes the key. Use fencing tokens to defend       │
│     against stale lock holders.                                  │
│                                                                  │
│  5. GROUP MEMBERSHIP                                             │
│     Track live members (heartbeats + ephemeral nodes)            │
│                                                                  │
│  6. BARRIERS / QUEUES / RATE LIMITERS                            │
│     Coordinate phases of distributed computations                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Fencing tokens — the must-know pitfall

```
NAIVE LOCK (BROKEN):
  Client A acquires lock, pauses (GC), lease expires.
  Client B acquires lock.
  Client A wakes up, still "thinks" it holds the lock, writes to storage.
  → DATA CORRUPTION.

WITH FENCING TOKEN:
  Lock service hands out monotonically increasing tokens:
    A gets token 42
    B gets token 43
  Storage tracks highest token it has seen.
  When A writes with token 42, storage rejects it (already seen 43).
```

---

## 9. Production Best Practices (Distilled)

These echo Amazon's guidance in the [AWS Builders' Library article on leader election](https://aws.amazon.com/builders-library/leader-election-in-distributed-systems/).

1. **Prefer idempotency over single-leader correctness.** If two leaders briefly coexist and the work is idempotent, nothing breaks.
2. **Check lease validity before every side effect** — not just at task start.
3. **Couple leadership and work in the same thread**; don't heartbeat in the background while work hangs.
4. **Use local elapsed time**, never wall clock. Beware GC pauses and clock jumps.
5. **Make the current leader discoverable** (expose `/status/leader`) and keep an audit trail of leadership transitions.
6. **Use fencing tokens** when the "work" involves writing to external storage.
7. **Pick odd cluster sizes** (3, 5, 7). 5 is the sweet spot for most production etcd/ZK/Consul deployments.
8. **Don't write your own**. Use etcd/ZK/Consul or a managed equivalent.
9. **Capacity-plan consensus**. A single Raft group maxes out at a few thousand writes/sec — shard early if you need more.
10. **Formally verify tricky protocols** (TLA+) — bugs here are subtle and production failures are catastrophic.

---

## 10. Interview Cheat Sheet

| Question | Answer |
|---|---|
| How many nodes to tolerate F failures? | `2F + 1` (crash) or `3F + 1` (Byzantine). |
| Why is Raft easier than Paxos? | Prescriptive leader, single log, strong log matching. |
| What prevents split-brain in Raft? | Majority quorum + monotonically increasing terms. |
| How does a new leader catch followers up? | Walks `nextIndex` backwards until logs match, then overwrites tail. |
| Does etcd or ZK use Raft? | etcd uses Raft; ZK uses ZAB. |
| What replaced ZK in Kafka? | KRaft (Raft-based controller quorum). |
| When would you avoid consensus? | When idempotency + optimistic locking is enough, or when latency matters more than strict agreement. |
| When to shard a consensus group? | As soon as write throughput or data size approaches a single group's ceiling. |
| Lease-based locks — gotcha? | GC pause / slow network can make you lose the lease silently → use fencing tokens. |

---

## References

- [Leader Election in Distributed Systems — AWS Builders' Library](https://aws.amazon.com/builders-library/leader-election-in-distributed-systems/)
- [In Search of an Understandable Consensus Algorithm (Raft) — Ongaro & Ousterhout](https://raft.github.io/raft.pdf)
- [Paxos Made Simple — Leslie Lamport](https://lamport.azurewebsites.net/pubs/paxos-simple.pdf)
- [ZooKeeper: Wait-free Coordination for Internet-scale Systems](https://www.usenix.org/legacy/event/atc10/tech/full_papers/Hunt.pdf)
- [The Chubby Lock Service for Loosely-Coupled Distributed Systems — Burrows](https://research.google/pubs/pub27897/)
- [etcd documentation](https://etcd.io/docs/)
- [How to do distributed locking — Martin Kleppmann](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
