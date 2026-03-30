package com.payments.jmm.jmm03;

import java.util.concurrent.*;

/**
 * JMM SECTION 3 — Correct: Double-Checked Locking with volatile
 *
 * WHY volatile FIXES IT:
 *   volatile prevents the JVM from reordering the reference assignment before the
 *   constructor body. Specifically, the volatile write to `instance` acts as a
 *   memory barrier — all writes in the constructor happen-before the volatile store.
 *
 *   Any thread that reads `instance != null` is therefore guaranteed to see the
 *   fully constructed object with warmup == 42.
 *
 * THE LOCAL VARIABLE OPTIMIZATION:
 *   `Engine local = instance` reads the volatile field once. The second access
 *   `return local` uses the local variable — avoids a second volatile read (minor
 *   performance optimization, commonly seen in JDK source).
 *
 * PREFER THESE ALTERNATIVES when possible:
 *   a) Eager initialization:  `private static final Engine INSTANCE = new Engine();`
 *      JVM guarantees class-loading happens-before any thread can access it.
 *
 *   b) Initialization-on-demand holder:
 *      Inner static class is loaded only when getInstance() is first called.
 *      No synchronization needed — class loading is thread-safe by the JLS.
 *
 *   c) Enum singleton (for singletons only):
 *      `enum Engine { INSTANCE; int warmup = 42; }`
 */
public class CorrectDoubleCheckedLocking {

    // ✅ volatile — prevents reordering of reference assignment past constructor
    private static volatile Engine instance;

    static final class Engine {
        final int warmup;
        Engine() { warmup = 42; }
    }

    // ✅ Correct DCL — volatile + double-check pattern
    static Engine getInstance() {
        Engine local = instance;            // ✅ single volatile read cached in local var
        if (local == null) {
            synchronized (CorrectDoubleCheckedLocking.class) {
                local = instance;
                if (local == null) {
                    local    = new Engine();
                    instance = local;       // ✅ volatile write — full barrier, no reordering
                }
            }
        }
        return local;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                await(start);
                Engine e = getInstance();
                if (e.warmup != 42) {
                    throw new IllegalStateException("Partial construction! warmup=" + e.warmup);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("[CORRECT-DCL] Singleton safely observed by all 50 threads — warmup=42");
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
