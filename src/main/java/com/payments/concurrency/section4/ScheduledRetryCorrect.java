package com.payments.concurrency.section4;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * SECTION 4 — Correct Implementation: Exponential backoff retry without blocking workers
 *
 * HOW IT WORKS:
 *   - Each attempt calls op.get() which returns a CompletableFuture<T>.
 *   - On success → complete the output future.
 *   - On failure → schedule the NEXT attempt after an exponential delay using
 *     ScheduledExecutorService.schedule(). The worker thread is released immediately —
 *     it does NOT sleep.
 *   - The scheduler thread wakes up after the delay and kicks off the next attempt.
 *
 * DELAY FORMULA: baseDelay * 2^(attemptNo-1)
 *   Attempt 1 → 0ms (immediate), Attempt 2 → 100ms, Attempt 3 → 200ms, Attempt 4 → 400ms ...
 *
 * PRODUCTION ADDITIONS TO CONSIDER:
 *   - Jitter: add random offset to avoid thundering herd on simultaneous retries.
 *   - Max delay cap: don't let delay grow unboundedly.
 *   - Circuit breaker: stop retrying if error rate is high.
 *   - Pair with orTimeout() per attempt so a single slow attempt doesn't hang the retry loop.
 */
public class ScheduledRetryCorrect {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "retry-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * Retries {@code op} up to {@code maxAttempts} times with exponential backoff.
     *
     * @param op          supplier that returns a new CompletableFuture on each call
     * @param maxAttempts total attempts (including the first)
     * @param baseDelay   delay before the second attempt; doubles each time
     * @return            a future that completes when op succeeds, or fails after all attempts
     */
    public <T> CompletableFuture<T> retryAsync(
            Supplier<CompletableFuture<T>> op,
            int maxAttempts,
            Duration baseDelay) {

        CompletableFuture<T> output = new CompletableFuture<>();
        attempt(op, maxAttempts, baseDelay, 1, output);
        return output;
    }

    private <T> void attempt(
            Supplier<CompletableFuture<T>> op,
            int maxAttempts,
            Duration baseDelay,
            int attemptNo,
            CompletableFuture<T> output) {

        System.out.println("[RETRY] Attempt " + attemptNo + "/" + maxAttempts);

        op.get().whenComplete((val, ex) -> {
            if (ex == null) {
                output.complete(val);    // success — done
                return;
            }

            if (attemptNo >= maxAttempts) {
                output.completeExceptionally(ex); // exhausted — propagate last error
                return;
            }

            // Exponential backoff delay — worker thread is NOT held during this wait
            long delayMs = (long) (baseDelay.toMillis() * Math.pow(2, attemptNo - 1));
            System.out.println("[RETRY] Attempt " + attemptNo + " failed ("
                    + ex.getCause().getClass().getSimpleName()
                    + "), retrying in " + delayMs + "ms (thread released)...");

            // Schedule next attempt after delay — no thread sleeps, scheduler just queues it
            scheduler.schedule(
                    () -> attempt(op, maxAttempts, baseDelay, attemptNo + 1, output),
                    delayMs,
                    TimeUnit.MILLISECONDS
            );
        });
    }

    public void shutdown() { scheduler.shutdown(); }

    // ── Demo ─────────────────────────────────────────────────────────────────

    static int callCount = 0;

    /** Simulates a call that fails twice then succeeds on the third attempt. */
    static CompletableFuture<String> unreliableCall() {
        return CompletableFuture.supplyAsync(() -> {
            callCount++;
            if (callCount < 3) throw new RuntimeException("transient network error");
            return "CBS_OK";
        });
    }

    public static void main(String[] args) throws Exception {
        ScheduledRetryCorrect retrier = new ScheduledRetryCorrect();

        CompletableFuture<String> result = retrier.retryAsync(
                ScheduledRetryCorrect::unreliableCall,
                5,
                Duration.ofMillis(100)
        );

        System.out.println("[RETRY] Final result: " + result.join());
        retrier.shutdown();
    }
}
