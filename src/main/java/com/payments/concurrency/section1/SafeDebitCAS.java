package com.payments.concurrency.section1;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SECTION 1 — Correct Implementation A: Atomic debit using CAS (AtomicLong)
 *
 * WHY: A single numeric invariant (balance) can be protected with a CAS loop.
 * compareAndSet atomically checks the current value and updates it only if it
 * hasn't changed. If another thread changed the balance first, we re-read and retry.
 *
 * WHEN TO USE: Single-field numeric invariants (one balance, one counter).
 * NOT suitable for multi-field updates — use locks with ordering for those (see SafeTransferDemo).
 */
public class SafeDebitCAS {

    static class Account {
        private final AtomicLong balance = new AtomicLong(1_000L);

        /**
         * 
         * IF (memory_value == expected_value)
    memory_value = new_value
    RETURN true
ELSE
    RETURN false
    1000 ==> memory ==> 200
    t1 and t2 ==> balance 1000
    compareandset : CAS ==> CAS(1000, 200) --> success
    t2 ==> balance 1000==> in memory balance is 200 not 1000 ==> CAS ==> fails
         * Atomically debits the account.
         *
         * @param amt amount in minor units (paise/cents — never use float/double for money)
         * @return true if the debit was applied; false if balance was insufficient
         */
        boolean debit(long amt) {
            while (true) {
                long cur = balance.get();
                if (cur < amt) return false;                          // insufficient funds
                if (balance.compareAndSet(cur, cur - amt)) return true; // CAS succeeded
                // else: another thread updated balance — retry from current value
            }
        }

        long balance() { return balance.get(); }
    }

    public static void main(String[] args) {
        Account acc = new Account();

        boolean first  = acc.debit(800); // should succeed  → balance=200
        boolean second = acc.debit(800); // should fail     → balance unchanged

        System.out.println("[CAS] First  debit(800): " + first  + "  balance=" + acc.balance());
        System.out.println("[CAS] Second debit(800): " + second + "  balance=" + acc.balance());
    }
}
