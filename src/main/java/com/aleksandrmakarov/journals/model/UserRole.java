package com.aleksandrmakarov.journals.model;

/** Defines the roles available in the journals system. */
public enum UserRole {
	/**
	 * Coach role with administrative privileges. Can set questions, and view all
	 * players' journals.
	 */
	ADMIN,

	/**
	 * Player role with standard user privileges. Can answer questions and view
	 * their own journals.
	 */
	PLAYER,

	/**
	 * Banned user role. Can't use the bot.
	 */
	BANNED
}
