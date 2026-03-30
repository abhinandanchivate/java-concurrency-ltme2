package com.payments.concurrency.section3;

import java.util.concurrent.*;

/**
 * SECTION 3 — ExecutorService: Designing Thread Pools for Load
 *
 * PROBLEM: UnboundedBacklogDemo
 * Executors.newFixedThreadPool(n) uses a LinkedBlockingQueue with no capacity limit.
 * Submitting work faster than it can be processed silently accumulates tasks in memory.
 * The backlog is invisible until OOM errors or severe GC pressure appear.
 *
 * This demo submits 50,000 tasks to a 2-thread pool — tasks pile up with no backpressure.
 */
public class UnboundedBacklogDemo {

    public static void main(String[] args) throws InterruptedException {
        // ❌ Unbounded queue hidden behind the convenience factory
        ExecutorService pool = Executors.newFixedThreadPool(2);

        int submitted = 0;
        for (int i = 0; i < 50_000; i++) {
            pool.submit(() -> sleep(50));
            submitted++;
        }

        System.out.println("[UNBOUNDED] Submitted " + submitted + " tasks — backlog can be enormous.");
        System.out.println("[UNBOUNDED] Use BoundedExecutorCorrect for production traffic.");
        pool.shutdownNow(); // skip waiting — the point is illustrated
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
