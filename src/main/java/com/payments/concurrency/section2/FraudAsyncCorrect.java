package com.payments.concurrency.section2;

import java.util.concurrent.*;

/**
 * SECTION 2 — Correct Implementation: Dedicated fraud pool + timeout policy
 *
 * FIXES applied over BlockingInsideAsyncDemo:
 *   1. Dedicated fraudPool — slow IO calls don't share threads with fast validations.
 *   2. orTimeout(150ms) — if the vendor takes too long, the future fails with TimeoutException.
 *   3. exceptionally(ex -> 50) — on any failure/timeout, fall back to a neutral score (50).
 *      The fallback is explicit and should be logged/monitored in production.
 *   4. thenApply — transforms the score to a decision WITHOUT blocking any thread.
 *      No get() is called mid-pipeline.
 */
public class FraudAsyncCorrect {

    enum Decision { APPROVE, REVIEW, BLOCK }

    public static void main(String[] args) {
        ExecutorService fraudPool = Executors.newFixedThreadPool(8);

        CompletableFuture<Integer> scoreF = CompletableFuture
                .supplyAsync(FraudAsyncCorrect::slowFraudScore, fraudPool)
                .orTimeout(150, TimeUnit.MILLISECONDS)       // ✅ explicit timeout
                .exceptionally(ex -> {                       // ✅ explicit fallback
                    System.out.println("[FRAUD] Timeout/failure — using fallback score=50: " + ex.getClass().getSimpleName());
                    return 50;
                });

        CompletableFuture<Decision> decisionF = scoreF.thenApply(score -> {
            // ✅ Pure transformation — no blocking, no thread occupied while waiting
            if (score >= 90) return Decision.BLOCK;
            if (score >= 75) return Decision.REVIEW;
            return Decision.APPROVE;
        });

        System.out.println("[FRAUD] Decision=" + decisionF.join());
        fraudPool.shutdown();
    }

    // Simulates a slow external ML/fraud vendor call (200ms > timeout of 150ms → triggers timeout)
    static int slowFraudScore() { sleep(200); return 90; }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
