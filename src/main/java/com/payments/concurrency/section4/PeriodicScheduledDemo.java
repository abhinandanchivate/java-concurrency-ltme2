package com.payments.concurrency.section4;

import java.time.Instant;
import java.util.concurrent.*;

/**
 * SECTION 4 — Bonus: Periodic work with ScheduledExecutorService
 *
 * scheduleAtFixedRate  → next run starts at fixed intervals regardless of task duration.
 *                        Risk: runs can overlap if the task takes longer than the period.
 *                        Use for: short polling tasks, health checks, rate-limit token refresh.
 *
 * scheduleWithFixedDelay → next run starts AFTER the previous run completes + delay.
 *                          No overlap possible.
 *                          Use for: retries, reconciliation, any task that must not overlap.
 *
 * IMPORTANT: If a scheduled task throws an unchecked exception, the scheduler silently
 * stops future executions of that task. Always catch and handle exceptions inside the task.
 */
public class PeriodicScheduledDemo {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "periodic-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Token bucket refresh every 1 second — short, reliable task → fixedRate is fine
        ScheduledFuture<?> tokenRefresh = scheduler.scheduleAtFixedRate(
                () -> System.out.println("[FIXED-RATE]  Token bucket refreshed at " + Instant.now()),
                0, 1, TimeUnit.SECONDS
        );

        // Reconciliation — must not overlap → fixedDelay
        ScheduledFuture<?> reconciliation = scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        System.out.println("[FIXED-DELAY] Reconciliation run at " + Instant.now());
                        Thread.sleep(300); // simulate some work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        // ✅ Always catch inside scheduled tasks — uncaught exception stops future runs
                        System.err.println("[FIXED-DELAY] Task error (suppressed): " + e);
                    }
                },
                0, 2, TimeUnit.SECONDS
        );

        Thread.sleep(5_000); // let it run for 5 seconds

        tokenRefresh.cancel(false);
        reconciliation.cancel(false);
        scheduler.shutdown();
        System.out.println("[SCHEDULED] Shutdown complete.");
    }
}
