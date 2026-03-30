package com.payments.jmm.jmm04;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JMM SECTION 4 — Locks, Atomics, and Visibility Boundaries
 *
 * PROBLEM: UnsynchronizedReadDemo
 * The writer acquires the lock and updates both fields atomically.
 * But the READER does NOT acquire the lock — it reads directly.
 *
 * JMM rule: lock release happens-before the NEXT lock acquisition on the same monitor.
 * If the reader never acquires the lock, it is NOT in the happens-before chain.
 * It may see a stale version, a torn read (partial update), or an inconsistent
 * combination of version and value from different points in time.
 *
 * "Synchronizing writers but not readers does not create a visibility guarantee."
 */
public class UnsynchronizedReadDemo {

    static final class Snapshot {
        int    version;
        String value;
    }

    private static final Snapshot     snapshot = new Snapshot();
    private static final ReentrantLock lock    = new ReentrantLock();

    static void update(String v) {
        lock.lock();
        try {
            snapshot.version++;    // ✅ writer holds lock
            snapshot.value = v;
        } finally {
            lock.unlock();
        }
    }

    // ❌ Reader does NOT acquire lock — not in happens-before chain with writer
    static String readUnsafe() {
        return snapshot.version + ":" + snapshot.value; // ❌ may see torn/stale state
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch done = new CountDownLatch(200);

        // Writers — all properly synchronized
        for (int i = 0; i < 100; i++) {
            final String val = "V" + i;
            pool.submit(() -> { update(val); done.countDown(); });
        }

        // Readers — NOT synchronized → may observe inconsistent state
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                String snap = readUnsafe(); // ❌ no lock — torn read possible
                System.out.println("[UNSYNC-READ] Read=" + snap + " (may be inconsistent)");
                done.countDown();
            });
        }

        done.await();
        pool.shutdown();
        System.out.println("[UNSYNC-READ] Done — readers without locks are NOT safe under JMM");
    }
}
