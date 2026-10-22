package edu.rutmiit.enterprise.matchmaking.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RequestIdsTest {
    @Test
    void preservesFullUuidAndNormalizesCase() {
        String candidate = "AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE";
        assertThat(RequestIds.normalize(List.of(candidate))).isEqualTo(candidate.toLowerCase());
    }

    @Test
    void replacesMissingMalformedOversizedAndAmbiguousValues() {
        for (List<String> values : List.of(List.<String>of(), List.of("1-1-1-1-1"),
                List.of("not-a-uuid"), List.of("a".repeat(1000)),
                List.of("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"))) {
            String result = RequestIds.normalize(values);
            assertThat(result).matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
            assertThat(values).doesNotContain(result);
        }
    }
}

