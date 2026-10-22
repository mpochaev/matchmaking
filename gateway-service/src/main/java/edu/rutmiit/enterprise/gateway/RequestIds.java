package edu.rutmiit.enterprise.gateway;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {
    public static final String HEADER = "X-Request-ID";
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private RequestIds() {}

    public static String normalize(List<String> values) {
        if (values.size() == 1) {
            String candidate = values.getFirst();
            if (candidate != null && candidate.length() == 36 && UUID_FORMAT.matcher(candidate).matches()) {
                return UUID.fromString(candidate).toString();
            }
        }
        return UUID.randomUUID().toString();
    }
}

