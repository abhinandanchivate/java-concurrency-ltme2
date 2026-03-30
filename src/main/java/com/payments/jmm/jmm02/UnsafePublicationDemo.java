package com.payments.jmm.jmm02;

import java.util.concurrent.*;

/**
 * JMM SECTION 2 — Safe Publication of Composite State
 *
 * PROBLEM: UnsafePublicationDemo
 * A MutableRisk object is constructed in one thread and its reference published
 * via a plain (non-volatile) field. Another thread reads the reference and accesses
 * the fields.
 *
 * What can go wrong under the JMM:
 *   - The JVM/CPU may reorder the field assignments AFTER the reference assignment.
 *   - The reader thread may observe the reference (non-null) but see default values
 *     (0, false, 0L) for score, approved, and evaluatedAt.
 *   - This is called "partially constructed object" visibility — a real JMM hazard.
 *
 * The bug is intermittent and JVM/hardware dependent — it may "work" on x86 but
 * fail on ARM, or only under JIT optimization after warmup.
 */
public class UnsafePublicationDemo {

    static final class MutableRisk {
        boolean approved;    // ❌ non-final — no publication guarantee
        int     score;
        long    evaluatedAt;
    }

    // ❌ Plain reference — no visibility guarantee for the object's fields
    private static MutableRisk unsafeShared;

    public static void main(String[] args) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Writer thread: constructs and publishes
        pool.submit(() -> {
            await(start);
            MutableRisk r = new MutableRisk();
            r.score       = 82;                           // ❌ may be reordered after reference publish
            r.approved    = true;
            r.evaluatedAt = System.currentTimeMillis();
            unsafeShared  = r;                            // ❌ plain write — no happens-before
            System.out.println("[UNSAFE-PUB] Writer published risk object");
        });

        // Reader thread: may see non-null reference but stale fields
        pool.submit(() -> {
            await(start);
            while (unsafeShared == null) Thread.onSpinWait(); // ❌ spin on plain field
            // By the time unsafeShared != null, fields may still have default values
            System.out.println("[UNSAFE-PUB] Reader: score=" + unsafeShared.score
                    + " approved=" + unsafeShared.approved
                    + "  (may be 0/false due to reordering — no happens-before)");
        });

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.SECONDS);
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
