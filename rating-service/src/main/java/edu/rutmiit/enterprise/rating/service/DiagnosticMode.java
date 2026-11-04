package edu.rutmiit.enterprise.rating.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DiagnosticMode {
    public enum Mode { NORMAL, SLOW, FAIL }
    public record Setting(Mode mode, Instant expiresAt) {}

    private final Clock clock;
    private final AtomicReference<Setting> setting =
            new AtomicReference<>(new Setting(Mode.NORMAL, Instant.EPOCH));

    public DiagnosticMode() {
        this(Clock.systemUTC());
    }

    DiagnosticMode(Clock clock) {
        this.clock = clock;
    }

    public Setting current() {
        Setting current = setting.get();
        if (current.mode() != Mode.NORMAL && !clock.instant().isBefore(current.expiresAt())) {
            setting.compareAndSet(current, new Setting(Mode.NORMAL, Instant.EPOCH));
        }
        return setting.get();
    }

    public Setting change(Mode mode, int seconds) {
        if (seconds < 1 || seconds > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seconds must be between 1 and 120");
        }
        Setting next = new Setting(mode,
                mode == Mode.NORMAL ? Instant.EPOCH : clock.instant().plusSeconds(seconds));
        setting.set(next);
        return next;
    }

    public void beforeCall() {
        switch (current().mode()) {
            case NORMAL -> {}
            case FAIL -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Laboratory diagnostic failure");
            case SLOW -> {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
                }
            }
        }
    }
}
