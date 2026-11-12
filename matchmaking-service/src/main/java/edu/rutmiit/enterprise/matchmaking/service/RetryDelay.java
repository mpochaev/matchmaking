package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.config.RatingSettings;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.DoubleSupplier;

public final class RetryDelay {
    private final RatingSettings settings;
    private final Clock clock;
    private final DoubleSupplier random;

    public RetryDelay(RatingSettings settings, Clock clock, DoubleSupplier random) {
        this.settings = settings;
        this.clock = clock;
        this.random = random;
    }

    public long millis(int retryNumber, Throwable failure) {
        double exponential = settings.initialWait().toMillis() * Math.pow(2, Math.min(retryNumber - 1, 30));
        double base = Math.min(settings.maxWait().toMillis(), exponential);
        long policy = Math.min(settings.maxWait().toMillis(),
                Math.round(base * (1 - settings.jitter() + 2 * settings.jitter() * random.getAsDouble())));
        if (failure instanceof RestClientResponseException response && response.getResponseHeaders() != null) {
            return Math.max(policy, serverWait(response.getResponseHeaders().getFirst("Retry-After")));
        }
        return policy;
    }

    long serverWait(String value) {
        if (value == null || value.isBlank()) { return 0; }
        if (value.matches("[0-9]+")) {
            try {
                return Math.multiplyExact(Long.parseLong(value), 1000L);
            } catch (ArithmeticException | NumberFormatException tooLarge) {
                // Большое корректное delay-seconds запрещает повтор в нашем коротком бюджете.
                return Long.MAX_VALUE;
            }
        }
        try {
            var instant = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(0, Duration.between(clock.instant(), instant).toMillis());
        } catch (DateTimeParseException malformed) {
            // Некорректная подсказка не продлевает срок; остаётся ограниченная локальная политика.
            return 0;
        }
    }
}
