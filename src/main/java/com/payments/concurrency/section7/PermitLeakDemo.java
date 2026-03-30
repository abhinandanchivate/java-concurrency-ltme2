package com.payments.concurrency.section7;

import java.util.concurrent.*;

/**
 * SECTION 7 — Error Handling: exceptionally, handle, whenComplete
 *
 * PROBLEM: PermitLeakDemo
 * sem.release() is attached only to the SUCCESS path via thenAccept.
 * When the vendor throws, the exceptionally branch handles the error but
 * the permit is NEVER released.
 *
 * After enough failures, all permits are permanently consumed.
 * Every new request gets rejected with "LIMIT" even though zero real calls are in-flight.
 * The service silently degrades to zero capacity.
 *
 * FIX: See ErrorHandlingCorrect — use whenComplete so the permit is released
 * on BOTH success and failure paths, always.
 */
public class PermitLeakDemo {

    static final Semaphore sem  = new Semaphore(2);
    static final ExecutorService pool = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        // Simulate 4 calls — after 2 failures, permits are exhausted permanently
        for (int i = 1; i <= 4; i++) {
            CompletableFuture<Integer> f = call(i);

            // ❌ Release ONLY on success — failure path leaks the permit
            f.thenAccept(v -> {
                sem.release();
                System.out.println("[LEAK] Success — permit released. Available=" + sem.availablePermits());
            });

            f.exceptionally(ex -> {
                // ❌ No sem.release() here — permit is gone forever after this failure
                System.out.println("[LEAK] Failed: " + ex.getMessage()
                        + " — permit NOT released. Available=" + sem.availablePermits());
                return null;
            }).join();
        }

        System.out.println("[LEAK] Final available permits=" + sem.availablePermits()
                + "  (should be 2 but is 0 — capacity drained by leaked permits)");
        pool.shutdown();
    }

    static CompletableFuture<Integer> call(int id) {
        if (!sem.tryAcquire()) {
            System.out.println("[LEAK] Call-" + id + " rejected — no permits (already leaked)");
            return CompletableFuture.failedFuture(new RuntimeException("LIMIT"));
        }
        System.out.println("[LEAK] Call-" + id + " acquired permit. Available=" + sem.availablePermits());
        // ❌ Vendor always throws — permit is acquired but will never be released
        return CompletableFuture.supplyAsync(
                () -> { throw new RuntimeException("vendor down"); }, pool);
    }
}
