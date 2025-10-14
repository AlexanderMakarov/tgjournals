package com.aleksandrmakarov.journals.model;

import java.time.LocalDateTime;

/**
 * Represents a training session with associated questions and journals. Only one session can be
 * active at a time.
 *
 * @param id Unique identifier in the database
 * @param name Name of the session
 * @param createdAt Date and time when the session was created
 * @param finishedAt Date and time when the session was finished (null if still active)
 */
public record Session(Long id, String name, LocalDateTime createdAt, LocalDateTime finishedAt) {}
