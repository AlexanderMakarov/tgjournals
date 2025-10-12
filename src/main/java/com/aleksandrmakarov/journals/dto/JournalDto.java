package com.aleksandrmakarov.journals.dto;

import com.aleksandrmakarov.journals.model.Journal;
import java.time.LocalDateTime;

public record JournalDto(
    Long id, String answer, LocalDateTime createdAt, String questionText, String sessionDate) {
  public static JournalDto from(Journal journal) {
    return new JournalDto(
        journal.id(),
        journal.answer(),
        journal.createdAt(),
        "Question ID: " + journal.questionId(), // Simplified for now
        "Session ID: " + journal.sessionId() // Simplified for now
        );
  }
}
