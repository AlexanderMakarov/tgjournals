package com.aleksandrmakarov.journals.controller;

import com.aleksandrmakarov.journals.repository.JournalRepositoryInterface;
import com.aleksandrmakarov.journals.repository.SessionRepositoryInterface;
import com.aleksandrmakarov.journals.repository.UserRepositoryInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous controller for public endpoints that don't require authentication. Provides health
 * check and API documentation endpoints.
 */
@RestController
@Tag(name = "Public", description = "Public endpoints accessible without authentication")
@RequiredArgsConstructor
@Slf4j
public class AnonymousController {

  private final UserRepositoryInterface userRepository;
  private final SessionRepositoryInterface sessionRepository;
  private final JournalRepositoryInterface journalRepository;

  private final AtomicReference<CachedCounts> cachedCounts = new AtomicReference<>();
  private static final long CACHE_DURATION_SECONDS = 60; // 1 minute

  /**
   * Health check endpoint for Kubernetes liveness probes.
   *
   * @return ResponseEntity containing service status information
   */
  @Operation(
      summary = "Health Check",
      description =
          "Returns the health status of the Journals Bot service. Used by Kubernetes for liveness checks.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Map.class),
                    examples =
                        @ExampleObject(
                            name = "Healthy Response",
                            value = "{\"status\": \"UP\", \"service\": \"journals-bot\"}")))
      })
  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    try {
      CachedCounts counts = getCachedCounts();

      Map<String, Object> response = new HashMap<>();
      response.put("status", "UP");
      response.put("service", "journals-bot");
      response.put("users", counts.userCount());
      response.put("sessions", counts.sessionCount());
      response.put("journals", counts.journalCount());
      response.put("lastUpdated", Instant.ofEpochMilli(counts.lastUpdated()));
      response.put("timestamp", Instant.now());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error checking database health", e);
      Map<String, Object> response = new HashMap<>();
      response.put("status", "DOWN");
      response.put("service", "journals-bot");
      response.put("error", e.getMessage());
      response.put("timestamp", Instant.now());
      return ResponseEntity.ok(response);
    }
  }

  private CachedCounts getCachedCounts() {
    CachedCounts current = cachedCounts.get();
    long now = System.currentTimeMillis();

    if (current == null || (now - current.lastUpdated()) > (CACHE_DURATION_SECONDS * 1000)) {
      log.debug("Updating cached database counts");
      CachedCounts newCounts =
          new CachedCounts(
              userRepository.count(), sessionRepository.count(), journalRepository.count(), now);
      cachedCounts.set(newCounts);
      return newCounts;
    }

    log.debug("Using cached database counts");
    return current;
  }

  private record CachedCounts(
      long userCount, long sessionCount, long journalCount, long lastUpdated) {}
}
