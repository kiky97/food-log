package com.foodlog.util;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Retry {

    private Retry() {
    }

    /** Runs {@code call} up to {@code maxAttempts} times with exponential backoff between retryable failures. */
    public static <T> T execute(int maxAttempts, Duration backoff, Supplier<T> call,
                                Predicate<RuntimeException> retryable) {
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (attempt >= attempts || !retryable.test(e)) {
                    throw e;
                }
                sleep(backoff.toMillis() * (1L << (attempt - 1)));
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", ie);
        }
    }
}
