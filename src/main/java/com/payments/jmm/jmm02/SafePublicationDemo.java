package com.payments.jmm.jmm02;

import java.util.concurrent.*;

/**
 * JMM SECTION 2 — Correct: Safe Publication via Immutability + volatile
 *
 * WHY THIS WORKS — two guarantees combined:
 *
 * 1. final fields (JMM §17.5):
 *    Once a constructor completes, all final fields are guaranteed visible to any
 *    thread that obtains a reference to the object — even WITHOUT synchronization.
 *    The JMM inserts an implicit "freeze" action at the end of every constructor
 *    that writes final fields.
 *
 * 2. volatile reference:
 *    The write `safeShared = new ImmutableRisk(82)` is a volatile store.
 *    Any thread that subsequently reads `safeShared` is guaranteed to see the
 *    fully constructed object, including all its final fields.
 *
 * PUBLICATION STRATEGIES (choose one):
 *   a) volatile reference + final fields          ← this demo
 *   b) synchronized block (writer and reader)
 *   c) Hand off via BlockingQueue / ConcurrentHashMap
 *   d) Use java.util.concurrent.atomic references
 *
 * RULE OF THUMB: Immutable objects + clear publication boundary = simplest correct model.
 */
public class SafePublicationDemo {

    // ✅ All fields are final — guaranteed visible after construction
    static final class ImmutableRisk {
        final boolean approved;
        final int     score;
        final long    evaluatedAt;

        ImmutableRisk(int score) {
            this.score       = score;
            this.approved    = score >= 80;
            this.evaluatedAt = System.currentTimeMillis();
            // After constructor returns, all final fields are safely published
        }
    }

    // ✅ volatile reference — write happens-before any subsequent read
    private static volatile ImmutableRisk safeShared;

    public static void main(String[] args) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Writer thread
        pool.submit(() -> {
            await(start);
            // ✅ Constructor finishes first (final freeze), then volatile write publishes reference
            safeShared = new ImmutableRisk(82);
            System.out.println("[SAFE-PUB] Writer published ImmutableRisk");
        });

        // Reader thread — guaranteed to see complete, consistent state
        pool.submit(() -> {
            await(start);
            while (safeShared == null) Thread.onSpinWait(); // ✅ spinning on volatile field
            // safeShared != null implies all final fields are visible
            System.out.println("[SAFE-PUB] Reader: score=" + safeShared.score
                    + " approved=" + safeShared.approved
                    + "  (fully visible — immutable + volatile publication)");
        });

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.SECONDS);
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
