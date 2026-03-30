package com.payments.jmm.jmm07;

import java.util.*;

/**
 * JMM SECTION 7 — Detecting Memory Leaks with Profilers
 *
 * PROBLEM: StaticCacheLeakDemo
 * A static HashMap grows without bound. Static fields are GC roots — objects
 * referenced from static fields are ALWAYS reachable and can NEVER be collected.
 *
 * This is the most common "memory leak" pattern in Java:
 *   - Not a true leak (JVM doesn't lose track of the memory)
 *   - A RETENTION issue: objects are reachable but no longer needed
 *
 * Common retention culprits in production:
 *   a) Static Map/List caches without eviction policy
 *   b) Listeners/callbacks registered but never deregistered
 *   c) ThreadLocal values not removed in thread pools (see JMM06)
 *   d) Inner class instances holding implicit reference to outer class
 *
 * Symptoms: heap grows steadily over time, OOM after hours/days of uptime,
 * GC runs more frequently but reclaims less each time.
 *
 * NOTE: This demo is intentionally commented out of the main runner to avoid
 * causing an actual OOM in the process. Uncomment to observe the behavior.
 */
public class StaticCacheLeakDemo {

    // ❌ Static map — GC root, never eligible for collection
    private static final Map<String, byte[]> CACHE = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("[LEAK-DEMO] Starting unbounded cache fill — will OOM eventually");
        System.out.println("[LEAK-DEMO] (Comment this demo out in production demos to avoid OOM)");

        int i = 0;
        // In a real app this would be a request/event loop — each iteration adds more
        while (i < 200) { // capped at 200 iterations here for safety
            CACHE.put("TXN-" + i, new byte[512 * 1024]); // 512KB per entry
            i++;
            if (i % 50 == 0) {
                System.out.println("[LEAK-DEMO] Entries=" + i
                        + "  approx heap used=" + (i * 512 / 1024) + "MB"
                        + "  (never reclaimed — static reference holds everything)");
            }
        }
        System.out.println("[LEAK-DEMO] Done — " + CACHE.size()
                + " entries retained in static map. None are GC-eligible.");
    }
}
