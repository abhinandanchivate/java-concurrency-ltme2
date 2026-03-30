package com.payments.concurrency.section1;

import java.util.concurrent.*;

/**
 * SECTION 1 — Concurrent Transaction Validation
 *
 * PROBLEM: UnsafeDebitDemo
 * Two threads read the same balance before either writes. Both see balance >= amt,
 * both pass the check, and both subtract — leaving a negative balance.
 *
 * The sleep(10) call widens the race window so the bug is reliably reproducible.
 */
public class UnsafeDebitDemo {

    static class Account {
        long balance = 1000;
// 
        void debit(long amt) {
            if (balance >= amt) {
                sleep(10); // widen race window — never do this in production
                balance -= amt; // NOT atomic with the check above
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Account acc = new Account();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        pool.submit(() -> acc.debit(800));
        pool.submit(() -> acc.debit(800));

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        // Expected: one debit should have been rejected — balance should be 200.
        // Actual:   both debits succeed — balance can go to -600 (race condition).
        System.out.println("[UNSAFE] Final balance=" + acc.balance
                + "  <-- may be negative due to race condition");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
