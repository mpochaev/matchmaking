package edu.rutmiit.enterprise.rating.service;

import edu.rutmiit.enterprise.rating.api.RatingController.RatingResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RatingService {
    private static final Logger log = LoggerFactory.getLogger(RatingService.class);
    private final DiagnosticMode diagnostics;
    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();
    private final Map<String, Counter> requests;
    private final Map<String, Timer> durations;

    public RatingService(DiagnosticMode diagnostics, MeterRegistry registry) {
        this.diagnostics = diagnostics;
        this.registry = registry;
        requests = Map.of(
                "success", registry.counter("rating.requests", "result", "success"),
                "failure", registry.counter("rating.requests", "result", "failure"));
        durations = Map.of("success", timer("success"), "failure", timer("failure"));
        Gauge.builder("rating.active", active, AtomicInteger::get)
                .description("Currently executing rating calculations")
                .register(registry);
    }

    private Timer timer(String result) {
        return Timer.builder("rating.processing")
                .description("Rating calculation time, excluding HTTP and authentication")
                .tag("result", result)
                .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(1))
                .publishPercentileHistogram()
                .register(registry);
    }

    public RatingResponse rate(UUID playerId) {
        active.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);
        String result = "failure";
        try {
            diagnostics.beforeCall();
            RatingResponse response = new RatingResponse(playerId, 1500, "GOLD");
            result = "success";
            log.atInfo().addKeyValue("event", "rating.completed")
                    .addKeyValue("outcome", result).log("Rating completed");
            return response;
        } catch (RuntimeException failure) {
            log.atWarn().addKeyValue("event", "rating.failed")
                    .addKeyValue("outcome", result)
                    .addKeyValue("failureType", failure.getClass().getSimpleName())
                    .log("Rating failed");
            throw failure;
        } finally {
            sample.stop(durations.get(result));
            requests.get(result).increment();
            active.decrementAndGet();
        }
    }
}
