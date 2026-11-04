package edu.rutmiit.enterprise.rating.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class DiagnosticModeTest {
    @Test
    void modeExpiresAndCanBeResetWithoutSleeping() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var mode = new DiagnosticMode(clock);
        mode.change(DiagnosticMode.Mode.FAIL, 2);
        assertThat(mode.current().mode()).isEqualTo(DiagnosticMode.Mode.FAIL);
        now.set(now.get().plusSeconds(2));
        assertThat(mode.current().mode()).isEqualTo(DiagnosticMode.Mode.NORMAL);
        mode.change(DiagnosticMode.Mode.SLOW, 10);
        mode.change(DiagnosticMode.Mode.NORMAL, 1);
        assertThat(mode.current().mode()).isEqualTo(DiagnosticMode.Mode.NORMAL);
    }

    @Test
    void rejectsUnboundedDiagnosticDuration() {
        var mode = new DiagnosticMode();
        assertThatThrownBy(() -> mode.change(DiagnosticMode.Mode.FAIL, 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> mode.change(DiagnosticMode.Mode.FAIL, 121))
                .isInstanceOf(ResponseStatusException.class);
    }
}
