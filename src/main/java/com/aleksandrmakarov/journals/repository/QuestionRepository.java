package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import java.util.List;

/** Question repository interface. */
public interface QuestionRepository {

  List<Question> findBySessionIdOrderByOrderIndex(Long sessionId);

  List<Question> findBySessionIdOrderByOrderIndex(Long sessionId, QuestionType type);

  Question save(Question question);

  List<Long> saveBatch(List<Question> questions);

  void deleteBySessionId(Long sessionId);

  void deleteAll();
}
