package com.payments.concurrency.section5;

import java.util.*;
import java.util.concurrent.*;

/**
 * SECTION 5 — Fork/Join RecursiveTask for Aggregation
 *
 * PROBLEM with naive parallel aggregation (ContendedAggregationDemo approach):
 *   ConcurrentHashMap.merge() uses fine-grained locking internally, but under high parallelism
 *   with many hot keys (e.g., 2M transactions across only 1000 merchants), threads contend
 *   on the same keys — reducing throughput as parallelism increases.
 *
 * SOLUTION: Local aggregate + tree merge
 *   1. Each leaf task aggregates its chunk into a plain (non-shared) HashMap — zero contention.
 *   2. As results bubble up the recursion tree, small maps merge into larger ones.
 *   3. Final result is fully merged with O(log N) merge steps instead of N contended inserts.
 *
 * THRESHOLD TUNING:
 *   - Too small → excessive task creation overhead.
 *   - Too large → poor parallelism.
 *   - Start at ~50k–100k per leaf; tune with profiling.
 *
 * CRITICAL: compute() must be CPU-only — no blocking IO, no sleep().
 *           Blocking inside Fork/Join stalls worker threads and reduces parallelism severely.
 */
public class ForkJoinAggregationCorrect {

    record Txn(String merchant, long amount) {}

    static class AggTask extends RecursiveTask<Map<String, Long>> {
        private static final int THRESHOLD = 50_000;

        private final List<Txn> txns;
        private final int start;
        private final int end;

        AggTask(List<Txn> txns, int start, int end) {
            this.txns  = txns;
            this.start = start;
            this.end   = end;
        }

        @Override
        protected Map<String, Long> compute() {
            int size = end - start;

            // Base case: chunk is small enough → aggregate locally with no contention
            if (size <= THRESHOLD) {
                return aggregate(txns.subList(start, end));
            }

            // Recursive case: split into two halves
            int mid = start + size / 2;
            AggTask left  = new AggTask(txns, start, mid);
            AggTask right = new AggTask(txns, mid,   end);

            left.fork();                        // submit left to the pool asynchronously
            Map<String, Long> rightMap = right.compute(); // compute right on this thread
            Map<String, Long> leftMap  = left.join();     // wait for left

            return merge(leftMap, rightMap);
        }

        /** Pure CPU aggregation — no shared state, no contention. */
        private Map<String, Long> aggregate(List<Txn> chunk) {
            Map<String, Long> totals = new HashMap<>(chunk.size() * 2);
            for (Txn t : chunk) {
                totals.merge(t.merchant(), t.amount(), Long::sum);
            }
            return totals;
        }

        /**
         * Merges the smaller map into the larger to minimise copy operations.
         * Mutates and returns {@code a} (the larger map).
         */
        private Map<String, Long> merge(Map<String, Long> a, Map<String, Long> b) {
            // Always merge smaller into larger — reduces iterations
            if (a.size() < b.size()) { var tmp = a; a = b; b = tmp; }
            for (var e : b.entrySet()) {
                a.merge(e.getKey(), e.getValue(), Long::sum);
            }
            return a;
        }
    }

    public static void main(String[] args) {
        // Build 2M transactions across 1000 merchants
        List<Txn> txns = new ArrayList<>(2_000_000);
        for (int i = 0; i < 2_000_000; i++) {
            txns.add(new Txn("M" + (i % 1000), 10L));
        }

        // Dedicated ForkJoinPool — don't use commonPool() for heavy batch jobs
        // Leave 2 cores for GC and OS to avoid competing with JVM housekeeping
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        long start = System.currentTimeMillis();
        Map<String, Long> totals = pool.invoke(new AggTask(txns, 0, txns.size()));
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[FORK-JOIN] Merchants=" + totals.size()
                + "  elapsed=" + elapsed + "ms  parallelism=" + parallelism);

        // Spot-check: each merchant should have 2000 transactions × 10 = 20000
        long sample = totals.getOrDefault("M0", -1L);
        System.out.println("[FORK-JOIN] M0 total=" + sample + " (expected 20000)");

        pool.shutdown();
    }
}
