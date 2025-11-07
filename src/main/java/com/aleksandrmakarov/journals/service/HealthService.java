package com.aleksandrmakarov.journals.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for health check operations.
 */
public interface HealthService {
	/**
	 * Gets health status information including user, session, and journal counts.
	 *
	 * @return Map containing health status information
	 */
	Map<String, Object> getHealthStatus();
}

