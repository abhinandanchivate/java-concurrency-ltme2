package com.payments.jmm.jmm01;

import java.util.concurrent.*;

/**
 * JMM SECTION 1 — Visibility of a Simple State Flag
 *
 * PROBLEM: PlainFlagDemo
 * The `approved` field is a plain boolean — no visibility guarantee across threads.
 *
 * What can go wrong under the JMM:
 *   - The JIT compiler may cache `approved` in a CPU register for the settler thread.
 *   - The CPU may reorder the write on the validator side.
 *   - The settler thread reads a stale value (false) indefinitely — a liveness failure.
 *
 * This is NOT a timing issue. Even if the validator sleeps and gives the setter
 * "plenty of time", the JMM does NOT guarantee the settler ever sees the write
 * without a happens-before relationship.
 *
 * "It usually works on my machine" is not a correctness argument under the JMM.
 */
public class PlainFlagDemo {

    // ❌ No visibility guarantee — write on one thread may never be seen by another
    static class PlainFlag {
        private boolean approved;

        public boolean isApproved() { return approved; }
        public void approve()       { approved = true; }
    }

    public static void main(String[] args) throws Exception {
        PlainFlag flag = new PlainFlag();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Validator thread: approves after 50ms
        pool.submit(() -> {
            await(start);
            sleep(50);
            flag.approve();
            System.out.println("[PLAIN-FLAG] Validator: approved=true written");
        });

        // Settler thread: spins waiting for approval — may NEVER see it
        pool.submit(() -> {
            await(start);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
            while (!flag.isApproved() && System.nanoTime() < deadline) {
                Thread.onSpinWait(); // ❌ spin may read stale cached value forever
            }
            System.out.println("[PLAIN-FLAG] Settler: observed approved=" + flag.isApproved()
                    + "  (may be false even though write happened — no happens-before)");
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
