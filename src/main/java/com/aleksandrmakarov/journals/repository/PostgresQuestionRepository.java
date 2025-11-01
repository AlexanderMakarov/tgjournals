package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresQuestionRepository implements QuestionRepository {

  private final JdbcTemplate jdbcTemplate;

  public PostgresQuestionRepository(JdbcTemplate jdbcTemplate) {
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

  public List<Question> findBySessionIdOrderByOrderIndex(Long sessionId) {
    return jdbcTemplate.query(
        "SELECT * FROM questions WHERE session_id = ? ORDER BY order_index",
        QUESTION_ROW_MAPPER,
        sessionId);
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
      Long id = jdbcTemplate.queryForObject(
          "INSERT INTO questions (text, type, order_index, session_id) VALUES (?, ?, ?, ?) RETURNING id",
          Long.class,
          question.text(),
          question.type().name(),
          question.orderIndex(),
          question.sessionId());
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

  public List<Long> saveBatch(List<Question> questions) {
    if (questions == null || questions.isEmpty()) {
      return java.util.Collections.emptyList();
    }

    StringBuilder sql =
        new StringBuilder("INSERT INTO questions (text, type, order_index, session_id) VALUES ");
    String sep = "";
    for (int i = 0; i < questions.size(); i++) {
      sql.append(sep).append("(?, ?, ?, ?)");
      sep = ", ";
    }
    sql.append(" RETURNING id");

    java.util.List<Object> params = new java.util.ArrayList<>(questions.size() * 4);
    for (Question q : questions) {
      params.add(q.text());
      params.add(q.type().name());
      params.add(q.orderIndex());
      params.add(q.sessionId());
    }

    Object[] arr = params.toArray();
    return jdbcTemplate.query(
        con -> {
          var ps = con.prepareStatement(sql.toString());
          for (int i = 0; i < arr.length; i++) {
            ps.setObject(i + 1, arr[i]);
          }
          return ps;
        },
        (rs, rowNum) -> rs.getLong("id"));
  }

  public void deleteBySessionId(Long sessionId) {
    jdbcTemplate.update("DELETE FROM questions WHERE session_id = ?", sessionId);
  }

  /** Deletes all questions from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM questions");
  }
}
