package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Session;
import java.util.List;
import java.util.Optional;

/**
 * Interface for SessionRepository to enable JDK dynamic proxies in GraalVM native images.
 * This avoids CGLIB proxy issues with GraalVM native images.
 */
public interface SessionRepositoryInterface {
    
    Optional<Session> findActiveSession();
    
    Session save(Session session);
    
    void finishAllActiveSessions();
    
    long count();
    
    List<Session> findFinishedSessionsOrderedByCreatedAt();
    
    void deleteAll();
}
