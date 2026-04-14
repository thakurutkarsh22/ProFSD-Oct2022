# Design Payment System (Stripe / PayPal)

> **Difficulty:** Hard | **Frequency:** ★★★★★ | **Companies:** Stripe, PayPal, Amazon, Google

---

## 1. Requirements

### Functional
- Process payments (credit card, debit, bank transfer)
- Support multiple currencies
- Refunds and chargebacks
- Payment status tracking
- Ledger / accounting (double-entry bookkeeping)
- Webhook notifications to merchants

### Non-Functional
- EXACTLY-ONCE payment processing (no double charges!)
- Strong consistency (money cannot be lost)
- PCI-DSS compliance (secure card data)
- 99.999% availability
- Audit trail for every transaction

### Scale
- 1M transactions/day, peak: 1000 TPS

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                      PAYMENT SYSTEM                                   │
│                                                                      │
│  ┌──────────┐    ┌────────────────┐    ┌──────────────┐             │
│  │ Merchant │───►│  Payment API   │───►│   Payment    │             │
│  │  App     │    │  (Idempotent)  │    │   Service    │             │
│  └──────────┘    └────────────────┘    └──────┬───────┘             │
│                                               │                      │
│                          ┌────────────────────┼──────────────┐       │
│                          ▼                    ▼              ▼       │
│                   ┌──────────────┐    ┌──────────────┐ ┌─────────┐ │
│                   │   Risk /     │    │    Ledger    │ │ Payment │ │
│                   │   Fraud      │    │   Service    │ │ State   │ │
│                   │   Engine     │    │  (Double-    │ │ Machine │ │
│                   │              │    │   entry)     │ │         │ │
│                   └──────────────┘    └──────────────┘ └─────────┘ │
│                                                                      │
│                          ┌────────────────────────────────┐          │
│                          ▼                                ▼          │
│                   ┌──────────────┐              ┌──────────────┐    │
│                   │  Payment     │              │   Webhook    │    │
│                   │  Gateway     │              │   Service    │    │
│                   │              │              │              │    │
│                   │ Connect to   │              │ Notify       │    │
│                   │ card network │              │ merchant     │    │
│                   │ (Visa,Master)│              │ of status    │    │
│                   └──────────────┘              └──────────────┘    │
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │  Payment DB  │    │  Ledger DB   │    │ Event Store  │          │
│  │ (PostgreSQL) │    │ (PostgreSQL) │    │ (Kafka)      │          │
│  │  ACID!       │    │  ACID!       │    │              │          │
│  └──────────────┘    └──────────────┘    └──────────────┘          │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Payment Flow

```
PAYMENT LIFECYCLE (State Machine):

  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
  │ CREATED  │───►│AUTHORIZED│───►│ CAPTURED │───►│ SETTLED  │
  └──────────┘    └──────────┘    └──────────┘    └──────────┘
       │               │               │
       ▼               ▼               ▼
  ┌──────────┐    ┌──────────┐    ┌──────────┐
  │ DECLINED │    │  VOIDED  │    │ REFUNDED │
  └──────────┘    └──────────┘    └──────────┘

STEP-BY-STEP:
  1. Merchant sends payment request with idempotency_key
  2. Payment Service creates payment record (CREATED)
  3. Fraud engine checks risk score
  4. If approved → send to Payment Gateway (Visa/Mastercard)
  5. Card network authorizes → (AUTHORIZED)
  6. Merchant confirms → capture funds (CAPTURED)
  7. End of day → settlement with bank (SETTLED)
```

---

## 4. Critical Design: Idempotency

```
THE MOST IMPORTANT CONCEPT IN PAYMENT DESIGN:

  Client retries (network timeout, crash, etc.) must NOT charge twice.

  Request:
  POST /api/payments
  Idempotency-Key: "pay_abc123"
  { "amount": 100, "currency": "USD", "card": "tok_xxx" }

  FLOW:
  1. Receive request with idempotency_key = "pay_abc123"
  2. Check DB: does this key already exist?
     YES → return stored result (don't process again)
     NO  → process payment, store result with key
  
  ┌───────────────────────────────────────────┐
  │ idempotency_keys                          │
  │ ┌──────────────┬────────┬───────────────┐│
  │ │ key          │ status │ response      ││
  │ ├──────────────┼────────┼───────────────┤│
  │ │ pay_abc123   │ SUCCESS│ {txn_id: 456} ││
  │ │ pay_def456   │ FAILED │ {error: ...}  ││
  │ └──────────────┴────────┴───────────────┘│
  └───────────────────────────────────────────┘

  Store idempotency_key + payment in SAME transaction (atomic).
```

---

## 5. Double-Entry Ledger

```
  Every payment creates TWO ledger entries (debit + credit).
  Total debits MUST ALWAYS equal total credits.

  Payment: Customer pays Merchant $100

  ┌────────────────┬───────┬────────┬────────────────┐
  │ Entry          │ Debit │ Credit │ Account        │
  ├────────────────┼───────┼────────┼────────────────┤
  │ Charge customer│ $100  │        │ Customer Wallet│
  │ Pay merchant   │       │ $100   │ Merchant Wallet│
  └────────────────┴───────┴────────┴────────────────┘

  Σ Debits = Σ Credits = $100 ✓

  Refund: Merchant refunds $100
  ┌────────────────┬───────┬────────┬────────────────┐
  │ Debit merchant │ $100  │        │ Merchant Wallet│
  │ Credit customer│       │ $100   │ Customer Wallet│
  └────────────────┴───────┴────────┴────────────────┘

  This ensures money is NEVER created or destroyed.
  Can always reconcile by checking debits = credits.
```

---

## 6. Key Points for Interview

1. **Idempotency** is NON-NEGOTIABLE — duplicate payments are catastrophic
2. **Double-entry ledger** for accounting correctness
3. **State machine** for payment lifecycle (created → authorized → captured → settled)
4. **PostgreSQL with ACID** — no eventual consistency for money
5. **PCI-DSS**: never store raw card numbers, use tokenization
6. **Fraud detection** before authorization (ML-based risk scoring)
7. **Webhook + retry** to notify merchants of payment status changes
8. **Reconciliation** job to compare your records with bank/card network
