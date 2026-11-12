package edu.rutmiit.enterprise.matchmaking.service;

import org.springframework.web.client.RestClientResponseException;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;

public final class HttpFailures {
    private HttpFailures() {}

    public static String result(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                return "interrupted";
            }
            if (cause instanceof RestClientResponseException response) {
                return switch (response.getStatusCode().value()) {
                    case 429, 502, 503, 504 -> "http_retryable";
                    default -> "rejected";
                };
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return "timeout";
            }
            if (cause instanceof ConnectException || cause instanceof SocketException || cause instanceof EOFException) {
                return "connection";
            }
        }
        return "other";
    }

    public static boolean retryable(Throwable failure) {
        return switch (result(failure)) {
            case "timeout", "connection", "http_retryable" -> true;
            default -> false;
        };
    }
}
