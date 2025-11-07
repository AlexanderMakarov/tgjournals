package com.aleksandrmakarov.journals.controller;

import com.aleksandrmakarov.journals.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous controller for public endpoints that don't require authentication.
 * Provides health check and API documentation endpoints.
 */
@RestController
@Tag(name = "Public", description = "Public endpoints accessible without authentication")
@RequiredArgsConstructor
public class AnonymousController {

	private final HealthService healthService;

	/**
	 * Health check endpoint for Kubernetes liveness probes.
	 *
	 * @return ResponseEntity containing service status information
	 */
	@Operation(summary = "Health Check", description = "Returns the health status of the Journals Bot service. Used by Kubernetes for liveness checks.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Service is healthy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class), examples = @ExampleObject(name = "Healthy Response", value = "{\"status\": \"UP\", \"service\": \"journals-bot\"}")))})
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		return ResponseEntity.ok(healthService.getHealthStatus());
	}
}
