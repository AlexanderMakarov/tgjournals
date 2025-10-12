package com.aleksandrmakarov.journals.model;

import java.time.LocalDateTime;

/**
 * Represents a journal entry containing a user's answer to a specific question. Each journal entry
 * is linked to a user, session, and question.
 *
 * @param id Unique identifier in the database
 * @param answer The user's response to the question
 * @param createdAt Timestamp when the journal entry was created
 * @param userId ID of the user who created this journal entry
 * @param sessionId ID of the session this journal entry belongs to
 * @param questionId ID of the question this journal entry answers
 */
public record Journal(
    Long id,
    String answer,
    LocalDateTime createdAt,
    Long userId,
    Long sessionId,
    Long questionId) {}
