package com.payments.concurrency;

import com.payments.concurrency.section1.*;
import com.payments.concurrency.section2.*;
import com.payments.concurrency.section3.*;
import com.payments.concurrency.section4.*;
import com.payments.concurrency.section5.*;
import com.payments.concurrency.section6.*;
import com.payments.concurrency.section7.*;
import com.payments.concurrency.section8.*;
import com.payments.concurrency.section9.*;
import com.payments.concurrency.section5.ContendedAggregationDemo;
import com.payments.concurrency.section7.PermitLeakDemo;
import com.payments.concurrency.section8.NoTimeoutDemo;

/**
 * Main runner — executes all 9 sections sequentially with clear banners.
 *
 * Run individual sections directly via their own main() for focused exploration.
 */
public class ConcurrencyRunner {

    public static void main(String[] args) throws Exception {
        run("1A — Unsafe Debit (Race Condition Demo)",     () -> UnsafeDebitDemo.main(null));
        run("1B — Safe Debit CAS (AtomicLong)",            () -> SafeDebitCAS.main(null));
        run("1C — Safe Transfer (Lock Ordering)",          () -> SafeTransferDemo.main(null));
        run("1D — Idempotency Gate",                       () -> IdempotencyGate.main(null));
        run("2A — Blocking Inside Async (Anti-pattern)",   () -> BlockingInsideAsyncDemo.main(null));
        run("2B — Fraud Async Correct",                    () -> FraudAsyncCorrect.main(null));
        run("3A — Unbounded Backlog Demo",                 () -> UnboundedBacklogDemo.main(null));
        run("3B — Bounded Executor with Backpressure",     () -> BoundedExecutorCorrect.main(null));
        run("4A — Sleep-based Retry (Anti-pattern)",       () -> SleepBackoffDemo.main(null));
        run("4B — Scheduled Retry (Exponential Backoff)",  () -> ScheduledRetryCorrect.main(null));
        run("4C — Periodic Scheduled Tasks",               () -> PeriodicScheduledDemo.main(null));
        run("5A — Contended Aggregation (Anti-pattern)",   () -> ContendedAggregationDemo.main(null));
        run("5B — Fork/Join Aggregation (Correct)",        () -> ForkJoinAggregationCorrect.main(null));
        run("6A — Nested Future (Anti-pattern)",           () -> NestedFutureDemo.main(null));
        run("6B — Operators: allOf/thenCompose/thenCombine", () -> OperatorsCorrect.main(null));
        run("7A — Permit Leak on Failure (Anti-pattern)",  () -> PermitLeakDemo.main(null));
        run("7B — Error Handling: handle + whenComplete",  () -> ErrorHandlingCorrect.main(null));
        run("8A — No Timeout Demo (Anti-pattern)",         () -> NoTimeoutDemo.main(null));
        run("8B — Timeouts: orTimeout + completeOnTimeout",() -> TimeoutsCorrect.main(null));
        run("9  — Semaphore Bulkhead Pattern",             () -> SemaphoreBulkheadCorrect.main(null));
    }

    static void run(String title, ThrowingRunnable r) throws Exception {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("  SECTION: " + title);
        System.out.println("═".repeat(70));
        r.run();
        Thread.sleep(200); // brief pause between sections for readability
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
