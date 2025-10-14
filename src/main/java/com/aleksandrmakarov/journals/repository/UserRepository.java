package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.util.TimestampUtils;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing User entities in the database. Provides CRUD operations and specialized
 * queries for user management. Uses JdbcTemplate for direct SQL operations with SQLite.
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<User> userRowMapper;

  @PostConstruct
  private void initRowMapper() {
    this.userRowMapper =
        (rs, rowNum) -> {
          return new User(
              rs.getLong("id"),
              rs.getLong("telegram_id"),
              rs.getString("username"),
              rs.getString("first_name"),
              rs.getString("last_name"),
              UserRole.valueOf(rs.getString("role")),
              TimestampUtils.fromTimestamp(rs.getTimestamp("created_at")),
              rs.getString("state_type") != null
                  ? com.aleksandrmakarov.journals.model.StateType.valueOf(
                      rs.getString("state_type"))
                  : null,
              rs.getObject("state_session_id") != null ? rs.getLong("state_session_id") : null,
              rs.getObject("state_question_index") != null
                  ? rs.getInt("state_question_index")
                  : null,
              TimestampUtils.fromTimestamp(rs.getTimestamp("state_updated_at")));
        };
  }

  /**
   * Finds a user by their Telegram ID.
   *
   * @param telegramId The Telegram user ID to search for
   * @return Optional containing the user if found, empty otherwise
   */
  public Optional<User> findByTelegramId(Long telegramId) {
    List<User> users =
        jdbcTemplate.query("SELECT * FROM users WHERE telegram_id = ?", userRowMapper, telegramId);
    return users.stream().findFirst();
  }

  /** Sets the single state for user, overwriting any existing one. */
  public void upsertState(Long userId, StateType stateType, Long sessionId, Integer questionIndex) {
    jdbcTemplate.update(
        "UPDATE users SET state_type = ?, state_session_id = ?, state_question_index = ?, state_updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        stateType != null ? stateType.name() : null,
        sessionId,
        questionIndex,
        userId);
  }

  /** Clears any state for user. */
  public void clearState(Long userId) {
    jdbcTemplate.update(
        "UPDATE users SET state_type = NULL, state_session_id = NULL, state_question_index = NULL, state_updated_at = NULL WHERE id = ?",
        userId);
  }

  /**
   * Saves a user to the database. If the user has no ID, creates a new record. Otherwise, updates
   * existing record.
   *
   * @param user The user to save
   * @return The saved user with generated ID if new
   */
  public User save(User user) {
    if (user.id() == null) {
      // Insert new user
      jdbcTemplate.update(
          "INSERT INTO users (telegram_id, username, first_name, last_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?)",
          user.telegramId(),
          user.username(),
          user.firstName(),
          user.lastName(),
          user.role().name(),
          TimestampUtils.toTimestamp(user.createdAt()));
      Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
      return new User(
          id,
          user.telegramId(),
          user.username(),
          user.firstName(),
          user.lastName(),
          user.role(),
          user.createdAt(),
          null,
          null,
          null,
          null);
    } else {
      // Update existing user
      jdbcTemplate.update(
          "UPDATE users SET username = ?, first_name = ?, last_name = ?, role = ? WHERE id = ?",
          user.username(),
          user.firstName(),
          user.lastName(),
          user.role().name(),
          user.id());
      return user;
    }
  }

  /**
   * Finds all players ordered by their last journal entry date. Players with no journals appear
   * last in the list.
   *
   * @return List of players sorted by last journal date (most recent first)
   */
  public List<User> findPlayersOrderedByLastJournal() {
    return jdbcTemplate.query(
        "SELECT u.* FROM users u "
            + "LEFT JOIN journals j ON u.id = j.user_id "
            + "WHERE u.role = 'PLAYER' "
            + "GROUP BY u.id "
            + "ORDER BY MAX(j.created_at) DESC NULLS LAST",
        userRowMapper);
  }

  /**
   * Counts the total number of users in the database.
   *
   * @return Total number of users
   */
  public long count() {
    Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    return result != null ? result : 0L;
  }

  /** Deletes all users from the database. Used primarily for testing. */
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM users");
  }
}
