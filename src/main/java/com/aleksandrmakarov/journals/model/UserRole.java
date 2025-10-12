package com.aleksandrmakarov.journals.model;

/**
 * Defines the roles available in the journals system.
 *
 * <ul>
 *   <li>{@code COACH} - Can set questions and view all players' journals
 *   <li>{@code PLAYER} - Can answer questions and view their own journals
 * </ul>
 */
public enum UserRole {
  /**
   * Coach role with administrative privileges. Can create sessions, set questions, and view all
   * players' journals.
   */
  COACH,

  /**
   * Player role with standard user privileges. Can answer questions and view their own journals.
   */
  PLAYER
}
