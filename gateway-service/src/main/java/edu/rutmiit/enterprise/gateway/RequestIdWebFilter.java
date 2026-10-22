package edu.rutmiit.enterprise.gateway;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdWebFilter.class);

    @PostConstruct
    void registerMdcAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                "requestId", () -> MDC.get("requestId"),
                value -> MDC.put("requestId", value), () -> MDC.remove("requestId"));
    }

    @PreDestroy
    void removeMdcAccessor() {
        ContextRegistry.getInstance().removeThreadLocalAccessor("requestId");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = RequestIds.normalize(
                exchange.getRequest().getHeaders().getOrDefault(RequestIds.HEADER, List.of()));
        var request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(RequestIds.HEADER, requestId))
                .build();
        var correlatedExchange = exchange.mutate().request(request).build();
        exchange.getResponse().getHeaders().set(RequestIds.HEADER, requestId);
        // Прокси может добавить такой же header из ответа backend; перед отправкой оставляем один.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(RequestIds.HEADER, requestId);
            return Mono.empty();
        });

        return Mono.defer(() -> {
            log.info("HTTP request method={} path={}", request.getMethod(), request.getPath().value());
            return chain.filter(correlatedExchange)
                    .doOnSuccess(unused -> log.info("HTTP response status={}", exchange.getResponse().getStatusCode()))
                    .doOnError(failure -> log.warn("HTTP request failed type={}", failure.getClass().getSimpleName()));
        }).contextWrite(context -> context.put("requestId", requestId));
    }
}

