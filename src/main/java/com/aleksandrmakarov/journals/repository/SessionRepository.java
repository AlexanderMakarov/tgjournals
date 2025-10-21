package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.util.TimestampUtils;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing Session entities in the database. Provides operations for session
 * management including active session tracking.
 */
@Repository
@RequiredArgsConstructor
public class SessionRepository implements SessionRepositoryInterface {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<Session> sessionRowMapper;

  @PostConstruct
  private void initRowMapper() {
    this.sessionRowMapper =
        (rs, rowNum) -> {
          return new Session(
              rs.getLong("id"),
              rs.getString("name"),
              TimestampUtils.fromTimestamp(rs.getTimestamp("created_at")),
              rs.getTimestamp("finished_at") != null
                  ? TimestampUtils.fromTimestamp(rs.getTimestamp("finished_at"))
                  : null);
        };
  }

  /**
   * Finds the currently active session. Only one session can be active at a time.
   *
   * @return Optional containing the active session if found, empty otherwise
   */
  public Optional<Session> findActiveSession() {
    List<Session> sessions =
        jdbcTemplate.query("SELECT * FROM sessions WHERE finished_at IS NULL", sessionRowMapper);
    return sessions.stream().findFirst();
  }

  /**
   * Saves a session to the database. If the session has no ID, creates a new record. Otherwise,
   * updates existing record.
   *
   * @param session The session to save
   * @return The saved session with generated ID if new
   */
  public Session save(Session session) {
    if (session.id() == null) {
      // Insert new session
      jdbcTemplate.update(
          "INSERT INTO sessions (name, created_at, finished_at) VALUES (?, ?, ?)",
          session.name(),
          TimestampUtils.toTimestamp(session.createdAt()),
          session.finishedAt() != null ? TimestampUtils.toTimestamp(session.finishedAt()) : null);
      Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
      return new Session(id, session.name(), session.createdAt(), session.finishedAt());
    } else {
      // Update existing session
      jdbcTemplate.update(
          "UPDATE sessions SET name = ?, created_at = ?, finished_at = ? WHERE id = ?",
          session.name(),
          TimestampUtils.toTimestamp(session.createdAt()),
          session.finishedAt() != null ? TimestampUtils.toTimestamp(session.finishedAt()) : null,
          session.id());
      return session;
    }
  }

  /**
   * Finishes all active sessions by setting finished_at timestamp. Used when creating a new active
   * session.
   */
  public void finishAllActiveSessions() {
    jdbcTemplate.update(
        "UPDATE sessions SET finished_at = CURRENT_TIMESTAMP WHERE finished_at IS NULL");
  }

  /**
   * Counts the total number of sessions in the database.
   *
   * @return Total number of sessions
   */
  public long count() {
    Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sessions", Long.class);
    return result != null ? result : 0L;
  }

  /**
   * Finds finished sessions ordered by creation date (most recent first).
   *
   * @return List of finished sessions ordered by created_at DESC
   */
  public List<Session> findFinishedSessionsOrderedByCreatedAt() {
    return jdbcTemplate.query(
        "SELECT * FROM sessions WHERE finished_at IS NOT NULL ORDER BY created_at DESC",
        sessionRowMapper);
  }

  /** Deletes all sessions from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM sessions");
  }
}
