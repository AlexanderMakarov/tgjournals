package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {

  private final JdbcTemplate jdbcTemplate;

  public QuestionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final RowMapper<Question> QUESTION_ROW_MAPPER =
      (rs, rowNum) ->
          new Question(
              rs.getLong("id"),
              rs.getString("text"),
              QuestionType.valueOf(rs.getString("type")),
              rs.getInt("order_index"),
              rs.getLong("session_id"));

  public List<Question> findBySessionIdAndTypeOrderByOrderIndex(Long sessionId, QuestionType type) {
    return jdbcTemplate.query(
        "SELECT * FROM questions WHERE session_id = ? AND type = ? ORDER BY order_index",
        QUESTION_ROW_MAPPER,
        sessionId,
        type.name());
  }

  public List<Question> findBySessionIdOrderByOrderIndex(Long sessionId, QuestionType type) {
    return jdbcTemplate.query(
        "SELECT * FROM questions WHERE session_id = ? ORDER BY order_index",
        QUESTION_ROW_MAPPER,
        sessionId);
  }

  public Question save(Question question) {
    if (question.id() == null) {
      // Insert new question
      jdbcTemplate.update(
          "INSERT INTO questions (text, type, order_index, session_id) VALUES (?, ?, ?, ?)",
          question.text(),
          question.type().name(),
          question.orderIndex(),
          question.sessionId());
      Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
      return new Question(
          id, question.text(), question.type(), question.orderIndex(), question.sessionId());
    } else {
      // Update existing question
      jdbcTemplate.update(
          "UPDATE questions SET text = ?, type = ?, order_index = ?, session_id = ? WHERE id = ?",
          question.text(),
          question.type().name(),
          question.orderIndex(),
          question.sessionId(),
          question.id());
      return question;
    }
  }

  public void deleteBySessionId(Long sessionId) {
    jdbcTemplate.update("DELETE FROM questions WHERE session_id = ?", sessionId);
  }

  /** Deletes all questions from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM questions");
  }
}
