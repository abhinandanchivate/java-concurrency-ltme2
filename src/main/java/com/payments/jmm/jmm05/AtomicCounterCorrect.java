package com.payments.jmm.jmm05;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMM SECTION 5 — Correct: AtomicInteger for Read-Modify-Write Atomicity
 *
 * WHY AtomicInteger WORKS:
 *   incrementAndGet() uses a hardware CAS (Compare-And-Swap) instruction.
 *   It atomically reads the current value, adds 1, and stores the result — all
 *   as a single uninterruptible CPU instruction. No other thread can interleave.
 *
 *   If the CAS fails (another thread changed the value between read and write),
 *   it retries automatically in a spin loop — no lock needed.
 *
 * VISIBILITY vs ATOMICITY — summary:
 * ┌─────────────────┬────────────┬───────────┐
 * │ Mechanism       │ Visibility │ Atomicity │
 * ├─────────────────┼────────────┼───────────┤
 * │ plain field     │ ❌         │ ❌        │
 * │ volatile        │ ✅         │ ❌ (i++)  │
 * │ AtomicInteger   │ ✅         │ ✅ (i++)  │
 * │ synchronized    │ ✅         │ ✅        │
 * └─────────────────┴────────────┴───────────┘
 *
 * USE AtomicInteger for: counters, sequence generators, simple numeric state.
 * USE locks for: multi-field invariants, compound check-then-act operations.
 */
public class AtomicCounterCorrect {

    private static final int THREADS    = 6;
    private static final int PER_THREAD = 50_000;

    // ✅ AtomicInteger — CAS-based, lock-free, atomic read-modify-write
    private static final AtomicInteger atomicCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                await(start);
                for (int j = 0; j < PER_THREAD; j++) {
                    atomicCount.incrementAndGet(); // ✅ atomic CAS — no lost updates
                }
                done.countDown();
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();

        int expected = THREADS * PER_THREAD;
        int actual   = atomicCount.get();
        System.out.println("[ATOMIC-COUNTER] Expected=" + expected
                + "  Actual=" + actual
                + "  Match=" + (expected == actual)
                + "  (AtomicInteger.incrementAndGet() is always exact)");
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
