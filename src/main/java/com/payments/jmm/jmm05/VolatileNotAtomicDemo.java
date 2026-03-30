package com.payments.jmm.jmm05;

import java.util.concurrent.*;

/**
 * JMM SECTION 5 — Visibility vs Atomicity
 *
 * PROBLEM: VolatileNotAtomicDemo
 * `volatile` guarantees VISIBILITY (every read sees the latest write) but NOT ATOMICITY.
 *
 * `volatileCount++` is three operations:
 *   1. READ  current value from main memory
 *   2. ADD   1
 *   3. WRITE result back to main memory
 *
 * Two threads can interleave:
 *   Thread A reads 5 → Thread B reads 5 → Thread A writes 6 → Thread B writes 6
 *   Both saw 5, both wrote 6 — one increment is LOST.
 *
 * volatile only ensures each individual READ or WRITE is visible.
 * It does NOT make the read-modify-write sequence atomic.
 *
 * Expected:  6 threads × 50,000 increments = 300,000
 * Actual:    typically 150,000 – 280,000 (lost updates due to races)
 */
public class VolatileNotAtomicDemo {

    private static final int THREADS    = 6;
    private static final int PER_THREAD = 50_000;

    // ❌ volatile gives visibility, NOT atomicity — i++ is a compound operation
    private static volatile int volatileCount = 0;

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                await(start);
                for (int j = 0; j < PER_THREAD; j++) {
                    volatileCount++; // ❌ READ-ADD-WRITE not atomic — updates are lost
                }
                done.countDown();
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();

        int expected = THREADS * PER_THREAD;
        System.out.println("[VOLATILE-NOT-ATOMIC] Expected=" + expected
                + "  Actual=" + volatileCount
                + "  Lost=" + (expected - volatileCount)
                + "  (volatile++ is NOT atomic)");
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
