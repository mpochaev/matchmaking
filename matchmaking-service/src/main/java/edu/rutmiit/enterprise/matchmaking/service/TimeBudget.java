package edu.rutmiit.enterprise.matchmaking.service;

import java.time.Duration;
import java.util.function.LongSupplier;

public final class TimeBudget {
    private final LongSupplier ticker;
    private final long started;
    private final long allowance;

    public TimeBudget(Duration allowance, LongSupplier ticker) {
        this.ticker = ticker;
        this.started = ticker.getAsLong();
        this.allowance = allowance.toNanos();
    }

    public boolean fits(Duration attempt, Duration reserve, long waitMillis) {
        long remaining = Math.max(0L, allowance - Math.max(0L, ticker.getAsLong() - started));
        long required = attempt.plus(reserve).toNanos();
        if (remaining < required || waitMillis < 0) {
            return false;
        }
        return waitMillis <= (remaining - required) / 1_000_000L;
    }

    public static class Exhausted extends RuntimeException {
        public Exhausted() {
            super("Недостаточно остатка бюджета для новой попытки");
        }
    }
}
