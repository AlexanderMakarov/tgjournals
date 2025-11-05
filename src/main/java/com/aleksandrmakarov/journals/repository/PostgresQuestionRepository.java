package com.aleksandrmakarov.journals.repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;

/**
 * Postgres question repository implementation.
 */
@Repository
public class PostgresQuestionRepository implements QuestionRepository {

	private final JdbcTemplate jdbcTemplate;

	public PostgresQuestionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final RowMapper<Question> QUESTION_ROW_MAPPER = (rs, rowNum) -> new Question(rs.getLong("id"),
			rs.getString("text"), QuestionType.valueOf(rs.getString("type")), rs.getInt("order_index"),
			rs.getLong("session_id"));

	public List<Question> findBySessionIdOrderByOrderIndex(Long sessionId) {
		return jdbcTemplate.query("SELECT * FROM questions WHERE session_id = ? ORDER BY order_index",
				QUESTION_ROW_MAPPER, sessionId);
	}

	public List<Question> findBySessionIdAndQuestionTypeOrderByOrderIndex(Long sessionId, QuestionType type) {
		return jdbcTemplate.query("SELECT * FROM questions WHERE session_id = ? ORDER BY order_index",
				QUESTION_ROW_MAPPER, sessionId);
	}

	@Override
	public Question save(Question question) {
		if (question.id() == null) {
			// Insert new question
			Long id = jdbcTemplate.queryForObject(
					"INSERT INTO questions (text, type, order_index, session_id) VALUES (?, ?, ?, ?) RETURNING id",
					Long.class, question.text(), question.type().name(), question.orderIndex(), question.sessionId());
			return new Question(id, question.text(), question.type(), question.orderIndex(), question.sessionId());
		} else {
			// Update existing question
			jdbcTemplate.update("UPDATE questions SET text = ?, type = ?, order_index = ?, session_id = ? WHERE id = ?",
					question.text(), question.type().name(), question.orderIndex(), question.sessionId(),
					question.id());
			return question;
		}
	}

	@Override
	public List<Long> saveBatch(List<Question> questions) {
		if (questions == null || questions.isEmpty()) {
			return Collections.emptyList();
		}

		String placeholders = IntStream.range(0, questions.size()).mapToObj(i -> "(?, ?, ?, ?)")
				.collect(Collectors.joining(", "));
		String sql = "INSERT INTO questions (text, type, order_index, session_id) VALUES " + placeholders
				+ " RETURNING id";

		List<Object> params = questions.stream()
				.flatMap(q -> Stream.of(q.text(), q.type().name(), q.orderIndex(), q.sessionId()))
				.collect(Collectors.toList());

		return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), params.toArray());
	}

	@Override
	public void deleteBySessionId(Long sessionId) {
		jdbcTemplate.update("DELETE FROM questions WHERE session_id = ?", sessionId);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM questions");
	}
}
