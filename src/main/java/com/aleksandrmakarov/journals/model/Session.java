package com.aleksandrmakarov.journals.model;

import java.time.LocalDateTime;

/**
 * Represents a training session with associated questions and journals. Only one session can be
 * active at a time.
 *
 * @param id Unique identifier in the database
 * @param date Date and time when the session was created
 * @param isActive Whether this session is currently active (only one can be active)
 */
public record Session(Long id, LocalDateTime date, Boolean isActive) {}
