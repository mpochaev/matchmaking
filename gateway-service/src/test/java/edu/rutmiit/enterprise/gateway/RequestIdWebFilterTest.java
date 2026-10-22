package edu.rutmiit.enterprise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

class RequestIdWebFilterTest {
    @Test
    void replacesDuplicateHeadersBeforeAnyHandlerAndWritesReactorContext() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bff/me")
                .header(RequestIds.HEADER, "11111111-1111-4111-8111-111111111111",
                        "22222222-2222-4222-8222-222222222222"));
        new RequestIdWebFilter().filter(exchange, forwarded -> Mono.deferContextual(context -> {
            String requestId = context.get("requestId");
            assertThat(forwarded.getRequest().getHeaders().get(RequestIds.HEADER))
                    .containsExactly(requestId);
            assertThat(exchange.getResponse().getHeaders().getFirst(RequestIds.HEADER))
                    .isEqualTo(requestId);
            // Имитируем копирование одноименного заголовка из ответа backend.
            forwarded.getResponse().getHeaders().add(RequestIds.HEADER, requestId);
            return forwarded.getResponse().setComplete();
        })).block();
        assertThat(exchange.getResponse().getHeaders().get(RequestIds.HEADER)).hasSize(1);
    }
}

