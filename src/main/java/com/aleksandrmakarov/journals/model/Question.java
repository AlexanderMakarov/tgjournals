package com.aleksandrmakarov.journals.model;

/**
 * Represents a question within a training session. Questions are categorized as BEFORE
 * (pre-session) or AFTER (post-session).
 *
 * @param id Unique identifier in the database
 * @param text The question text to be displayed to users
 * @param type Whether this is a BEFORE or AFTER question
 * @param orderIndex Order of the question within its type group
 * @param sessionId ID of the session this question belongs to
 */
public record Question(
    Long id, String text, QuestionType type, Integer orderIndex, Long sessionId) {}
