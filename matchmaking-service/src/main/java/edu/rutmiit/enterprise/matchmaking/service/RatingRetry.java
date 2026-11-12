package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.config.RatingSettings;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
@Profile("!session-auth")
public class RatingRetry {
    private static final Logger log = LoggerFactory.getLogger(RatingRetry.class);
    private final RatingSettings settings;
    private final LongSupplier ticker;
    private final RetryDelay delays;
    private final Map<String, Counter> attempts;
    private final Map<String, Counter> operations;
    private final Counter retries;

    @Autowired
    public RatingRetry(RatingSettings settings, MeterRegistry registry) {
        this(settings, registry, System::nanoTime,
                new RetryDelay(settings, Clock.systemUTC(), () -> ThreadLocalRandom.current().nextDouble()));
    }

    RatingRetry(RatingSettings settings, MeterRegistry registry,
                        LongSupplier ticker, RetryDelay delays) {
        this.settings = settings;
        this.ticker = ticker;
        this.delays = delays;
        attempts = counters(registry, "matchmaking.rating.attempts",
                "success", "timeout", "connection", "http_retryable", "rejected", "interrupted", "other");
        operations = counters(registry, "matchmaking.rating.operations", "success", "failure", "budget_exhausted");
        retries = registry.counter("matchmaking.rating.retries");
    }

    private static Map<String, Counter> counters(MeterRegistry registry, String name, String... results) {
        var counters = new HashMap<String, Counter>();
        for (String result : results) { counters.put(result, registry.counter(name, "result", result)); }
        return Map.copyOf(counters);
    }

    // Применяется исключительно к одному безопасному GET, не к произвольным POST-командам.
    public <T> T execute(Supplier<T> request) {
        var budget = new TimeBudget(settings.budget(), ticker);
        var number = new AtomicInteger();
        var wait = new AtomicLong();
        var refusedByBudget = new AtomicBoolean();
        // Retry и его замыкания принадлежат одному вызову. Метрики общие и потокобезопасные.
        var config = RetryConfig.<T>custom()
                .maxAttempts(settings.maxAttempts())
                .retryOnException(failure -> {
                    if (number.get() >= settings.maxAttempts()) { return false; }
                    if (!HttpFailures.retryable(failure)) { return false; }
                    long delay = delays.millis(number.get(), failure);
                    boolean fits = budget.fits(settings.attemptAllowance(), settings.reserve(), delay);
                    refusedByBudget.set(!fits);
                    wait.set(delay);
                    return fits;
                })
                .intervalBiFunction((attempt, result) -> wait.get())
                .build();
        var retry = Retry.of("rating", config);
        retry.getEventPublisher().onRetry(event -> {
            retries.increment();
            log.atWarn().addKeyValue("event", "rating.retry")
                    .addKeyValue("attempt", number.get() + 1).addKeyValue("delayMs", wait.get())
                    .log("Повтор безопасного чтения рейтинга");
        });
        try {
            T response = retry.executeSupplier(() -> {
                if (!budget.fits(settings.attemptAllowance(), settings.reserve(), 0)) {
                    throw new TimeBudget.Exhausted();
                }
                int attempt = number.incrementAndGet();
                log.atInfo().addKeyValue("event", "rating.attempt")
                        .addKeyValue("attempt", attempt).log("Вызов рейтинга");
                try {
                    T value = request.get();
                    attempts.get("success").increment();
                    return value;
                } catch (RuntimeException failure) {
                    attempts.get(HttpFailures.result(failure)).increment();
                    throw failure;
                }
            });
            operations.get("success").increment();
            return response;
        } catch (RuntimeException failure) {
            boolean noBudget = refusedByBudget.get() || failure instanceof TimeBudget.Exhausted;
            operations.get(noBudget ? "budget_exhausted" : "failure").increment();
            if (noBudget) { throw new TimeBudget.Exhausted(); }
            throw failure;
        }
    }
}
