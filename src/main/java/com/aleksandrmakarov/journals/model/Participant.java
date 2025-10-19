package com.aleksandrmakarov.journals.model;

/**
 * Represents a participant entry containing user information and number of sessions participated in.
 *
 * @param id Unique identifier in the database
 * @param user User information
 * @param sessionCount Number of sessions participated in
 */
public record Participant(
    Long id,
    User user,
    int sessionCount) {}
