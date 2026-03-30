package com.payments.jmm.jmm07;

import java.util.*;

/**
 * JMM SECTION 7 — Correct: Bounded Cache with Eviction
 *
 * FIX: Replace the unbounded static map with a size-bounded cache that evicts
 * the eldest entry when capacity is exceeded.
 *
 * LinkedHashMap with accessOrder=false and removeEldestEntry override is the
 * simplest built-in eviction cache in the JDK. No external library needed.
 *
 * PRODUCTION-GRADE ALTERNATIVES:
 *   a) Caffeine (recommended) — expiry by time or size, async loading, stats.
 *   b) Guava Cache — similar, slightly less configurable than Caffeine.
 *   c) ConcurrentHashMap + manual eviction thread — more control, more code.
 *   d) Redis/Memcached — for distributed caches shared across instances.
 *
 * PROFILING TO FIND LEAKS:
 *   1. Take heap dumps: `jcmd <pid> GC.heap_dump /tmp/heap.hprof`
 *   2. Open in VisualVM, Eclipse MAT, or IntelliJ heap analyzer.
 *   3. Look for: largest retained sets, objects with unexpected GC root paths.
 *   4. Sort by "retained heap" to find what's holding the most memory.
 *   GC tuning (-Xmx, -XX:+UseG1GC) cannot fix a retention problem — fix the root cause.
 */
public class BoundedCacheDemo {

    private static final int MAX_ENTRIES = 100;

    // ✅ Bounded LRU cache — evicts eldest entry when over capacity
    private static final Map<String, byte[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_ENTRIES; // ✅ evict when over limit
                }
            }
    );

    public static void main(String[] args) {
        System.out.println("[BOUNDED-CACHE] Filling cache — capped at " + MAX_ENTRIES + " entries");

        for (int i = 0; i < 300; i++) {
            CACHE.put("TXN-" + i, new byte[512 * 1024]); // 512KB per entry

            if (i % 50 == 0) {
                System.out.println("[BOUNDED-CACHE] Added entry " + i
                        + "  cache size=" + CACHE.size()
                        + " (never exceeds " + MAX_ENTRIES + ")");
            }
        }

        System.out.println("[BOUNDED-CACHE] Final size=" + CACHE.size()
                + "  (bounded — old entries evicted, heap stays stable)");
    }
}
