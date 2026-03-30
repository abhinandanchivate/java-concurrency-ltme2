package com.payments.jmm.jmm06;

import java.util.concurrent.*;

/**
 * JMM SECTION 6 — Correct: ThreadLocal with Guaranteed Removal
 *
 * THE RULE: Always call TL.remove() in a finally block inside the same task that
 * called TL.set(). This guarantees cleanup even if the task throws an exception.
 *
 * WHY finally IS MANDATORY:
 *   If the task body throws before reaching remove(), the value leaks anyway.
 *   Only a finally block guarantees execution regardless of the outcome.
 *
 * PATTERN: set → try { use } finally { remove }
 *
 * ALTERNATIVES TO CONSIDER:
 *   a) Pass context as method parameters — explicit, no hidden state, recommended.
 *   b) ScopedValue (Java 21 preview) — structured, inherited by child threads,
 *      automatically cleaned up when the scope exits.
 *   c) MDC (Mapped Diagnostic Context) in logging frameworks — uses ThreadLocal
 *      internally but provides explicit push/pop lifecycle.
 *
 * PRODUCTION NOTE: Consider wrapping your ExecutorService to set/clear context
 * automatically in a custom ThreadFactory or task wrapper — removes the burden
 * from each call site.
 */
public class ThreadLocalCorrect {

    static final ThreadLocal<byte[]> REQUEST_CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i = 0; i < 20; i++) {
            final int reqId = i;
            pool.submit(() -> {
                REQUEST_CONTEXT.set(new byte[2_000_000]); // 2MB per request
                try {
                    // Do request work — context is available via REQUEST_CONTEXT.get()
                    System.out.println("[TL-CORRECT] Request-" + reqId
                            + " on thread=" + Thread.currentThread().getName()
                            + " — context set, will be removed in finally");

                    // simulate work that might throw
                    if (reqId % 7 == 0) throw new RuntimeException("simulated failure");

                } finally {
                    REQUEST_CONTEXT.remove(); // ✅ ALWAYS runs — even if task threw
                    // Thread is now clean for the next request
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("[TL-CORRECT] Done — all ThreadLocal values removed; no memory leak");
    }
}
