package com.payments.jmm.jmm03;

import java.util.concurrent.*;

/**
 * JMM SECTION 3 — Lazy Initialization and Instruction Reordering
 *
 * PROBLEM: BrokenDoubleCheckedLocking
 * Double-checked locking WITHOUT volatile is a classic JMM bug.
 *
 * The broken sequence (from JVM's perspective):
 *   Thread A inside synchronized block:
 *     1. Allocate memory for Engine
 *     2. Assign reference to `instance`       ← reordering allows this BEFORE step 3
 *     3. Execute Engine() constructor (warmup = 42)
 *
 *   Thread B reads `instance` AFTER step 2 but BEFORE step 3:
 *     - instance != null  → skips synchronized block
 *     - reads instance.warmup → sees 0 (default) instead of 42
 *     - throws IllegalStateException
 *
 * This is a real JVM/JIT reordering that happens in production under load.
 * It cannot be reproduced reliably but the JMM allows it.
 */
public class BrokenDoubleCheckedLocking {

    // ❌ NOT volatile — reference assignment can be reordered before constructor body
    private static Engine instance;

    static final class Engine {
        final int warmup;
        Engine() { warmup = 42; }
    }

    // ❌ Broken DCL — instance can be seen non-null but partially constructed
    static Engine getInstance() {
        if (instance == null) {                          // ❌ first check — no lock
            synchronized (BrokenDoubleCheckedLocking.class) {
                if (instance == null) {
                    instance = new Engine();             // ❌ write may be reordered
                }
            }
        }
        return instance;                                 // ❌ may return partially constructed object
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                await(start);
                Engine e = getInstance();
                if (e.warmup != 42) {
                    System.out.println("[BROKEN-DCL] Partial construction observed! warmup=" + e.warmup);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("[BROKEN-DCL] Finished — bug may not manifest on x86 but IS allowed by JMM");
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
