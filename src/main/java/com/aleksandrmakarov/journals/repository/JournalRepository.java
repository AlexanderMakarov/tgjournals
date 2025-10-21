package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.SessionJournals;
import java.util.List;

/** Journal repository interface. */
public interface JournalRepository {
  Journal save(Journal journal);

  List<Long> saveBatch(List<Journal> journals);

  List<Journal> findByUserIdOrderByCreatedAtDesc(Long userId, int limit);

  List<Journal> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);

  List<SessionJournals> findLastNJournalsPerUser(Long userId, int limitLastSessions);

  Long countByUserId(Long userId);

  long count();

  void deleteAll();
}
