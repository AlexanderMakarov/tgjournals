package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JournalService {

  @Autowired private JournalRepository journalRepository;

  @Autowired private QuestionRepository questionRepository;

  public Journal saveJournal(String answer, User user, Session session, Question question) {
    Journal journal =
        new Journal(null, answer, LocalDateTime.now(), user.id(), session.id(), question.id());
    return journalRepository.save(journal);
  }

  public List<Journal> getUserJournals(User user, int limit) {
    return journalRepository.findByUserIdOrderByCreatedAtDesc(user.id(), limit);
  }

  public List<Journal> getUserJournalsForSession(User user, Session session) {
    return journalRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(user.id(), session.id());
  }

  public Long getUserJournalCount(User user) {
    return journalRepository.countByUserId(user.id());
  }

  public String formatJournalsForDisplay(List<Journal> journals) {
    if (journals.isEmpty()) {
      return "No journals found.";
    }

    StringBuilder sb = new StringBuilder();
    for (Journal journal : journals) {
      sb.append("📅 ").append(journal.createdAt().toLocalDate()).append("\n");
      // We need to get the question text - this is a limitation of the current approach
      // In a real implementation, you might want to join with questions table
      sb.append("❓ Question ID: ").append(journal.questionId()).append("\n");
      sb.append("💭 ").append(journal.answer()).append("\n\n");
    }
    return sb.toString();
  }
}
