package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionService {

  @Autowired private SessionRepository sessionRepository;

  @Autowired private QuestionRepository questionRepository;

  public Session getActiveSession() {
    return sessionRepository.findByIsActiveTrue().orElse(null);
  }

  public Session createNewSession() {
    // Deactivate current session
    sessionRepository.deactivateAllSessions();

    // Create new session
    Session newSession = new Session(null, LocalDateTime.now(), true);
    return sessionRepository.save(newSession);
  }

  public void updateSessionQuestions(Session session, String questionsText) {
    // Clear existing questions
    questionRepository.deleteBySessionId(session.id());

    // Parse and create new questions
    String[] lines = questionsText.split("\n");
    int beforeOrder = 1;
    int afterOrder = 1;

    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty()) continue;

      QuestionType type;
      String text;

      if (line.startsWith("Before: ")) {
        type = QuestionType.BEFORE;
        text = line.substring(8);
        Question question = new Question(null, text, type, beforeOrder++, session.id());
        questionRepository.save(question);
      } else if (line.startsWith("After: ")) {
        type = QuestionType.AFTER;
        text = line.substring(7);
        Question question = new Question(null, text, type, afterOrder++, session.id());
        questionRepository.save(question);
      }
    }
  }

  public List<Question> getQuestionsByType(Session session, QuestionType type) {
    return questionRepository.findBySessionIdAndTypeOrderByOrderIndex(session.id(), type);
  }
}
