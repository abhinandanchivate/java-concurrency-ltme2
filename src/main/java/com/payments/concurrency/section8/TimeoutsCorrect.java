package com.payments.concurrency.section8;

import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * SECTION 8 — Timeouts (Java 17)
 *
 * Java 9+ added two timeout methods directly on CompletableFuture:
 *
 * orTimeout(n, unit)
 *   → If not completed within n units, completes EXCEPTIONALLY with TimeoutException.
 *   → Use when a timeout should be treated as a failure (fail-fast, circuit-break).
 *   → Downstream .exceptionally() / .handle() stages then produce fallback behavior.
 *
 * completeOnTimeout(default, n, unit)
 *   → If not completed within n units, completes NORMALLY with the given default value.
 *   → Use when a default is acceptable and you don't want the pipeline to fail.
 *   → The slow supplier continues running in the background (it's not interrupted).
 *
 * CHOOSING BETWEEN THEM:
 *   - Fraud check that MUST respond:            orTimeout + exceptionally(fallback)
 *   - Optional enrichment (device info, etc.):  completeOnTimeout("UNKNOWN")
 *   - Critical dependency (CBS balance check):  orTimeout, then surface the error
 *
 * TIMEOUT SIZING:
 *   - Align per-call timeouts with your overall SLA budget.
 *   - One dependency should not be able to consume the entire SLA.
 *   - Monitor timeout rates in production — a rising rate is an early incident signal.
 */
public class TimeoutsCorrect {

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // ── Scenario A: orTimeout — treat timeout as failure ────────────────
        CompletableFuture<String> failFast =
                CompletableFuture.supplyAsync(TimeoutsCorrect::slowCall, pool)
                        .orTimeout(100, MILLISECONDS); // slowCall takes 200ms → times out

        failFast
                .thenAccept(v -> System.out.println("[TIMEOUT] failFast value=" + v))
                .exceptionally(ex -> {
                    System.out.println("[TIMEOUT] orTimeout fired: " + ex.getClass().getSimpleName());
                    return null;
                })
                .join();

        // ── Scenario B: completeOnTimeout — use default on timeout ──────────
        CompletableFuture<String> withDefault =
                CompletableFuture.supplyAsync(TimeoutsCorrect::slowCall, pool)
                        .completeOnTimeout("DEFAULT_RESPONSE", 100, MILLISECONDS);

        System.out.println("[TIMEOUT] completeOnTimeout result=" + withDefault.join());

        // ── Scenario C: fast call — no timeout fires ─────────────────────────
        CompletableFuture<String> fastEnough =
                CompletableFuture.supplyAsync(TimeoutsCorrect::fastCall, pool)
                        .orTimeout(100, MILLISECONDS);

        System.out.println("[TIMEOUT] fast call result=" + fastEnough.join());

        pool.shutdown();
    }

    static String slowCall() { sleep(200); return "OK"; }  // 200ms > 100ms timeout
    static String fastCall() { sleep(50);  return "FAST_OK"; } // 50ms < 100ms — completes normally

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
