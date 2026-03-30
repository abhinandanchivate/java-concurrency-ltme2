package com.payments.jmm.jmm08;

import java.util.*;

/**
 * JMM SECTION 8 — Heap and GC Behavior
 *
 * PROBLEM: GcPressureDemo
 * Two separate GC problems demonstrated together:
 *
 * 1. HIGH ALLOCATION RATE (minor GC pressure):
 *    `new byte[32 * 1024]` inside a tight loop allocates 32KB per iteration.
 *    These objects are short-lived (not retained) — they become eligible for GC
 *    immediately. The GC must run frequently to reclaim Eden space.
 *    Result: high minor GC frequency, CPU time spent in GC instead of application work.
 *
 * 2. UNBOUNDED RETENTION (major GC / OOM pressure):
 *    Every 200 iterations, a 512KB object is added to `retained` list.
 *    The list is cleared at size > 50, but the pattern still creates pressure.
 *    In a real service, retention without a bound leads to heap exhaustion.
 *
 * GC CANNOT COMPENSATE FOR:
 *   - Objects that are still reachable (retained)
 *   - Allocation rate that exceeds reclamation rate
 *   Increasing -Xmx only delays OOM — it does not fix the root cause.
 */
public class GcPressureDemo {

    public static void main(String[] args) {
        List<byte[]> retained = new ArrayList<>();
        long totalAllocated = 0;

        for (int i = 0; i < 50_000; i++) {
            // ❌ High allocation rate — GC must run often to reclaim these short-lived objects
            @SuppressWarnings("unused")
            byte[] throwaway = new byte[32 * 1024]; // allocated then immediately abandoned
            totalAllocated += 32 * 1024;

            // ❌ Periodic large retention — holds 512KB per entry until cleared
            if (i % 200 == 0) retained.add(new byte[512 * 1024]);

            // Clear when over 50 — but still causes GC pressure before each clear
            if (retained.size() > 50) retained.clear();
        }

        System.out.println("[GC-PRESSURE] Done"
                + "  totalAllocated~=" + (totalAllocated / (1024 * 1024)) + "MB"
                + "  (high alloc rate + periodic retention = GC stress)");
    }
}
