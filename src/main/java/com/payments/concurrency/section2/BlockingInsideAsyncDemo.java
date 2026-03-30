package com.payments.concurrency.section2;

import java.util.concurrent.*;

/**
 * SECTION 2 — Async Fraud Checks with CompletableFuture
 *
 * PROBLEM: BlockingInsideAsyncDemo
 * The decision stage calls scoreF.get() inside a task running on the SAME 2-thread pool.
 * If both pool threads are occupied:
 *   Thread-1: running slowFraudScore (computing the score)
 *   Thread-2: blocked on scoreF.get() waiting for Thread-1
 *
 * With only 2 threads this is a near-deadlock. Under load, this pattern collapses throughput
 * and turns async work back into blocking work.
 */
public class BlockingInsideAsyncDemo {

    public static void main(String[] args) throws Exception {
        // Only 2 threads — same pool used for both fraud computation AND the decision stage
        ExecutorService pool = Executors.newFixedThreadPool(2);

        CompletableFuture<Integer> scoreF =
                CompletableFuture.supplyAsync(BlockingInsideAsyncDemo::slowFraudScore, pool);

        CompletableFuture<String> decisionF =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // ❌ Blocks a pool thread waiting for another stage on the same pool
                        int score = scoreF.get();
                        return score >= 80 ? "REVIEW" : "APPROVE";
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, pool);

        System.out.println("[BLOCKING-ASYNC] Decision=" + decisionF.join()
                + "  (got lucky with 2 threads — fails under higher concurrency)");
        pool.shutdown();
    }

    static int slowFraudScore() { sleep(200); return 90; }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
