package edu.rutmiit.enterprise.matchmaking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = RequestIds.normalize(Collections.list(request.getHeaders(RequestIds.HEADER)));
        String previous = MDC.get("requestId");
        response.setHeader(RequestIds.HEADER, requestId);
        MDC.put("requestId", requestId);
        try {
            log.info("HTTP request method={} path={}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
            log.info("HTTP response status={}", response.getStatus());
        } finally {
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }
}

