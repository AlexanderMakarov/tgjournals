package com.aleksandrmakarov.journals.model;

/**
 * Represents a participant entry containing user information
 * and number of sessions participated in.
 *
 * @param user User information.
 * @param sessionCount Number of sessions participated in.
 */
public record Participant(User user, int sessionCount) {}
