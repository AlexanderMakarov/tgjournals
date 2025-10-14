package com.aleksandrmakarov.journals.model;

import java.time.LocalDateTime;

/**
 * Represents a user in the journals system. Can be either a COACH or PLAYER with different
 * permissions.
 *
 * @param id Unique identifier in the database
 * @param telegramId Telegram user ID from the bot API
 * @param username Telegram username (without @)
 * @param firstName User's first name
 * @param lastName User's last name
 * @param role User role (COACH or PLAYER)
 * @param createdAt Timestamp when the user was first registered
 */
public record User(
    Long id,
    Long telegramId,
    String username,
    String firstName,
    String lastName,
    UserRole role,
    LocalDateTime createdAt,
    StateType stateType,
    Long stateSessionId,
    Integer stateQuestionIndex,
    LocalDateTime stateUpdatedAt) {

  /**
   * Returns a formatted display name for the user. Combines first and last name, or falls back to
   * username, or "Unknown".
   *
   * @return Formatted display name
   */
  public String getDisplayName() {
    StringBuilder name = new StringBuilder();
    if (firstName != null) name.append(firstName);
    if (lastName != null) {
      if (name.length() > 0) name.append(" ");
      name.append(lastName);
    }
    if (name.length() == 0 && username != null) {
      name.append("@").append(username);
    }
    return name.length() > 0 ? name.toString() : "Unknown";
  }
}
