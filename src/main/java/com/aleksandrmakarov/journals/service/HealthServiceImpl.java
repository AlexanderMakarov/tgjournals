package com.aleksandrmakarov.journals.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import com.aleksandrmakarov.journals.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HealthServiceImpl implements HealthService {

	private final UserRepository userRepository;
	private final SessionRepository sessionRepository;
	private final JournalRepository journalRepository;

	private final AtomicReference<CachedCounts> cachedCounts = new AtomicReference<>();
	private static final long CACHE_DURATION_SECONDS = 60;

	@Autowired
	public HealthServiceImpl(UserRepository userRepository, SessionRepository sessionRepository,
			JournalRepository journalRepository) {
		this.userRepository = userRepository;
		this.sessionRepository = sessionRepository;
		this.journalRepository = journalRepository;
	}

	@Override
	public Map<String, Object> getHealthStatus() {
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

			return response;
		} catch (Exception e) {
			log.error("Error checking database health", e);
			Map<String, Object> response = new HashMap<>();
			response.put("status", "DOWN");
			response.put("service", "journals-bot");
			response.put("error", e.getMessage());
			response.put("timestamp", Instant.now());
			return response;
		}
	}

	private CachedCounts getCachedCounts() {
		CachedCounts current = cachedCounts.get();
		long now = System.currentTimeMillis();

		if (current == null || (now - current.lastUpdated()) > (CACHE_DURATION_SECONDS * 1000)) {
			log.debug("Updating cached database counts");
			CachedCounts newCounts = new CachedCounts(userRepository.count(), sessionRepository.count(),
					journalRepository.count(), now);
			cachedCounts.set(newCounts);
			return newCounts;
		}

		log.debug("Using cached database counts");
		return current;
	}

	private record CachedCounts(long userCount, long sessionCount, long journalCount, long lastUpdated) {
	}
}

