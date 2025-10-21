package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import java.util.List;

/**
 * Interface for QuestionRepository to enable JDK dynamic proxies in GraalVM native images.
 * This avoids CGLIB proxy issues with GraalVM native images.
 */
public interface QuestionRepositoryInterface {
    
    List<Question> findBySessionIdOrderByOrderIndex(Long sessionId);
    
    List<Question> findBySessionIdOrderByOrderIndex(Long sessionId, QuestionType type);
    
    Question save(Question question);
    
    List<Long> saveBatch(List<Question> questions);
    
    void deleteBySessionId(Long sessionId);
    
    void deleteAll();
}
