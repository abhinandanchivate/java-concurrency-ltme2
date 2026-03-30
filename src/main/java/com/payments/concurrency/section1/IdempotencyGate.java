package com.payments.concurrency.section1;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECTION 1 — Correct Implementation C: Idempotency gate (duplicate transaction IDs)
 *
 * WHY: Mobile retries and network timeouts can submit the same transaction ID multiple times.
 * Without an idempotency gate, each submission applies independently — doubling the debit.
 *
 * IMPLEMENTATION: ConcurrentHashMap.newKeySet() is thread-safe. Set.add() returns true
 * only the FIRST time a key is inserted — making firstTime() an atomic check-and-register.
 *
 * PRODUCTION NOTE: In a distributed system, this set lives in Redis or a DB table, not
 * in-process memory. The principle is the same.
 * 
 * if(seen.contains(txnId)){
 * seen.add();
 * return true;
 * 
 */
public class IdempotencyGate {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
    Set<String> keys = map.keySet();

    /**
     * Returns true only the FIRST time this txnId is presented.
     * Subsequent calls with the same ID return false — the caller should treat it as a duplicate.
     */
    public boolean firstTime(String txnId) {
        return seen.add(txnId); // thread-safe; returns true only on first insertion
    }

    public static void main(String[] args) {
        IdempotencyGate gate = new IdempotencyGate();

        System.out.println("[IDEMPOTENCY] TXN-001 first time?  " + gate.firstTime("TXN-001")); // true
        System.out.println("[IDEMPOTENCY] TXN-001 retry?       " + gate.firstTime("TXN-001")); // false
        System.out.println("[IDEMPOTENCY] TXN-002 first time?  " + gate.firstTime("TXN-002")); // true
        System.out.println("[IDEMPOTENCY] TXN-001 third attempt?" + gate.firstTime("TXN-001")); // false
    }
}
