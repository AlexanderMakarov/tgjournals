package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.util.TimestampUtils;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Repository for managing User entities in the database. */
@Repository
@RequiredArgsConstructor
public class UserRepository {

  private final JdbcTemplate jdbcTemplate;

  private RowMapper<User> userRowMapper;

  private RowMapper<Participant> participantRowMapper;

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

  @PostConstruct
  private void initParticipantRowMapper() {
    this.participantRowMapper =
        (rs, rowNum) -> {
          return new Participant(
            rs.getLong("id"),
            userRowMapper.mapRow(rs, rowNum),
            rs.getInt("session_count"));
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

  /**
   * Finds a user by their username.
   *
   * @param username The username to search for
   * @return Optional containing the user if found, empty otherwise
   */
  public Optional<User> findByUsername(String username) {
    List<User> users =
        jdbcTemplate.query("SELECT * FROM users WHERE username = ?", userRowMapper, username);
    return users.stream().findFirst();
  }

  /**
   * Finds all users by their role.
   *
   * @param role The role to search for
   * @return List of users with the given role
   */
  public List<User> findAllByRole(UserRole role) {
    return jdbcTemplate.query("SELECT * FROM users WHERE role = ?", userRowMapper, role.name());
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
  public void clearState(Long userId, boolean isClearQuestionIndex) {
    if (isClearQuestionIndex) {
      jdbcTemplate.update(
          "UPDATE users SET state_type = NULL, state_session_id = NULL, state_question_index = 0, state_updated_at = NULL WHERE id = ?",
          userId);
    } else {
      jdbcTemplate.update(
          "UPDATE users SET state_type = NULL, state_session_id = NULL, state_updated_at = NULL WHERE id = ?",
          userId);
    }
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
          "INSERT INTO users (telegram_id, username, first_name, last_name, role, created_at, state_question_index) VALUES (?, ?, ?, ?, ?, ?, ?)",
          user.telegramId(),
          user.username(),
          user.firstName(),
          user.lastName(),
          user.role().name(),
          TimestampUtils.toTimestamp(user.createdAt()),
          0);
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
          user.stateQuestionIndex(),
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
   * Finds all participants ordered by their last journal entry date.
   * For each participant, count the number of sessions participated in.
   *
   * @return List of participants sorted by last journal date (most recent first).
   */
  public List<Participant> findParticipantsOrderedByLastJournal() {
    return jdbcTemplate.query(
      "SELECT u.*, COUNT(DISTINCT j.session_id) as session_count FROM users u "
        + "LEFT JOIN journals j ON u.id = j.user_id "
        + "LEFT JOIN sessions s ON j.session_id = s.id "
        + "GROUP BY u.id "
        + "ORDER BY MAX(s.created_at) DESC NULLS LAST",
        participantRowMapper);
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
