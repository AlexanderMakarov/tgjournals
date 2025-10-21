package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.SessionJournals;
import java.util.List;

/**
 * Interface for JournalRepository to enable JDK dynamic proxies in GraalVM native images.
 * This avoids CGLIB proxy issues with GraalVM native images.
 */
public interface JournalRepositoryInterface {
    
    Journal save(Journal journal);
    
    List<Long> saveBatch(List<Journal> journals);
    
    List<Journal> findByUserIdOrderByCreatedAtDesc(Long userId, int limit);
    
    List<Journal> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);
    
    List<SessionJournals> findLastNJournalsPerUser(Long userId, int limitLastSessions);
    
    Long countByUserId(Long userId);
    
    long count();
    
    void deleteAll();
}
