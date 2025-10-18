package com.aleksandrmakarov.journals.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a list of journals for a session.
 *
 * @param sessionId The ID of the session.
 * @param sessionName The name of the session.
 * @param sessionDate The date of the session.
 * @param journals The list of journals with questions.
 */
public record SessionJournals(
    long sessionId,
    String sessionName,
    LocalDateTime sessionDate,
    List<JournalWithQuestion> journals) {}
