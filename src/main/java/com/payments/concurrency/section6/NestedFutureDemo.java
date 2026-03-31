package com.payments.concurrency.section6;

import java.util.concurrent.CompletableFuture;

/**
 * SECTION 6 — CompletableFuture Composition
 *
 * PROBLEM: NestedFutureDemo
 * Using thenApply with a lambda that itself returns a CompletableFuture produces a
 * CompletableFuture<CompletableFuture<T>> — a nested future.
 *
 * To get the actual value you need .join().join() — verbose and error-prone.
 * This is the most common CompletableFuture mistake.
 *
 * FIX: Use thenCompose when your transformation returns a CompletableFuture.
 *      thenCompose "flattens" the result — equivalent to flatMap in streams.
 *      
 *      | Method      | Type             | Use Case              | Returns      |
| ----------- | ---------------- | --------------------- | ------------ |
| thenApply   | sync transform   | result → new value    | Future<T>    |
| thenCompose | async chain      | result → async call   | Future<T>    |
| thenCombine | parallel combine | 2 independent futures | Future<T>    |
| allOf       | wait many        | N futures             | Future<Void> |

 *      
 *      
 *      
 */
public class NestedFutureDemo {

    static CompletableFuture<Integer> fraudScoreAsync() {
        return CompletableFuture.supplyAsync(() -> 90);
    }

    public static void main(String[] args) {
        CompletableFuture<Boolean> validF = CompletableFuture.supplyAsync(() -> true);

        // ❌ thenApply returns CompletableFuture<CompletableFuture<Integer>>
//        CompletableFuture<CompletableFuture<Integer>> nested =
//                validF.thenApply(valid -> valid
//                        ? fraudScoreAsync()
//                        : CompletableFuture.completedFuture(0));
        CompletableFuture<Integer> nested = validF.thenCompose(valid-> valid? fraudScoreAsync()
        		:CompletableFuture.completedFuture(0));
        // T-> R boolean-> completableFuture<Integer>

        // Two .join() calls needed — brittle, confusing, easy to miss the second
        int score = nested.join();
        System.out.println("[NESTED] Score=" + score + "  (needed .join().join() — use thenCompose instead)");
    }
}
