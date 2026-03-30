package com.payments.concurrency.section8;

import java.util.concurrent.*;

/**
 * SECTION 8 — Timeouts (Java 17)
 *
 * PROBLEM: NoTimeoutDemo
 * Without a timeout, the CompletableFuture waits as long as the dependency takes.
 * If the dependency stalls (network partition, GC pause, overloaded host), the
 * future never completes — the calling thread blocks in .join() indefinitely.
 *
 * Effects under load:
 *   - Request threads pile up waiting for .join() to return
 *   - Thread pool exhausts → new requests get rejected or queue up
 *   - Even after dependency recovers, service is slow draining the backlog
 *   - Semaphore permits (if used) are held indefinitely → capacity collapses
 *
 * FIX: See TimeoutsCorrect — use orTimeout() or completeOnTimeout() so every
 * external call has a bounded wait time, no matter what the dependency does.
 */
public class NoTimeoutDemo {

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        System.out.println("[NO-TIMEOUT] Calling slow dependency — no timeout set...");

        // ❌ No orTimeout() — if slowCall() never returns, this blocks forever
        CompletableFuture<String> f =
                CompletableFuture.supplyAsync(NoTimeoutDemo::slowCall, pool);

        // This .join() will block until slowCall() finishes — no escape hatch
        System.out.println("[NO-TIMEOUT] Result=" + f.join()
                + "  (waited the full duration — dangerous in production)");

        pool.shutdown();
    }

    // Simulates a dependency that takes 3 seconds — in production this could be infinite
    static String slowCall() {
        sleep(3_000);
        return "FINALLY_OK";
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
