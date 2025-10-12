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
public class SessionRepository {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<Session> sessionRowMapper;

  @PostConstruct
  private void initRowMapper() {
    this.sessionRowMapper =
        (rs, rowNum) -> {
          return new Session(
              rs.getLong("id"),
              TimestampUtils.fromTimestamp(rs.getTimestamp("date")),
              rs.getBoolean("is_active"));
        };
  }

  /**
   * Finds the currently active session. Only one session can be active at a time.
   *
   * @return Optional containing the active session if found, empty otherwise
   */
  public Optional<Session> findByIsActiveTrue() {
    List<Session> sessions =
        jdbcTemplate.query("SELECT * FROM sessions WHERE is_active = 1", sessionRowMapper);
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
          "INSERT INTO sessions (date, is_active) VALUES (?, ?)",
          TimestampUtils.toTimestamp(session.date()),
          session.isActive());
      Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
      return new Session(id, session.date(), session.isActive());
    } else {
      // Update existing session
      jdbcTemplate.update(
          "UPDATE sessions SET date = ?, is_active = ? WHERE id = ?",
          TimestampUtils.toTimestamp(session.date()),
          session.isActive(),
          session.id());
      return session;
    }
  }

  /**
   * Deactivates all sessions by setting is_active to false. Used when creating a new active
   * session.
   */
  public void deactivateAllSessions() {
    jdbcTemplate.update("UPDATE sessions SET is_active = 0");
  }

  /**
   * Counts the total number of sessions in the database.
   *
   * @return Total number of sessions
   */
  public long count() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sessions", Long.class);
  }

  /** Deletes all sessions from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM sessions");
  }
}
