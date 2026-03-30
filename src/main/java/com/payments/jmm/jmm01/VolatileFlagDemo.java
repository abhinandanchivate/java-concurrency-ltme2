package com.payments.jmm.jmm01;

import java.util.concurrent.*;

/**
 * JMM SECTION 1 — Correct: Visibility with volatile (Happens-Before)
 *
 * WHY volatile WORKS:
 *   JMM rule: a write to a volatile variable happens-before every subsequent read
 *   of that same variable.
 *
 *   This means:
 *   - The write `approved = true` is immediately flushed to main memory.
 *   - Any thread that reads `approved` after the write is guaranteed to observe `true`.
 *   - The compiler and CPU cannot reorder writes past a volatile store.
 *
 * WHEN volatile IS ENOUGH:
 *   - Single writer, one or more readers (simple state flag / publication sentinel).
 *   - The flag itself is the only shared state — no compound check-then-act.
 *
 * WHEN volatile IS NOT ENOUGH:
 *   - Read-modify-write (i++ needs AtomicInteger — see JMM05).
 *   - Multi-field invariants (use locks — see JMM04).
 */
public class VolatileFlagDemo {

    // ✅ volatile establishes happens-before: write → all subsequent reads
    static class VolatileFlag {
        private volatile boolean approved;

        public boolean isApproved() { return approved; }
        public void approve()       { approved = true; } // flushed to main memory immediately
    }

    public static void main(String[] args) throws Exception {
        VolatileFlag flag = new VolatileFlag();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Validator thread: approves after 50ms
        pool.submit(() -> {
            await(start);
            sleep(50);
            flag.approve(); // ✅ volatile write — visible to all threads immediately
            System.out.println("[VOLATILE-FLAG] Validator: approved=true written");
        });

        // Settler thread: guaranteed to eventually observe the write
        pool.submit(() -> {
            await(start);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
            while (!flag.isApproved() && System.nanoTime() < deadline) {
                Thread.onSpinWait(); // ✅ each read goes to main memory — no stale cache
            }
            System.out.println("[VOLATILE-FLAG] Settler: observed approved=" + flag.isApproved()
                    + "  (guaranteed visible via happens-before)");
        });

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.SECONDS);
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
