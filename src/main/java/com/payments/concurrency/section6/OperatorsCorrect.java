package com.payments.concurrency.section6;

import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * SECTION 6 — Correct Implementation: Parallel validations + combined risk
 *
 * OPERATOR GUIDE:
 * ┌──────────────┬─────────────────────────────────────────────────────────────┐
 * │ Operator     │ When to use                                                 │
 * ├──────────────┼─────────────────────────────────────────────────────────────┤
 * │ thenApply    │ Transform a value → value (synchronous lambda)              │
 * │ thenCompose  │ Transform a value → CompletableFuture (async next step)     │
 * │ thenCombine  │ Merge two independent futures into one value                │
 * │ allOf        │ Wait for multiple futures; then join individually            │
 * └──────────────┴─────────────────────────────────────────────────────────────┘
 *
 * FLOW IN THIS DEMO:
 *   1. kycF and limitF run in PARALLEL on validationPool   (independent)
 *   2. allOf waits for both; thenApply combines the booleans
 *   3. thenCompose: if valid → kick off mlF and velF in PARALLEL on fraudPool
 *   4. thenCombine merges mlScore + velocity into a Risk record
 *   5. thenApply converts Risk → Decision (synchronous)
 */
public class OperatorsCorrect {

    record Req(String id, long amount) {}
    record Risk(double ml, int velocity) {}
    enum Decision { APPROVE, REVIEW, DECLINE }

    public static void main(String[] args) {
        ExecutorService validationPool = Executors.newFixedThreadPool(8);
        ExecutorService fraudPool      = Executors.newFixedThreadPool(8);

        Req req = new Req("TXN-500", 1_200L);

        // ── Step 1: Run KYC and limit checks in parallel ─────────────────────
        CompletableFuture<Boolean> kycF   = CompletableFuture.supplyAsync(() -> {
            System.out.println("[PIPELINE] KYC check running on " + Thread.currentThread().getName());
            return true; // KYC passed
        }, validationPool);

        CompletableFuture<Boolean> limitF = CompletableFuture.supplyAsync(() -> {
            System.out.println("[PIPELINE] Limit check running on " + Thread.currentThread().getName());
            return req.amount() <= 5_000; // within limit
        }, validationPool);

        // ── Step 2: Combine validation results ───────────────────────────────
        CompletableFuture<Boolean> validF = CompletableFuture
                .allOf(kycF, limitF)
                .thenApply(v -> kycF.join() && limitF.join());
        // Note: join() is safe here because allOf guarantees both are complete

        // ── Step 3: Conditionally run fraud checks ───────────────────────────
        CompletableFuture<Decision> decisionF = validF.thenCompose(valid -> {
            if (!valid) {
                System.out.println("[PIPELINE] Validation failed → DECLINE immediately");
                return CompletableFuture.completedFuture(Decision.DECLINE);
            }

            // ── Step 4: ML score and velocity in parallel ─────────────────
            CompletableFuture<Double> mlF = CompletableFuture
                    .supplyAsync(() -> {
                        System.out.println("[PIPELINE] ML score running on " + Thread.currentThread().getName());
                        sleep(120); return 0.82;
                    }, fraudPool)
                    .orTimeout(150, MILLISECONDS)
                    .exceptionally(ex -> {
                        System.out.println("[PIPELINE] ML timeout — using fallback score");
                        return 0.30; // safe fallback on vendor timeout
                    });

            CompletableFuture<Integer> velF = CompletableFuture.supplyAsync(() -> {
                System.out.println("[PIPELINE] Velocity check running on " + Thread.currentThread().getName());
                return 4; // 4 transactions in last hour
            }, fraudPool);

            // ── Step 5: Combine ML + velocity → Risk ──────────────────────
            CompletableFuture<Risk> riskF = mlF.thenCombine(velF, Risk::new);

            // ── Step 6: Map Risk → Decision ───────────────────────────────
            return riskF.thenApply(r -> {
                if (r.ml() > 0.8)  return Decision.DECLINE;
                if (r.ml() > 0.5)  return Decision.REVIEW;
                return Decision.APPROVE;
            });
        });

        System.out.println("[PIPELINE] Final Decision=" + decisionF.join());

        validationPool.shutdown();
        fraudPool.shutdown();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
