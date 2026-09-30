package edu.rutmiit.enterprise.matchmaking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLobbyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{6}$") String code,
        @NotNull UUID hostId,
        @Min(3) @Max(5) Integer maxPlayers
) {
}
