package com.payments.jmm.jmm06;

import java.util.concurrent.*;

/**
 * JMM SECTION 6 — ThreadLocal Retention in Thread Pools
 *
 * PROBLEM: ThreadLocalLeakDemo
 * ThreadLocal values are attached to Thread objects, not to tasks.
 * In a thread pool, threads are REUSED across many requests.
 *
 * If TL.remove() is not called, the value set for request N stays on the thread
 * and leaks into request N+1, N+2, ... until the thread is destroyed (which in
 * a fixed pool may be NEVER during the lifetime of the service).
 *
 * Two problems caused:
 *
 * 1. DATA LEAK / CORRECTNESS BUG:
 *    The next request on the same thread reads stale data from a previous request.
 *    Sensitive data (user ID, tenant context, session token) crosses request boundaries.
 *
 * 2. MEMORY LEAK:
 *    Each retained value holds a reference to its data. With 2MB per thread and 100
 *    pooled threads, that's 200MB permanently occupied — never eligible for GC.
 */
public class ThreadLocalLeakDemo {

    // Simulates a per-request context (e.g., user ID, tenant, correlation ID)
    static final ThreadLocal<byte[]> REQUEST_CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i = 0; i < 20; i++) {
            final int reqId = i;
            pool.submit(() -> {
                // ❌ Set but never removed — value survives on the thread after task ends
                REQUEST_CONTEXT.set(new byte[2_000_000]); // 2MB context per request

                System.out.println("[TL-LEAK] Request-" + reqId
                        + " on thread=" + Thread.currentThread().getName()
                        + " — context set but NOT removed");

                // ❌ No TL.remove() — next task on this thread inherits this 2MB allocation
                // After 20 requests: 4 threads × 2MB = 8MB permanently retained
            });
        }

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("[TL-LEAK] Done — ThreadLocal values still live on all 4 pooled threads");
    }
}
