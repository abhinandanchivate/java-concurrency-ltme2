# Java 17 Concurrency & Async Programming

A runnable Maven project covering all 10 topics from the **Day 2A** course material.

---

## Project Structure

```
src/main/java/com/payments/concurrency/
├── ConcurrencyRunner.java          ← Runs all sections in sequence
├── section1/
│   ├── UnsafeDebitDemo.java        ← Race condition (shows the bug)
│   ├── SafeDebitCAS.java           ← Fix A: CAS with AtomicLong
│   ├── SafeTransferDemo.java       ← Fix B: Lock ordering (deadlock prevention)
│   └── IdempotencyGate.java        ← Fix C: ConcurrentHashMap deduplication
├── section2/
│   ├── BlockingInsideAsyncDemo.java ← Anti-pattern: get() inside async pool
│   └── FraudAsyncCorrect.java      ← Dedicated pool + orTimeout + fallback
├── section3/
│   ├── UnboundedBacklogDemo.java   ← Anti-pattern: Executors.newFixedThreadPool
│   └── BoundedExecutorCorrect.java ← ThreadPoolExecutor + ArrayBlockingQueue + CallerRunsPolicy
├── section4/
│   ├── SleepBackoffDemo.java       ← Anti-pattern: sleep() in worker thread
│   ├── ScheduledRetryCorrect.java  ← Exponential backoff without blocking threads
│   └── PeriodicScheduledDemo.java  ← scheduleAtFixedRate vs scheduleWithFixedDelay
├── section5/
│   └── ForkJoinAggregationCorrect.java ← RecursiveTask: local agg + tree merge
├── section6/
│   ├── NestedFutureDemo.java       ← Anti-pattern: thenApply returning a future
│   └── OperatorsCorrect.java       ← allOf / thenCompose / thenCombine / thenApply
├── section7/
│   └── ErrorHandlingCorrect.java   ← handle + whenComplete for guaranteed cleanup
├── section8/
│   └── TimeoutsCorrect.java        ← orTimeout vs completeOnTimeout
└── section9/
    └── SemaphoreBulkheadCorrect.java ← tryAcquire + whenComplete release

src/test/java/com/payments/concurrency/
├── section1/Section1Test.java      ← CAS, transfer, idempotency tests
└── section2/Section2to9Test.java   ← Timeout, error handling, bulkhead tests
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 17+     |
| Maven| 3.8+    |

---

## Build & Run

```bash
# Compile
mvn compile

# Run all sections via the main runner
mvn exec:java -Dexec.mainClass=com.payments.concurrency.ConcurrencyRunner

# Run a specific section
mvn exec:java -Dexec.mainClass=com.payments.concurrency.section1.SafeDebitCAS

# Run all tests
mvn test
```

---

## Section-by-Section Guide

### Section 1 — Concurrent Transaction Validation
| Class | What it shows |
|-------|--------------|
| `UnsafeDebitDemo` | Race condition — two threads both pass the balance check → negative balance |
| `SafeDebitCAS` | CAS loop with `AtomicLong` — atomic check-and-debit for a single numeric invariant |
| `SafeTransferDemo` | Lock ordering — always acquire locks in consistent ID order to prevent deadlock |
| `IdempotencyGate` | `ConcurrentHashMap.newKeySet()` — thread-safe deduplication of transaction IDs |

### Section 2 — Async Fraud Checks
| Class | What it shows |
|-------|--------------|
| `BlockingInsideAsyncDemo` | Anti-pattern: `.get()` inside async pipeline on the same bounded pool |
| `FraudAsyncCorrect` | Dedicated pool + `orTimeout` + `exceptionally` fallback — non-blocking pipeline |

### Section 3 — ExecutorService Thread Pool Design
| Class | What it shows |
|-------|--------------|
| `UnboundedBacklogDemo` | Hidden unbounded queue in `Executors.newFixedThreadPool` |
| `BoundedExecutorCorrect` | `ThreadPoolExecutor` + `ArrayBlockingQueue` + `CallerRunsPolicy` + named threads |

### Section 4 — ScheduledExecutorService
| Class | What it shows |
|-------|--------------|
| `SleepBackoffDemo` | Sleep wastes a worker thread for the entire delay |
| `ScheduledRetryCorrect` | Exponential backoff via `scheduler.schedule()` — thread released immediately |
| `PeriodicScheduledDemo` | `scheduleAtFixedRate` vs `scheduleWithFixedDelay` with exception handling |

### Section 5 — Fork/Join RecursiveTask
`ForkJoinAggregationCorrect` — local `HashMap` per leaf, tree merge, dedicated `ForkJoinPool`

### Section 6 — CompletableFuture Composition
| Operator | Use when |
|----------|---------|
| `thenApply` | Transform value → value (sync) |
| `thenCompose` | Start async next step that returns a `CompletableFuture` |
| `thenCombine` | Merge two independent futures |
| `allOf` | Wait for N futures, then join individually |

### Section 7 — Error Handling
| Operator | Use when |
|----------|---------|
| `exceptionally` | Recovery value on failure only |
| `handle` | Unified output for both success and failure |
| `whenComplete` | Cleanup (permit release, metrics, audit) — does NOT change the value |

### Section 8 — Timeouts
| Method | Behaviour on timeout |
|--------|---------------------|
| `orTimeout(n, unit)` | Completes exceptionally with `TimeoutException` |
| `completeOnTimeout(val, n, unit)` | Completes normally with the provided default value |

### Section 9 — Bulkhead Pattern (Semaphore)
`SemaphoreBulkheadCorrect` — `tryAcquire()` for non-blocking rejection + `whenComplete` for guaranteed permit release.

---

## Cross-Cutting Best Practices (Section 10)

1. **Use `long` (minor units) for money** — no floating-point rounding.
2. **Bound all queues** — unbounded queues hide overload until OOM.
3. **Separate pools by workload type** — slow IO must not starve fast validation.
4. **Never block inside async pipelines** — use composition operators instead of `.get()`.
5. **Add timeouts to every external call** — protect capacity during dependency degradation.
6. **Release resources in `whenComplete`** — runs on both success and failure paths.
7. **Use Fork/Join only for CPU-bound work** — no blocking IO inside `compute()`.
8. **Make fallbacks explicit and observable** — log/meter every fallback activation.
9. **Name threads and executors** — essential for diagnosing deadlocks and saturation in production.
