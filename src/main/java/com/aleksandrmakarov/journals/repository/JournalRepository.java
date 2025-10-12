package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.util.TimestampUtils;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JournalRepository {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<Journal> journalRowMapper;

  @PostConstruct
  private void initRowMapper() {
    this.journalRowMapper =
        (rs, rowNum) -> {
          long timestampMillis = rs.getLong("created_at");
          LocalDateTime dateTime = TimestampUtils.fromMillis(timestampMillis);
          return new Journal(
              rs.getLong("id"),
              rs.getString("answer"),
              dateTime,
              rs.getLong("user_id"),
              rs.getLong("session_id"),
              rs.getLong("question_id"));
        };
  }

  public Journal save(Journal journal) {
    if (journal.id() == null) {
      // Insert new journal
      jdbcTemplate.update(
          "INSERT INTO journals (answer, created_at, user_id, session_id, question_id) VALUES (?, ?, ?, ?, ?)",
          journal.answer(),
          TimestampUtils.toMillis(journal.createdAt()),
          journal.userId(),
          journal.sessionId(),
          journal.questionId());
      Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
      return new Journal(
          id,
          journal.answer(),
          journal.createdAt(),
          journal.userId(),
          journal.sessionId(),
          journal.questionId());
    } else {
      // Update existing journal
      jdbcTemplate.update(
          "UPDATE journals SET answer = ?, created_at = ?, user_id = ?, session_id = ?, question_id = ? WHERE id = ?",
          journal.answer(),
          TimestampUtils.toMillis(journal.createdAt()),
          journal.userId(),
          journal.sessionId(),
          journal.questionId(),
          journal.id());
      return journal;
    }
  }

  public List<Journal> findByUserIdOrderByCreatedAtDesc(Long userId, int limit) {
    return jdbcTemplate.query(
        "SELECT * FROM journals WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        journalRowMapper,
        userId,
        limit);
  }

  public List<Journal> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId) {
    return jdbcTemplate.query(
        "SELECT * FROM journals WHERE user_id = ? AND session_id = ? ORDER BY created_at DESC",
        journalRowMapper,
        userId,
        sessionId);
  }

  public Long countByUserId(Long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM journals WHERE user_id = ?", Long.class, userId);
  }

  /**
   * Counts the total number of journals in the database.
   *
   * @return Total number of journals
   */
  public long count() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journals", Long.class);
  }

  /** Deletes all journals from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM journals");
  }
}
