package com.aleksandrmakarov.journals.repository;

import java.util.List;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;

/** Question repository interface. */
public interface QuestionRepository {

	List<Question> findBySessionIdOrderByOrderIndex(Long sessionId);

	List<Question> findBySessionIdAndQuestionTypeOrderByOrderIndex(Long sessionId, QuestionType type);

	Question save(Question question);

	List<Long> saveBatch(List<Question> questions);

	void deleteBySessionId(Long sessionId);

	void deleteAll();
}
