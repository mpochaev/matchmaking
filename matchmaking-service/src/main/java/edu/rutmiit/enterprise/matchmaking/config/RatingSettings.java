package edu.rutmiit.enterprise.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rating")
public record RatingSettings(String baseUrl, Duration connectTimeout, Duration readTimeout,
        Duration budget, Duration reserve, int maxAttempts, Duration initialWait, Duration maxWait,
        double jitter) {
    public RatingSettings {
        if (baseUrl == null || connectTimeout == null || readTimeout == null || budget == null
                || reserve == null || initialWait == null || maxWait == null) {
            throw new IllegalArgumentException("Все параметры клиента рейтинга обязательны");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()
                || budget.isNegative() || budget.isZero() || reserve.isNegative()
                || initialWait.isNegative() || maxWait.compareTo(initialWait) < 0
                || maxAttempts < 1 || maxAttempts > 2 || !Double.isFinite(jitter) || jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("Некорректная политика повторов");
        }
    }

    // Консервативный допуск, а не утверждение о сложении таймеров конкретного HTTP-клиента.
    public Duration attemptAllowance() {
        return connectTimeout.plus(readTimeout);
    }
}
