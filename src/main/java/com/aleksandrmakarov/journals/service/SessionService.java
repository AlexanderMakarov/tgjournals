package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.SessionJournals;
import com.aleksandrmakarov.journals.repository.JournalRepository;
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

  @Autowired private JournalRepository journalRepository;

  @Autowired private QuestionRepository questionRepository;

  public Session getActiveSession() {
    return sessionRepository.findActiveSession().orElse(null);
  }

  public Session createNewSession(String name) {
    // Finish current active session
    sessionRepository.finishAllActiveSessions();

    // Create new session
    Session newSession = new Session(null, name, LocalDateTime.now(), null);
    Session savedSession = sessionRepository.save(newSession);

    // Copy questions from the most recent finished session
    copyQuestionsFromLastSession(savedSession);

    return savedSession;
  }

  /**
   * Finishes the active session and returns the finished session.
   *
   * @return The finished session or @{@code null} if there is no active session.
   */
  public Session finishActiveSession() {
    Session activeSession = getActiveSession();
    if (activeSession != null) {
      Session finishedSession =
          new Session(
              activeSession.id(),
              activeSession.name(),
              activeSession.createdAt(),
              LocalDateTime.now());
      return sessionRepository.save(finishedSession);
    }
    return null;
  }

  public void updateSessionQuestions(Session session, List<Question> questions) {
    questionRepository.deleteBySessionId(session.id());
    if (questions == null || questions.isEmpty()) {
      return;
    }
    for (Question q : questions) {
      if (q == null) continue;
      Question toSave = new Question(null, q.text(), q.type(), q.orderIndex(), session.id());
      questionRepository.save(toSave);
    }
  }

  public List<Question> getQuestions(Long sessionId) {
    return questionRepository.findBySessionIdOrderByOrderIndex(sessionId);
  }

  public List<SessionJournals> getJournalsForLastSessions(Long userId, int limitLastSessions) {
    return journalRepository.findByUserIdGroupBySessionIdOrderByCreatedAtAsc(userId, limitLastSessions);
  }

  private void copyQuestionsFromLastSession(Session newSession) {
    // Find the most recent finished session
    List<Session> finishedSessions = sessionRepository.findFinishedSessionsOrderedByCreatedAt();
    if (!finishedSessions.isEmpty()) {
      Session lastSession = finishedSessions.get(0);
      // Copy all questions from the last session
      List<Question> questions =
          questionRepository.findBySessionIdOrderByOrderIndex(
              lastSession.id(), QuestionType.BEFORE);
      for (Question question : questions) {
        Question newQuestion =
            new Question(
                null, question.text(), question.type(), question.orderIndex(), newSession.id());
        questionRepository.save(newQuestion);
      }
    }
  }
}
