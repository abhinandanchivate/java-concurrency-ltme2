package com.payments.concurrency.section9;

import java.util.concurrent.*;

/**
 * SECTION 9 — Concurrency Limiting with Semaphore (Bulkhead Pattern)
 *
 * WHY BULKHEADS?
 * Even at steady request rate, concurrency grows when downstream calls slow down.
 * Without a cap, in-flight calls pile up, downstream degrades further, and the service
 * spends all its capacity waiting.
 *
 * SEMAPHORE AS BULKHEAD:
 *   - maxConcurrent permits = maximum simultaneous in-flight calls to one dependency.
 *   - When no permits are available → reject immediately (tryAcquire returns false).
 *   - Permits must be released on EVERY path — success, failure, timeout — via whenComplete.
 *
 * tryAcquire() vs acquire():
 *   acquire()    → blocks the caller until a permit is available → threads pile up under load.
 *   tryAcquire() → returns false immediately if no permit → fast rejection, predictable behavior.
 *
 * PRODUCTION SIZING:
 *   permits = (target_latency_ms / avg_vendor_latency_ms) × desired_concurrency
 *   Example: want 100 RPS, vendor avg=50ms, timeout=150ms → permits ≈ 10–20.
 *
 * PAIR WITH:
 *   - orTimeout() so permits aren't held indefinitely when a dependency stalls (Section 8).
 *   - Metrics on rejection rate to know when to scale permits or the dependency.
 */
public class SemaphoreBulkheadCorrect {

    public static final class BulkheadLimiter {
        private final Semaphore permits;
        private final String name;

        public BulkheadLimiter(String name, int maxConcurrent) {
            this.name    = name;
            this.permits = new Semaphore(maxConcurrent);
        }

        /**
         * Non-blocking execution: acquire a permit or fail immediately.
         * Permit is released in whenComplete regardless of outcome.
         */
        public <T> CompletableFuture<T> execute(Callable<T> call, Executor executor) {
            if (!permits.tryAcquire()) {
                System.out.println("[BULKHEAD-" + name + "] Rejected — "
                        + permits.availablePermits() + " permits in use");
                return CompletableFuture.failedFuture(
                        new RuntimeException("TOO_MANY_INFLIGHT"));
            }

            return CompletableFuture
                    .supplyAsync(() -> {
                        try { return call.call(); }
                        catch (Exception e) { throw new CompletionException(e); }
                    }, executor)
                    .whenComplete((r, ex) -> {
                        permits.release(); // ✅ ALWAYS released — no permit drain over time
                        System.out.println("[BULKHEAD-" + name + "] Permit released — "
                                + permits.availablePermits() + " available");
                    });
        }

        /**
         * Blocking-with-timeout variant: wait up to {@code timeout} for a permit.
         * Use sparingly — only when dropping work is worse than slight latency increase.
         */
        public <T> CompletableFuture<T> executeWithWait(
                Callable<T> call, Executor executor, long timeout, TimeUnit unit) {
            try {
                if (!permits.tryAcquire(timeout, unit)) {
                    return CompletableFuture.failedFuture(
                            new RuntimeException("TOO_MANY_INFLIGHT"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(e);
            }

            return CompletableFuture
                    .supplyAsync(() -> {
                        try { return call.call(); }
                        catch (Exception e) { throw new CompletionException(e); }
                    }, executor)
                    .whenComplete((r, ex) -> permits.release());
        }
    }

    // ── Demo ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool    = Executors.newFixedThreadPool(8);
        BulkheadLimiter limiter = new BulkheadLimiter("fraud-vendor", 2); // only 2 concurrent calls

        CompletableFuture<?>[] futures = new CompletableFuture[10];

        for (int i = 0; i < 10; i++) {
            int id = i;
            futures[i] = limiter
                    .execute(() -> { sleep(200); return "OK-" + id; }, pool)
                    .thenAccept(result -> System.out.println("[BULKHEAD] Completed: " + result))
                    .exceptionally(ex -> {
                        System.out.println("[BULKHEAD] Rejected task-" + id + ": " + ex.getMessage());
                        return null;
                    });
        }

        CompletableFuture.allOf(futures).join();
        pool.shutdown();
        System.out.println("[BULKHEAD] Done — only 2 ran concurrently; rest were rejected immediately.");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
