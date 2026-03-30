package com.payments.concurrency.section4;

import java.util.concurrent.*;

/**
 * SECTION 4 — ScheduledExecutorService: Scheduling Retries and Periodic Work
 *
 * PROBLEM: SleepBackoffDemo sleep() inside a worker thread ties up that thread
 * for the entire delay duration. With 3 attempts × 500ms sleep each, one retry
 * loop occupies a thread for ~1.5 seconds doing nothing. Under concurrent load,
 * all threads can be stuck sleeping simultaneously, leaving the pool empty for
 * real work.
 */
public class SleepBackoffDemo {

	public static void main(String[] args) throws InterruptedException {
		ExecutorService workers = Executors.newFixedThreadPool(2);

		workers.submit(() -> {
			for (int attempt = 1; attempt <= 3; attempt++) {
				try {
					callDownstream();
					System.out.println("[SLEEP-RETRY] Success on attempt " + attempt);
					return;
				} catch (RuntimeException e) {
					System.out.println(
							"[SLEEP-RETRY] Attempt " + attempt + " failed — sleeping 500ms (thread wasted)...");
					sleep(500); // ❌ Thread blocked, can't do other work during this delay
				}
			}
			System.out.println("[SLEEP-RETRY] All attempts exhausted.");
		});

		workers.shutdown();
		workers.awaitTermination(5, TimeUnit.SECONDS);
		System.out.println("[SLEEP-RETRY] Done — see ScheduledRetryCorrect for the non-blocking version.");
	}

	static void callDownstream() {
		throw new RuntimeException("transient timeout");
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
