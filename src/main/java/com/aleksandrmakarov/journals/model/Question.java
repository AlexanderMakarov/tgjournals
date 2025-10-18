package com.aleksandrmakarov.journals.model;

/**
 * Represents a question within a training session.
 *
 * @param id Unique identifier in the database.
 * @param text The question text to be displayed.
 * @param type Type of question.
 * @param orderIndex Order of the question within its type group. Starts with 1.
 * @param sessionId ID of the session this question belongs to.
 */
public record Question(
    Long id, String text, QuestionType type, Integer orderIndex, Long sessionId) {}
