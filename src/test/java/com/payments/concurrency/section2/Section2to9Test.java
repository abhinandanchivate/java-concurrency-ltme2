package com.payments.concurrency.section2;

import com.payments.concurrency.section7.ErrorHandlingCorrect;
import com.payments.concurrency.section8.TimeoutsCorrect;
import com.payments.concurrency.section9.SemaphoreBulkheadCorrect;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class Section2to9Test {

    // ── Section 2: FraudAsyncCorrect ─────────────────────────────────────────

    @Test
    @DisplayName("Fraud async: timeout fires and exceptionally returns fallback score")
    void fraudTimeoutReturnsFallback() {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // Vendor takes 200ms, timeout is 150ms → exceptionally fires → fallback = 50
        CompletableFuture<Integer> scoreF = CompletableFuture
                .supplyAsync(() -> { sleep(200); return 99; }, pool)
                .orTimeout(150, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> 50);

        int score = scoreF.join();
        assertEquals(50, score, "timed-out call must return fallback score 50");
        pool.shutdown();
    }

    @Test
    @DisplayName("Fraud async: fast vendor completes before timeout")
    void fraudFastVendorCompletesNormally() {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        CompletableFuture<Integer> scoreF = CompletableFuture
                .supplyAsync(() -> { sleep(50); return 90; }, pool)
                .orTimeout(200, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> 50);

        int score = scoreF.join();
        assertEquals(90, score, "fast vendor must return its actual score");
        pool.shutdown();
    }

    // ── Section 7: ErrorHandlingCorrect ─────────────────────────────────────

    @Test
    @DisplayName("Error handling: permits are not leaked after a vendor failure")
    void permitsNotLeakedOnFailure() {
        Semaphore sem = new Semaphore(2);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // Simulate the correct pattern: handle + whenComplete always releases permit
        Runnable callWithGuaranteedRelease = () -> {
            if (!sem.tryAcquire()) return;
            CompletableFuture
                    .supplyAsync(() -> { throw new RuntimeException("vendor down"); }, pool)
                    .handle((v, ex) -> ex != null ? -1 : v)   // normalize
                    .whenComplete((r, ex) -> sem.release())    // always release
                    .join();
        };

        for (int i = 0; i < 10; i++) callWithGuaranteedRelease.run();

        assertEquals(2, sem.availablePermits(), "all permits must be returned after 10 failures");
        pool.shutdown();
    }

    // ── Section 8: TimeoutsCorrect ───────────────────────────────────────────

    @Test
    @DisplayName("orTimeout: completes exceptionally when call exceeds timeout")
    void orTimeoutCompletesExceptionally() {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        CompletableFuture<String> f = CompletableFuture
                .supplyAsync(() -> { sleep(300); return "OK"; }, pool)
                .orTimeout(100, TimeUnit.MILLISECONDS);

        assertThrows(Exception.class, f::join, "orTimeout must complete exceptionally");
        pool.shutdown();
    }

    @Test
    @DisplayName("completeOnTimeout: returns default value when call exceeds timeout")
    void completeOnTimeoutReturnsDefault() {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        String result = CompletableFuture
                .supplyAsync(() -> { sleep(300); return "REAL"; }, pool)
                .completeOnTimeout("DEFAULT", 100, TimeUnit.MILLISECONDS)
                .join();

        assertEquals("DEFAULT", result, "completeOnTimeout must return the default value");
        pool.shutdown();
    }

    @Test
    @DisplayName("orTimeout: fast call completes normally without timeout")
    void orTimeoutFastCallSucceeds() {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        String result = CompletableFuture
                .supplyAsync(() -> { sleep(30); return "FAST"; }, pool)
                .orTimeout(200, TimeUnit.MILLISECONDS)
                .join();

        assertEquals("FAST", result, "call finishing before timeout must return real value");
        pool.shutdown();
    }

    // ── Section 9: SemaphoreBulkheadCorrect ─────────────────────────────────

    @Test
    @DisplayName("Bulkhead: excess requests are rejected immediately via tryAcquire")
    void bulkheadRejectsExcessRequests() {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        SemaphoreBulkheadCorrect.BulkheadLimiter limiter =
                new SemaphoreBulkheadCorrect.BulkheadLimiter("test", 2);

        java.util.concurrent.atomic.AtomicInteger rejections = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger successes  = new java.util.concurrent.atomic.AtomicInteger(0);
        CompletableFuture<?>[] futures = new CompletableFuture[10];

        for (int i = 0; i < 10; i++) {
            futures[i] = limiter
                    .execute(() -> { sleep(200); return "OK"; }, pool)
                    .thenRun(successes::incrementAndGet)
                    .exceptionally(ex -> { rejections.incrementAndGet(); return null; });
        }

        CompletableFuture.allOf(futures).join();
        pool.shutdown();

        assertTrue(successes.get() <= 2, "at most 2 concurrent calls should succeed, got: " + successes.get());
        assertTrue(rejections.get() >= 8, "at least 8 excess requests should be rejected, got: " + rejections.get());
    }

    @Test
    @DisplayName("Bulkhead: permits are returned after completion so capacity is restored")
    void bulkheadPermitsRestoredAfterCompletion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        SemaphoreBulkheadCorrect.BulkheadLimiter limiter =
                new SemaphoreBulkheadCorrect.BulkheadLimiter("test", 2);

        // First batch: 2 calls complete normally
        CompletableFuture.allOf(
                limiter.execute(() -> { sleep(50); return "A"; }, pool),
                limiter.execute(() -> { sleep(50); return "B"; }, pool)
        ).join();

        // After completion, capacity should be fully restored → new call should succeed
        String result = limiter.execute(() -> "RESTORED", pool).join();
        assertEquals("RESTORED", result, "permits must be restored after completion");

        pool.shutdown();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
