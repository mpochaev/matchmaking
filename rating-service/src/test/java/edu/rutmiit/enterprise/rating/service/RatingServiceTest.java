package edu.rutmiit.enterprise.rating.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RatingServiceTest {
    @Test
    void completedAndFailedCallsHaveBoundedTagsAndReleaseActiveGauge() {
        var registry = new SimpleMeterRegistry();
        try {
            var mode = new DiagnosticMode();
            var service = new RatingService(mode, registry);
            for (int i = 0; i < 5; i++) {
                service.rate(UUID.randomUUID());
            }
            mode.change(DiagnosticMode.Mode.FAIL, 30);
            assertThatThrownBy(() -> service.rate(UUID.randomUUID()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(registry.get("rating.requests").tag("result", "success").counter().count())
                    .isEqualTo(5);
            assertThat(registry.get("rating.requests").tag("result", "failure").counter().count())
                    .isEqualTo(1);
            assertThat(registry.get("rating.processing").tag("result", "failure").timer().count())
                    .isEqualTo(1);
            assertThat(registry.get("rating.active").gauge().value()).isZero();
            assertThat(registry.getMeters()).allSatisfy(meter ->
                    assertThat(meter.getId().getTags()).allSatisfy(tag -> {
                        assertThat(tag.getKey()).isIn("result", "le");
                        if (tag.getKey().equals("result")) {
                            assertThat(tag.getValue()).isIn("success", "failure");
                        } else {
                            // SimpleMeterRegistry представляет две заданные SLO-границы как gauges.
                            assertThat(tag.getValue()).isIn("0.5", "1");
                        }
                    }));
        } finally {
            registry.close();
        }
    }
}
