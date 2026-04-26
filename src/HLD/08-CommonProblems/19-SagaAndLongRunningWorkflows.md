# Sagas and Long-Running Workflows

> **TL;DR.** Business processes that span multiple services (book flight → charge card → issue ticket → send email) cannot run inside a single ACID transaction. The pattern is a **saga**: a sequence of local transactions linked by events, each with a **compensating action** for rollback. Two orchestration styles: **orchestration** (a central workflow engine drives the steps) and **choreography** (services react to each other's events). Production-grade sagas need **durable execution** (Temporal, Cadence, AWS Step Functions), **idempotent steps**, **versioning for running instances**, and **explicit failure/timeout handling** — anything less and your "saga" is a broken state machine that corrupts data.

---

## 1. Why you can't use a single transaction

A reservation flow:

```
 ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
 │  Flight │   │ Payment │   │ Ticket  │   │  Email  │
 │ Service │   │ Service │   │ Service │   │ Service │
 └────┬────┘   └────┬────┘   └────┬────┘   └────┬────┘
      │             │              │              │
  seats−1      charge $500       issue PNR     send mail

    4 different DBs, 4 different transaction contexts.
    No cross-service ACID transaction without 2PC.
    2PC blocks if any participant dies, and few systems support it.
```

If step 3 fails, you've charged the card (step 2) but the customer has no ticket. You must **compensate** — refund the card, release the seat. Correctly. Under retries. Across process restarts. Possibly for hours or days.

That is what a saga is.

---

## 2. Saga definition

A **saga** is a sequence `T₁, T₂, ..., Tₙ` of local transactions, with a matching compensating action `C₁, C₂, ..., Cₙ` such that:

- Either all `Tᵢ` succeed (happy path).
- Or some `Tₖ` fails, and we invoke `Cₖ₋₁, Cₖ₋₂, ..., C₁` (in reverse) to undo.

```
   T₁ → T₂ → T₃ → T₄ → ✓
              │
           (T₃ fails)
              │
              ▼
     C₂ ← C₁ (executed in reverse order)
```

**Key property:** each `Tᵢ` commits **locally**. There is no global "rollback" — just forward-only compensations.

---

## 3. Orchestration vs Choreography

### 3.1 Orchestration — the central brain

```
┌──────────────────────────────────────────────────────────────┐
│                       ORCHESTRATION                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                 ┌──────────────────┐                         │
│                 │    Orchestrator  │   ←── durable state     │
│                 │  (workflow engine)│                        │
│                 └──────────────────┘                         │
│                    │   │   │   │                             │
│        call flight │   │   │   │ call email                  │
│              ┌─────┘   │   │   └───────┐                     │
│              ▼         ▼   ▼           ▼                     │
│          Flight    Payment Ticket    Email                   │
│          Service   Service  Service  Service                 │
│                                                              │
│  Orchestrator holds the workflow definition. It calls each  │
│  service in order, handles failures, drives compensation.   │
└──────────────────────────────────────────────────────────────┘
```

**Pros:**
- Central place to see the workflow.
- Easy to reason about state.
- Retries, timeouts, compensation are explicit.
- Observability is free (the engine has all the state).

**Cons:**
- The engine becomes critical infrastructure.
- Services must be callable (API or task queue), not just event-driven.
- Coupling to the engine.

**Tools:** Temporal, Cadence, AWS Step Functions, Netflix Conductor, Camunda, Airflow (for data workflows), Celery + Celery Beat (lightweight).

### 3.2 Choreography — event-driven, no central brain

```
┌──────────────────────────────────────────────────────────────┐
│                       CHOREOGRAPHY                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Flight ── FlightBooked ──► Payment                         │
│                                 │                            │
│                                 ├── PaymentCaptured ──► Ticket│
│                                 │                        │   │
│                                 │                        ▼   │
│                                 │                     TicketIssued
│                                 │                        │   │
│                                 │                        ▼   │
│                                 │                    Email    │
│                                                              │
│   Each service subscribes to events and emits new ones.     │
│   No central orchestrator.                                  │
└──────────────────────────────────────────────────────────────┘
```

**Pros:**
- Loose coupling; services independent.
- No single point of failure in the middle.
- Natural fit for event-driven architectures.

**Cons:**
- **Workflow is invisible** — it lives in the edges between services.
- Hard to answer "what step are we on?" without a correlation ID and reconstruction.
- Cyclic dependencies can creep in.
- Compensation is tricky — who emits the "please compensate" event?

**When to use choreography:** <5 services involved, simple linear flows, strong event-sourcing culture. Beyond that, the invisibility starts hurting.

### 3.3 Picking between them

```
                 Steps ≤ 3?       Steps ≥ 4?
                 ───────────      ───────────
  Simple linear: Choreography OK   Orchestration
  Branching:     Orchestration     Orchestration (no question)
  Human steps:   Orchestration     Orchestration (no question)
  Long runtime:  Orchestration     Orchestration
```

**Default to orchestration.** The saying: "choreography is what you get when you refuse to admit you have a workflow."

---

## 4. Compensations — the subtle half

A compensating action is **not** a database rollback. It is a *new* local transaction that undoes the effect of the original. It must be:

### 4.1 Idempotent

`C_payment(charge_id)` may be called multiple times. It must refund exactly once.

### 4.2 Commutative (where possible)

If compensations can arrive out of order, they must still produce the right result.

### 4.3 Semantic, not physical

Refunds are not "undo". The customer may now have the refund *and* a notification about the refund. You can't hide that the thing happened.

### 4.4 Non-compensable actions

Some actions can't be undone:

- Sending an email.
- Making an API call that charges a third party for a one-shot action.
- Publishing a public event.

Strategies:
- **Backward recovery** (compensation) — only if undoable.
- **Forward recovery** — fix the error by retrying or an alternate path.
- **Pivot transaction** — a point in the saga where compensation stops being possible; past this point, you must go forward.

Example: once you've issued a plane ticket to a regulator, you can't un-issue it. Your compensation becomes "send a cancellation request" which is itself a new workflow.

---

## 5. Durable execution — why you need a workflow engine

Naïve saga implementation:

```python
def book_trip(user_id):
    flight = flight_service.reserve(user_id)
    try:
        payment = payment_service.charge(user_id, 500)
    except Exception:
        flight_service.release(flight.id)
        raise
    try:
        ticket = ticket_service.issue(user_id, flight.id)
    except Exception:
        payment_service.refund(payment.id)
        flight_service.release(flight.id)
        raise
    email_service.send(user_id, ticket.id)
```

Problems (every one of them is a production incident waiting to happen):

1. **Process crash between steps → inconsistent state** (paid, no ticket).
2. **Nested try/except explodes** with each new step.
3. **Retries not idempotent** — may double-charge.
4. **No visibility** — you can't answer "where is trip 42?"
5. **Long waits** (3-day SLA for human approval) freeze a thread.
6. **Deploy during an in-flight workflow** — state is lost.

A **durable workflow engine** solves all of these:

```python
@workflow.defn
class BookTrip:
    async def run(self, user_id):
        flight = await reserve_flight(user_id)           # durable
        try:
            payment = await charge(user_id, 500)         # durable
            ticket = await issue_ticket(user_id, flight) # durable
            await send_email(user_id, ticket)            # durable
        except Exception:
            # Each `await` above is a durable checkpoint.
            # Engine knows which succeeded; runs matching compensations.
            await compensate(flight, payment)
            raise
```

The engine persists state after every step. If the process crashes, another worker picks up exactly where it left off. The step functions are plain code. The retries, timeouts, compensation, persistence are the engine's job.

**Temporal / Cadence** pioneered this; AWS Step Functions, Netflix Conductor, and others are conceptual siblings.

---

## 6. Durable execution semantics — the key primitives

| Primitive | Guarantee |
|-----------|-----------|
| **Activity** | A single unit of work (an API call). Exactly-once *effect* (via idempotency key) even across retries. |
| **Workflow state** | Persisted between activity calls. Survives crashes. |
| **Timers** | Can sleep for days. Durable; process can die and timer still fires. |
| **Signals / queries** | External events / reads into a live workflow. |
| **Retry policies** | Per-activity: backoff, jitter, max attempts, non-retryable errors. |
| **Heartbeats** | Long activities report progress; engine detects frozen workers. |
| **Versioning** | In-flight workflows keep using old code paths; new ones use new. |

### 6.1 Idempotency keys for activities

Every external call needs an idempotency key derived from the workflow ID + activity sequence number. The server-side deduplicates. See [`06-IdempotencyAndDeduplication.md`](06-IdempotencyAndDeduplication.md).

### 6.2 Heartbeats + timeouts

```
schedule → start-to-close timeout (activity has N min to finish)
         → heartbeat timeout      (activity must report every M sec)
         → schedule-to-start      (workers must pick it up within K sec)
```

Without heartbeats, a stuck worker blocks the workflow indefinitely.

---

## 7. Versioning — the silent killer

Your workflow `BookTrip v1` is running for user 42. Halfway through, you deploy `BookTrip v2` which adds a new step. What happens?

- **Wrong answer:** the workflow takes the new step on its next await. Old in-flight workflows suddenly see a step that didn't exist when they started. Inconsistent behaviour; some customers double-charged.
- **Right answer:** the engine records the version at workflow start. V1 instances continue on V1 code paths forever; V2 instances take V2 path. You gate the new logic on a version check.

```python
if workflow.version("add_tax_step") >= 2:
    await add_tax()
```

This is messy but essential. Badly versioned workflows are the #1 reason saga implementations drift into broken states.

---

## 8. Failure recovery patterns

### 8.1 Retry-then-compensate

Retry the failing step N times (with backoff). If still failing, trigger compensation.

### 8.2 Semantic lock

Mark a resource as "pending" at step 1 (seat held, not sold). Compensation just releases the lock. The DB never has inconsistent state visible to other users.

### 8.3 Pivot transaction

```
[compensable steps] → PIVOT → [retryable steps]
        ↑                              ↑
   if fail: roll back            if fail: keep retrying
```

Everything before the pivot is undoable. Everything after is "we will succeed eventually". You pick the pivot at the last compensable point (e.g., right after charging; before issuing).

### 8.4 Human-in-the-loop

Workflow pauses until a human approves. The engine sleeps, sometimes for days. `await workflow.wait_condition(lambda: self.approval)` with a 7-day timeout.

### 8.5 Asynchronous compensation

Compensation isn't free and isn't instant. Model it as its own saga if it has multiple steps (e.g., "refund → notify → archive").

---

## 9. Observability

You need at least:

- **Workflow ID** (correlation ID) stamped on every event, log line, metric.
- **Current step** per in-flight workflow (engine UI usually has this).
- **Age** of each in-flight workflow. Anything older than P99 expected is suspect.
- **Failure rate per activity**.
- **Compensation rate** — high = something upstream is flaky.
- **Replay history** — a full timeline of every step, retry, signal.

Temporal's UI, Step Functions' console, and Conductor's dashboard all show workflow timelines — this is why orchestration wins on observability.

---

## 10. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| Hand-rolled saga with try/finally in app code | Process crash = inconsistent state; no durable retry |
| Using DB transaction to span service calls | Two-phase commit; blocks on participant failure; usually unsupported |
| Compensation that's "just the inverse API call" | Not idempotent; not crash-safe |
| Mixing business logic inside the workflow engine with side effects | Side effects inside non-activity code break replay |
| No idempotency keys on activities | Retries double-charge, double-email, double-book |
| Ignoring versioning | New deploy breaks in-flight workflows |
| Long workflow executing as one thread | Memory hogged for hours/days; restart = lost state |
| Choreography with > 5 services | Invisible workflow; debugging = reading event logs by hand |
| No pivot-point analysis | You try to compensate a non-compensable action |
| Compensation that races with the original | Timeout on the forward step triggers compensation, but the forward step finally succeeds |

---

## 11. Interview talking points

- **Name the choice.** "Saga vs distributed transaction — we pick saga because 2PC doesn't compose with HTTP services."
- **Orchestration vs choreography.** "We pick orchestration above 3 steps for observability."
- **Compensations are semantic, not rollback.** Explicit point.
- **Durable execution.** Mention Temporal / Cadence / Step Functions. "Not a queue of tasks — a *replayable log* of decisions."
- **Idempotency on every activity.** Tie to [`06`](06-IdempotencyAndDeduplication.md).
- **Versioning for running workflows.** Signal of operational experience.
- **Non-compensable actions need pivot transactions.** Senior signal.
- **Timeouts at every level.** Schedule-to-start, start-to-close, heartbeat.
- **Observability.** "The workflow ID is the correlation ID."

---

## 12. Related reading

- [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md) — every activity must be idempotent.
- [10-DeadLetterQueuesAndPoisonMessages.md](10-DeadLetterQueuesAndPoisonMessages.md) — what to do with permanently failing saga steps.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — outbox for reliable event publishing from saga steps.
- [20-CrossShardAndDistributedTransactions.md](20-CrossShardAndDistributedTransactions.md) — when sagas and 2PC are alternatives.
- [../03-AdvancedConcepts/03-DistributedTransactions.md](../03-AdvancedConcepts/03-DistributedTransactions.md) — 2PC, 3PC, saga theory.
- [../InterviewProblems/README.md](../InterviewProblems/README.md) — saga comes up in payments, booking, and workflow problems.
