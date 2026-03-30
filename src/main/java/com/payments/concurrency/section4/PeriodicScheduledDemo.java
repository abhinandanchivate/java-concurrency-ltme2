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
 * 
 * 
 * 
 * 
 * CBS → Kafka → Consumer → Queue
            ↓
     Batch Scheduler (5 sec)
            ↓
     Reconciliation Engine
            ↓
     Bank API / PSP API
            ↓
     DB (status update)
            ↓
     Alert system (if mismatch)
     
     class Transaction {
    String txnId;
    double amount;
    String status; // PENDING, MATCHED, MISMATCH
    BlockingQueue<Transaction> queue = new LinkedBlockingQueue<>(1000);
    ExecutorService producerPool = Executors.newFixedThreadPool(2);

producerPool.submit(() -> {
    for (int i = 1; i <= 50; i++) {
        Transaction tx = new Transaction();
        tx.txnId = "TXN-" + i;
        tx.amount = Math.random() * 1000;
        tx.status = "PENDING";

        queue.offer(tx);
        System.out.println("[PRODUCER] Added " + tx.txnId);

        sleep(100);
        
        
    }
});


ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

scheduler.scheduleWithFixedDelay(() -> {
    List<Transaction> batch = new ArrayList<>();

    queue.drainTo(batch, 10); // batch size = 10

    if (batch.isEmpty()) return;

    System.out.println("\n[BATCH] Processing " + batch.size() + " transactions");

    processBatch(batch);

}, 0, 5, TimeUnit.SECONDS)

void processBatch(List<Transaction> batch) {

    for (Transaction tx : batch) {
        try {
            boolean matched = callBankAPI(tx);

            if (matched) {
                tx.status = "MATCHED";
            } else {
                tx.status = "MISMATCH";
            }

            saveToDB(tx);

        } catch (Exception e) {
            System.err.println("[ERROR] " + tx.txnId + " failed: " + e.getMessage());
        }
    }
}
boolean callBankAPI(Transaction tx) {
    // simulate network delay
    sleep(50);

    return Math.random() > 0.2; // 80% success
}

void saveToDB(Transaction tx) {
    System.out.println("[DB] " + tx.txnId + " → " + tx.status);
}
}

Producer → adds TXNs to queue
        ↓
Scheduler (every 5 sec)
        ↓
Drain 10 TXNs → batch
        ↓
Processor → call bank API
        ↓
Update DB
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
