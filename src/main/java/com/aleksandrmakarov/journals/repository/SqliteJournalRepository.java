package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.JournalWithQuestion;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.SessionJournals;
import com.aleksandrmakarov.journals.util.TimestampUtils;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SqliteJournalRepository implements JournalRepository {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<Journal> journalRowMapper;

  @PostConstruct
  private void initRowMapper() {
    this.journalRowMapper =
        (rs, rowNum) -> {
          return new Journal(
              rs.getLong("id"),
              rs.getString("answer"),
              TimestampUtils.fromTimestamp(rs.getTimestamp("created_at")),
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
          TimestampUtils.toTimestamp(journal.createdAt()),
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
          TimestampUtils.toTimestamp(journal.createdAt()),
          journal.userId(),
          journal.sessionId(),
          journal.questionId(),
          journal.id());
      return journal;
    }
  }

  public List<Long> saveBatch(List<Journal> journals) {
    if (journals == null || journals.isEmpty()) {
      return java.util.Collections.emptyList();
    }

    StringBuilder sql =
        new StringBuilder(
            "INSERT INTO journals (answer, created_at, user_id, session_id, question_id) VALUES ");
    String sep = "";
    for (int i = 0; i < journals.size(); i++) {
      sql.append(sep).append("(?, ?, ?, ?, ?)");
      sep = ", ";
    }
    sql.append(" RETURNING id");

    java.util.List<Object> params = new java.util.ArrayList<>(journals.size() * 5);
    for (Journal j : journals) {
      params.add(j.answer());
      params.add(TimestampUtils.toTimestamp(j.createdAt()));
      params.add(j.userId());
      params.add(j.sessionId());
      params.add(j.questionId());
    }

    return jdbcTemplate.query(
        sql.toString(),
        ps -> {
          Object[] arr = params.toArray();
          for (int i = 0; i < arr.length; i++) {
            ps.setObject(i + 1, arr[i]);
          }
        },
        (rs, rowNum) -> rs.getLong("id"));
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

  public List<SessionJournals> findLastNJournalsPerUser(Long userId, int limitLastSessions) {
    // SQLite-compatible query: pick `limitLastSessions` latest sessions by MAX(created_at),
    // then list journals within them.
    String sql =
        """
        WITH latest_sessions AS (
          SELECT session_id, MAX(created_at) AS last_created_at
          FROM journals
          WHERE user_id = ?
          GROUP BY session_id
          ORDER BY last_created_at DESC
          LIMIT ?
        )
        SELECT j.session_id,
               s.name AS session_name,
               s.created_at AS session_date,
               j.id AS journal_id,
               j.answer,
               j.created_at,
               j.user_id,
               q.type AS question_type,
               q.text AS question_text
        FROM journals j
        INNER JOIN latest_sessions ls ON j.session_id = ls.session_id
        INNER JOIN questions q ON j.question_id = q.id
        INNER JOIN sessions s ON s.id = j.session_id
        WHERE j.user_id = ?
        ORDER BY j.created_at ASC
        """;
    // Parsing rows into auxiliary record.
    record Row(
        long sessionId,
        String sessionName,
        LocalDateTime sessionDate,
        long journalId,
        String answer,
        LocalDateTime createdAt,
        Long userId,
        QuestionType questionType,
        String questionText) {}
    List<Row> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("session_id"),
                    rs.getString("session_name"),
                    TimestampUtils.fromTimestamp(rs.getTimestamp("session_date")),
                    rs.getLong("journal_id"),
                    rs.getString("answer"),
                    TimestampUtils.fromTimestamp(rs.getTimestamp("created_at")),
                    rs.getLong("user_id"),
                    QuestionType.valueOf(rs.getString("question_type")),
                    rs.getString("question_text")),
            userId,
            limitLastSessions,
            userId);
    // Aggregate rows by sessionId. We know that they are already sorted.
    List<SessionJournals> result = new java.util.ArrayList<>();
    SessionJournals currentSessionJournals = null;
    for (Row row : rows) {
      if (currentSessionJournals == null || currentSessionJournals.sessionId() != row.sessionId()) {
        var journals = new ArrayList<JournalWithQuestion>();
        Journal journal =
            new Journal(
                row.journalId, row.answer, row.createdAt, row.userId, row.sessionId, row.journalId);
        JournalWithQuestion journalWithQuestion =
            new JournalWithQuestion(journal, row.questionType, row.questionText);
        journals.add(journalWithQuestion);
        currentSessionJournals =
            new SessionJournals(row.sessionId(), row.sessionName(), row.sessionDate, journals);
        result.add(currentSessionJournals);
      } else {
        Journal journal =
            new Journal(
                row.journalId, row.answer, row.createdAt, row.userId, row.sessionId, row.journalId);
        JournalWithQuestion journalWithQuestion =
            new JournalWithQuestion(journal, row.questionType, row.questionText);
        currentSessionJournals.journals().add(journalWithQuestion);
      }
    }
    return result;
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
