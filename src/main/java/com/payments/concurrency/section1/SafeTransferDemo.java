package com.payments.concurrency.section1;

import java.util.concurrent.locks.ReentrantLock;

/**
 * SECTION 1 — Correct Implementation B: Transfer deadlock prevention with lock ordering
 *
 * PROBLEM: A→B and B→A transfers running concurrently can deadlock:
 *   Thread-1 holds lock(A), waits for lock(B)
 *   Thread-2 holds lock(B), waits for lock(A)  → circular wait → deadlock
 *
 * FIX: Always acquire locks in a consistent global order (alphabetical by ID here).
 * Both threads then contend for the same "first" lock, eliminating circular waits.
 *
 * WHEN TO USE: Any multi-resource update where multiple fields/accounts must change atomically.
 */
public class SafeTransferDemo {

    static class Account {
        final String id;
        final ReentrantLock lock = new ReentrantLock();
        long balance;

        Account(String id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }
    // JVM --> OS--> scheduler-> another thread

    /**
     * Transfers {@code amt} from {@code from} to {@code to} with deadlock-safe lock ordering.
     *
     * Lock ordering rule: always lock the account whose id is lexicographically smaller first.
     * This guarantees both threads agree on which lock to acquire first — no circular waits.
     */
    static boolean transfer(Account from, Account to, long amt) {
        // Determine consistent lock order
        Account first  = from.id.compareTo(to.id) <= 0 ? from : to;
        Account second = (first == from) ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                if (from.balance < amt) return false; // insufficient funds
                from.balance -= amt;
                to.balance   += amt;
                return true;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account alice = new Account("alice", 1_000L);
        Account bob   = new Account("bob",   500L);

        Thread t1 = new Thread(() -> {
            boolean ok = transfer(alice, bob, 300);
            System.out.println("[TRANSFER] alice→bob 300: " + ok);
        });
        Thread t2 = new Thread(() -> {
            boolean ok = transfer(bob, alice, 200);
            System.out.println("[TRANSFER] bob→alice 200: " + ok);
        });

        t1.start(); t2.start();
        t1.join();  t2.join();

        System.out.println("[TRANSFER] alice=" + alice.balance + "  bob=" + bob.balance);
        // alice: 1000 - 300 + 200 = 900
        // bob:    500 + 300 - 200 = 600
    }
}
