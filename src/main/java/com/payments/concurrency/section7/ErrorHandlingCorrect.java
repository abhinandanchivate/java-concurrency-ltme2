package com.payments.concurrency.section7;

import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * SECTION 7 — Error Handling: exceptionally, handle, whenComplete
 *
 * ── PROBLEM: PermitLeakDemo ─────────────────────────────────────────────────
 * Permits are released only on the SUCCESS path via thenAccept.
 * When the vendor throws, the exceptionally branch runs but sem.release() is NOT called.
 * After enough failures, all permits are permanently consumed — every new request gets
 * "LIMIT" even though no real work is in-flight.
 *
 * ── OPERATOR GUIDE ──────────────────────────────────────────────────────────
 * exceptionally(fn)     → Called only on failure. Provides a recovery value.
 *                         Cannot distinguish "what succeeded" — use handle for that.
 *
 * handle(fn(val, ex))   → Called on BOTH success and failure.
 *                         Converts (value | exception) → output value.
 *                         Great for unified normalization (e.g., score or fallback).
 *
 * whenComplete(fn)      → Called on BOTH success and failure, but does NOT change the value.
 *                         Returns the same future (pass-through). Perfect for cleanup:
 *                         release semaphores, record metrics, write audit logs.
 *
 * ── CORRECT PATTERN ─────────────────────────────────────────────────────────
 *   .handle(...)         // normalize value or apply fallback
 *   .whenComplete(...)   // cleanup — guaranteed to run on both paths
 */
public class ErrorHandlingCorrect {

    static final Semaphore sem  = new Semaphore(2);
    static final ExecutorService pool = Executors.newFixedThreadPool(4);

    /**
     * Calls a slow vendor with a 150ms timeout.
     * - handle()       → fallback score 50 on timeout or any failure
     * - whenComplete() → always releases the permit, success or failure
     */
    static CompletableFuture<Integer> fraudScore() {
        // Non-blocking permit check — fail fast if at capacity
        if (!sem.tryAcquire()) {
            System.out.println("[ERROR-HANDLING] Rejected — at capacity");
            return CompletableFuture.failedFuture(new RuntimeException("TOO_MANY_INFLIGHT"));
        }

        return CompletableFuture
                .supplyAsync(ErrorHandlingCorrect::slowVendor, pool)
                .orTimeout(150, MILLISECONDS)
                // handle → called on BOTH paths; normalizes to Integer in all cases
                .handle((score, ex) -> {
                    if (ex != null) {
                        System.out.println("[ERROR-HANDLING] Vendor failed ("
                                + ex.getClass().getSimpleName() + ") → fallback score=50");
                        return 50; // ✅ explicit fallback — should also be logged/metered
                    }
                    return score;
                })
                // whenComplete → cleanup; guaranteed to run whether handle produced a value or not
                .whenComplete((r, ex) -> {
                    sem.release(); // ✅ permit ALWAYS returned — no gradual capacity drain
                    System.out.println("[ERROR-HANDLING] Permit released — available="
                            + sem.availablePermits());
                });
    }

    public static void main(String[] args) {
        // First call — vendor is slow (250ms) so orTimeout fires at 150ms
        System.out.println("[ERROR-HANDLING] Score=" + fraudScore().join());

        // Second call — same path; permits should still be available
        System.out.println("[ERROR-HANDLING] Score=" + fraudScore().join());

        pool.shutdown();
    }

    // Simulates a vendor that takes 250ms — always exceeds the 150ms timeout
    static int slowVendor() { sleep(250); return 90; }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
