package com.aleksandrmakarov.journals.model;

/**
 * Represents a journal with its question type and text.
 *
 * @param journal      The journal.
 * @param questionType The type of the question.
 * @param question     The text of the question.
 */
public record JournalWithQuestion(Journal journal, QuestionType questionType, String question) {
}
