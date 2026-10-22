package edu.rutmiit.enterprise.matchmaking.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

class RequestIdFilterTest {
    @Test
    void exposesIdBeforeChainAndRestoresMdcAfterException() {
        var request = new MockHttpServletRequest("GET", "/api/players");
        var response = new MockHttpServletResponse();
        request.addHeader(RequestIds.HEADER, "invalid");
        MDC.put("requestId", "outer");
        try {
            assertThatThrownBy(() -> new RequestIdFilter().doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader(RequestIds.HEADER));
                throw new IOException("simulated failure");
            })).isInstanceOf(IOException.class);
            assertThat(MDC.get("requestId")).isEqualTo("outer");
            assertThat(response.getHeader(RequestIds.HEADER)).isNotEqualTo("invalid");
        } finally {
            MDC.remove("requestId");
        }
    }
}

