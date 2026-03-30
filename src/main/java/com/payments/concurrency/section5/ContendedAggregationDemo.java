package com.payments.concurrency.section5;

import java.util.*;
import java.util.concurrent.*;

/**
 * SECTION 5 — Fork/Join RecursiveTask for Aggregation
 *
 * PROBLEM: ContendedAggregationDemo
 * parallelStream() distributes work across threads, but all threads write to the SAME
 * ConcurrentHashMap. With 2M transactions and only 1000 merchants, many threads contend
 * on the same hot keys (e.g., "M0" is updated 2000 times).
 *
 * ConcurrentHashMap uses stripe-level locking internally, but under heavy contention on
 * hot keys, threads spin-wait on the same bucket — reducing throughput as parallelism
 * increases. More threads = more contention, not more speed.
 *
 * FIX: See ForkJoinAggregationCorrect — aggregate locally per task, then merge results
 * up the tree. Zero shared contention during aggregation.
 */
public class ContendedAggregationDemo {

    public static void main(String[] args) {
        // Shared map — all parallel threads write here → hot key contention
        var totals = new ConcurrentHashMap<String, Long>();

        List<String> merchants = new ArrayList<>();
        for (int i = 0; i < 2_000_000; i++) merchants.add("M" + (i % 1000));

        long start = System.currentTimeMillis();

        // ❌ All threads contend on the same 1000 keys inside ConcurrentHashMap
        merchants.parallelStream().forEach(m -> totals.merge(m, 10L, Long::sum));

        System.out.println("[CONTENDED] Merchants=" + totals.size()
                + "  elapsed=" + (System.currentTimeMillis() - start) + "ms"
                + "  (hot-key contention under parallelism)");
    }
}
