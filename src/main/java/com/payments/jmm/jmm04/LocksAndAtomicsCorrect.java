package com.payments.jmm.jmm04;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JMM SECTION 4 — Correct: Locks for Composite State, Atomics for Counters
 *
 * TWO TOOLS, TWO PURPOSES:
 *
 * ReentrantLock (or synchronized):
 *   - Protects COMPOSITE mutable state (multiple related fields that must change together).
 *   - Lock release happens-before the next lock acquisition on the same monitor.
 *   - BOTH writers AND readers must use the same lock — visibility is only guaranteed
 *     for threads inside the happens-before chain.
 *
 * AtomicInteger / AtomicLong / AtomicReference:
 *   - Protects a SINGLE variable with lock-free CAS operations.
 *   - Correct for counters, sequence numbers, simple flags.
 *   - NOT correct for multi-field invariants (a CAS on one field doesn't protect another).
 *
 * COMMON MISTAKE: using AtomicInteger for a counter that also has an associated
 * String or timestamp field — those other fields are not protected by the atomic.
 */
public class LocksAndAtomicsCorrect {

    static final class Snapshot {
        int    version;
        String value;
    }

    private static final Snapshot      snapshot = new Snapshot();
    private static final ReentrantLock lock     = new ReentrantLock();

    // ✅ Separate atomic counter — no relationship with snapshot fields
    private static final AtomicInteger requests = new AtomicInteger();

    // ✅ Writer holds lock for entire multi-field update
    static void update(String v) {
        lock.lock();
        try {
            snapshot.version++;
            snapshot.value = v;
        } finally {
            lock.unlock();
        }
    }

    // ✅ Reader also acquires the SAME lock — enters happens-before chain
    static String read() {
        lock.lock();
        try {
            return snapshot.version + ":" + snapshot.value; // consistent snapshot guaranteed
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch done = new CountDownLatch(200);

        for (int i = 0; i < 100; i++) {
            final String val = "V" + i;
            pool.submit(() -> {
                update(val);
                requests.incrementAndGet(); // ✅ atomic — safe for single-variable update
                done.countDown();
            });
        }

        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                String snap = read(); // ✅ synchronized read — consistent with writes
                System.out.println("[LOCKS-ATOMICS] Read=" + snap);
                done.countDown();
            });
        }

        done.await();
        pool.shutdown();
        System.out.println("[LOCKS-ATOMICS] Total requests=" + requests.get());
    }
}
