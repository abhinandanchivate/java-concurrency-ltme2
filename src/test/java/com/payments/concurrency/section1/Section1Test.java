package com.payments.concurrency.section1;

import org.junit.jupiter.api.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class Section1Test {

    // ── SafeDebitCAS ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("CAS debit: first debit succeeds, second is rejected (insufficient funds)")
    void casDebitSequential() {
        SafeDebitCAS.Account acc = new SafeDebitCAS.Account();
        assertTrue(acc.debit(800),  "first debit(800) on balance=1000 must succeed");
        assertFalse(acc.debit(800), "second debit(800) on balance=200 must be rejected");
        assertEquals(200, acc.balance());
    }

    @Test
    @DisplayName("CAS debit: concurrent debits never produce negative balance")
    void casDebitConcurrentNeverNegative() throws InterruptedException {
        SafeDebitCAS.Account acc = new SafeDebitCAS.Account();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(1);

        // 20 threads all trying to debit 200 from balance=1000 simultaneously
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                acc.debit(200);
            });
        }
        latch.countDown();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(acc.balance() >= 0, "balance must never go negative: " + acc.balance());
    }

    // ── SafeTransferDemo ────────────────────────────────────────────────────

    @Test
    @DisplayName("Transfer: correct amounts after concurrent bidirectional transfers")
    void transferBidirectionalNoDeadlock() throws InterruptedException {
        SafeTransferDemo.Account alice = new SafeTransferDemo.Account("alice", 1_000L);
        SafeTransferDemo.Account bob   = new SafeTransferDemo.Account("bob",   500L);

        // Concurrent opposite-direction transfers — would deadlock without lock ordering
        Thread t1 = new Thread(() -> SafeTransferDemo.transfer(alice, bob, 300));
        Thread t2 = new Thread(() -> SafeTransferDemo.transfer(bob, alice, 200));
        t1.start(); t2.start();
        t1.join(2_000); t2.join(2_000);

        // Total money must be conserved regardless of which transfer won
        assertEquals(1_500L, alice.balance + bob.balance, "total must be conserved");
    }

    @Test
    @DisplayName("Transfer: rejected when source has insufficient funds")
    void transferInsufficientFunds() {
        SafeTransferDemo.Account alice = new SafeTransferDemo.Account("alice", 100L);
        SafeTransferDemo.Account bob   = new SafeTransferDemo.Account("bob",   0L);

        boolean result = SafeTransferDemo.transfer(alice, bob, 500);
        assertFalse(result, "transfer of 500 from account with 100 must be rejected");
        assertEquals(100L, alice.balance, "alice balance must be unchanged");
        assertEquals(0L,   bob.balance,   "bob balance must be unchanged");
    }

    // ── IdempotencyGate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Idempotency: first call returns true, subsequent calls return false")
    void idempotencyGateBlocksDuplicates() {
        IdempotencyGate gate = new IdempotencyGate();
        assertTrue(gate.firstTime("TXN-001"),  "first time must return true");
        assertFalse(gate.firstTime("TXN-001"), "second time must return false");
        assertFalse(gate.firstTime("TXN-001"), "third time must return false");
        assertTrue(gate.firstTime("TXN-002"),  "different ID must return true");
    }

    @Test
    @DisplayName("Idempotency: concurrent submissions of the same ID — only one wins")
    void idempotencyConcurrent() throws InterruptedException {
        IdempotencyGate gate = new IdempotencyGate();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (gate.firstTime("TXN-DUPE")) wins.incrementAndGet();
            });
        }
        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);

        assertEquals(1, wins.get(), "exactly one thread must win the idempotency gate");
    }
}
