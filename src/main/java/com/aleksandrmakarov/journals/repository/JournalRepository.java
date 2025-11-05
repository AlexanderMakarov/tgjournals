package com.aleksandrmakarov.journals.repository;

import java.util.List;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.SessionJournals;

/** Journal repository interface. */
public interface JournalRepository {
	Journal save(Journal journal);

	Journal upsertJournal(String answer, Long userId, Long sessionId, Long questionId);

	List<Long> saveBatch(List<Journal> journals);

	List<Journal> findByUserIdOrderByCreatedAtDesc(Long userId, int limit);

	List<Journal> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);

	List<SessionJournals> findLastNJournalsPerUser(Long userId, int limitLastSessions);

	Long countByUserId(Long userId);

	long count();

	void deleteAll();
}
