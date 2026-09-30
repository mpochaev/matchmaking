package edu.rutmiit.enterprise.matchmaking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(
        @NotBlank @Size(max = 100) String nickname,
        @Pattern(regexp = "RU|EU|AS|US|ZZ") String region
) {
}
