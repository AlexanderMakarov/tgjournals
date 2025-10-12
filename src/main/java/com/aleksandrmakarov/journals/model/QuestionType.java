package com.aleksandrmakarov.journals.model;

/**
 * Defines the types of questions in a training session.
 *
 * <ul>
 *   <li>{@code BEFORE} - Questions answered before the training session
 *   <li>{@code AFTER} - Questions answered after the training session
 * </ul>
 */
public enum QuestionType {
  /**
   * Pre-session questions answered before training begins. Typically about goals, expectations, or
   * preparation.
   */
  BEFORE,

  /**
   * Post-session questions answered after training is complete. Typically about reflection,
   * achievements, and lessons learned.
   */
  AFTER
}
