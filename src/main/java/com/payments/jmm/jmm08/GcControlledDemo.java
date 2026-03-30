package com.payments.jmm.jmm08;

import java.util.*;

/**
 * JMM SECTION 8 — Correct: Controlled Allocation and Bounded Retention
 *
 * TWO FIXES applied:
 *
 * 1. REDUCE ALLOCATION RATE — reuse buffers:
 *    Instead of allocating a new byte[] on every iteration, allocate once and reuse.
 *    This eliminates most of the GC pressure from short-lived objects.
 *    Pattern: pre-allocate → reuse → release when done.
 *
 * 2. BOUND RETENTION — cap the retained list and clear promptly:
 *    Use a fixed-size structure. Release references as soon as the data is no longer needed.
 *    Do not hold collections "just in case" — release eagerly.
 *
 * GC TUNING GUIDE (apply AFTER fixing allocation/retention):
 *   -XX:+UseG1GC        — good default for most server workloads (Java 9+ default)
 *   -XX:+UseZGC         — ultra-low pause (sub-millisecond), Java 15+ production-ready
 *   -Xms == -Xmx        — avoids heap resize pauses in production
 *   -XX:MaxGCPauseMillis=200 (G1) — GC tries to meet the pause target
 *
 * REMEMBER: GC tuning is the last resort. Fix allocation and retention first.
 * A GC algorithm cannot collect objects that are still referenced.
 */
public class GcControlledDemo {

    private static final int MAX_RETAINED = 20; // strict bound

    public static void main(String[] args) {
        // ✅ Allocate buffer ONCE, reuse across iterations
        byte[] reusableBuffer = new byte[32 * 1024];

        // ✅ Fixed-capacity retained list — bounded memory at all times
        List<byte[]> retained = new ArrayList<>(MAX_RETAINED);

        for (int i = 0; i < 50_000; i++) {
            // ✅ Reuse buffer — zero allocation per iteration in steady state
            Arrays.fill(reusableBuffer, (byte) (i & 0xFF)); // simulate use

            // ✅ Only retain when needed, with a strict cap
            if (i % 2_000 == 0 && retained.size() < MAX_RETAINED) {
                retained.add(new byte[512 * 1024]);
            }

            // ✅ Release promptly when done — don't wait for the list to grow large
            if (retained.size() >= MAX_RETAINED) {
                retained.clear(); // release references → GC can reclaim
            }
        }

        System.out.println("[GC-CONTROLLED] Done"
                + "  reusableBuffer reused 50,000 times (0 extra allocs)"
                + "  maxRetained=" + MAX_RETAINED + " entries"
                + "  (minimal GC pressure — allocation and retention both controlled)");
    }
}
