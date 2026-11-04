package edu.rutmiit.enterprise.rating.api;

import edu.rutmiit.enterprise.rating.service.DiagnosticMode;
import edu.rutmiit.enterprise.rating.service.RatingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/internal")
public class RatingController {
    private final RatingService ratings;
    private final DiagnosticMode diagnostics;

    public RatingController(RatingService ratings, DiagnosticMode diagnostics) {
        this.ratings = ratings;
        this.diagnostics = diagnostics;
    }

    @GetMapping("/ratings/{playerId}")
    @PreAuthorize("hasRole('SERVICE')")
    public RatingResponse rating(@PathVariable UUID playerId) {
        return ratings.rate(playerId);
    }

    @GetMapping("/admin/info")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String, String> info() {
        return Map.of("service", "rating-service", "status", "ok");
    }

    @PutMapping("/admin/failure-mode")
    @PreAuthorize("hasRole('OPERATOR')")
    public DiagnosticMode.Setting changeMode(@RequestParam DiagnosticMode.Mode mode,
                                            @RequestParam(defaultValue = "90") int seconds) {
        return diagnostics.change(mode, seconds);
    }

    @GetMapping("/admin/failure-mode")
    @PreAuthorize("hasRole('OPERATOR')")
    public DiagnosticMode.Setting currentMode() {
        return diagnostics.current();
    }

    public record RatingResponse(UUID playerId, int rating, String rank) {}
}
