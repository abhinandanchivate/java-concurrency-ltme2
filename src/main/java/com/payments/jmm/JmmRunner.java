package com.payments.jmm;

import com.payments.jmm.jmm01.*;
import com.payments.jmm.jmm02.*;
import com.payments.jmm.jmm03.*;
import com.payments.jmm.jmm04.*;
import com.payments.jmm.jmm05.*;
import com.payments.jmm.jmm06.*;
import com.payments.jmm.jmm07.*;
import com.payments.jmm.jmm08.*;

/**
 * JMM Runner — executes all 8 Java Memory Model sections sequentially.
 * Each section shows the WRONG approach first, then the CORRECT approach.
 */
public class JmmRunner {

    public static void main(String[] args) throws Exception {
        run("JMM-01A — Plain Flag (No Visibility — WRONG)",         () -> PlainFlagDemo.main(null));
        run("JMM-01B — Volatile Flag (Happens-Before — CORRECT)",   () -> VolatileFlagDemo.main(null));

        run("JMM-02A — Unsafe Publication (Mutable — WRONG)",       () -> UnsafePublicationDemo.main(null));
        run("JMM-02B — Safe Publication (Immutable + volatile)",     () -> SafePublicationDemo.main(null));

        run("JMM-03A — Broken DCL (No volatile — WRONG)",           () -> BrokenDoubleCheckedLocking.main(null));
        run("JMM-03B — Correct DCL (volatile — CORRECT)",           () -> CorrectDoubleCheckedLocking.main(null));

        run("JMM-04A — Unsynchronized Read (WRONG)",                () -> UnsynchronizedReadDemo.main(null));
        run("JMM-04B — Locks + Atomics (CORRECT)",                  () -> LocksAndAtomicsCorrect.main(null));

        run("JMM-05A — volatile++ Not Atomic (WRONG)",              () -> VolatileNotAtomicDemo.main(null));
        run("JMM-05B — AtomicInteger.incrementAndGet (CORRECT)",    () -> AtomicCounterCorrect.main(null));

        run("JMM-06A — ThreadLocal Leak in Pool (WRONG)",           () -> ThreadLocalLeakDemo.main(null));
        run("JMM-06B — ThreadLocal with finally remove (CORRECT)",  () -> ThreadLocalCorrect.main(null));

        run("JMM-07A — Unbounded Static Cache Leak (WRONG)",        () -> StaticCacheLeakDemo.main(null));
        run("JMM-07B — Bounded Cache with Eviction (CORRECT)",      () -> BoundedCacheDemo.main(null));

        run("JMM-08A — High Alloc + Retention = GC Pressure",       () -> GcPressureDemo.main(null));
        run("JMM-08B — Controlled Allocation (CORRECT)",            () -> GcControlledDemo.main(null));
    }

    static void run(String title, ThrowingRunnable r) throws Exception {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("  " + title);
        System.out.println("═".repeat(70));
        r.run();
        Thread.sleep(150);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
