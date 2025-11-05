package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Session;
import java.util.List;
import java.util.Optional;

/** Session repository interface. */
public interface SessionRepository {

	Optional<Session> findActiveSession();

	Session save(Session session);

	void finishAllActiveSessions();

	long count();

	List<Session> findFinishedSessionsOrderedByCreatedAt();

	void deleteAll();
}
