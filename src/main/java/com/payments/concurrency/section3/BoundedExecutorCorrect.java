package com.payments.concurrency.section3;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SECTION 3 — Correct Implementation: Bounded pool with backpressure (CallerRunsPolicy)
 *
 * KEY DECISIONS:
 *   - ThreadPoolExecutor directly (not convenience factory) → full control over queue and policy.
 *   - ArrayBlockingQueue with fixed capacity → bounded memory; submission blocks when full.
 *   - CallerRunsPolicy → when queue is full, the submitting thread runs the task itself.
 *     This naturally slows down the producer without dropping work.
 *   - Named threads + UncaughtExceptionHandler → observable in production thread dumps.
 *
 * OVERLOAD POLICY GUIDE:
 *   CallerRunsPolicy  — slow the producer; use when losing work is unacceptable.
 *   AbortPolicy       — reject immediately; use when callers can handle RejectedExecutionException.
 *   DiscardPolicy     — silently drop; rarely appropriate for payment workloads.
 *
 * SEPARATE POOLS: Create one pool for fast validations, another for slow fraud/IO.
 * Shared pools let slow work starve fast work.
 */
public class BoundedExecutorCorrect {

    /**
     * Factory method for a production-ready bounded thread pool.
     *
     * @param name      used for thread naming — visible in thread dumps and JConsole
     * @param threads   fixed number of core/max threads
     * @param queueSize maximum waiting tasks before backpressure kicks in
     */
    public static ExecutorService boundedPool(String name, int threads, int queueSize) {
        BlockingQueue<Runnable> q = new ArrayBlockingQueue<>(queueSize);
        AtomicInteger seq = new AtomicInteger(1);

        //An object that creates new threads on demand.
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName(name + "-" + seq.getAndIncrement());
            t.setDaemon(false); // non-daemon: JVM waits for these threads on shutdown
            t.setUncaughtExceptionHandler((th, ex) ->
                    System.err.println("[ALERT] Uncaught exception on " + th.getName() + ": " + ex));
            return t;
        };

        return new ThreadPoolExecutor(
                threads, threads,           // core == max → fixed pool
                0L, TimeUnit.MILLISECONDS,  // keepAliveTime irrelevant for fixed pool
                q,
                tf,
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure: submitter runs task
        );
        
        // 1. threads : fixed pool size 
        // 2. threads
        // 3. oL : time after which extra thread die
        /*4. q: buffer between the producer and thread
        	5. tf : threadfactory which will provide the threads on demand (setup thread and properties for thread)
        	
        	. callerrunspolicy: task is submitted --> threads are avialable ==> if yes ==> 
        	// execute it 
        	/// // fi not : will it be queued 
        	/// // queue is full : 
        	
        	*/
    }

    public static void main(String[] args) throws InterruptedException {
        // Separate pools for different workload types — fast validations vs slow fraud
        ExecutorService validationPool = boundedPool("validation", 4, 500);
        ExecutorService fraudPool      = boundedPool("fraud-io",   8, 200);

        long start = System.currentTimeMillis();

        // Flood the validation pool — CallerRunsPolicy keeps memory bounded
        for (int i = 0; i < 5_000; i++) {
            validationPool.execute(() -> sleep(10)); // fast validation work
        }

        validationPool.shutdown();
        validationPool.awaitTermination(30, TimeUnit.SECONDS);
        fraudPool.shutdown();

        System.out.println("[BOUNDED] All tasks completed in "
                + (System.currentTimeMillis() - start) + "ms with bounded queue + backpressure.");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
    }
}
