package com.aleksandrmakarov.journals.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Represents a response from the bot.
 *
 * @param text
 *            The text of the response.
 * @param inlineKeyboard
 *            The inline keyboard to be displayed with the response.
 */
public record BotResponse(String text, InlineKeyboardMarkup inlineKeyboard) {

	/**
	 * Creates a new BotResponse with the given text and no keyboard.
	 *
	 * @param text
	 *            The text of the response.
	 * @return A new BotResponse with the given text and no keyboard.
	 */
	public static BotResponse text(String text) {
		return new BotResponse(text, null);
	}

	/**
	 * Creates a new BotResponse with the given text and an inline keyboard.
	 *
	 * @param text
	 *            The text of the response.
	 * @param inlineKeyboard
	 *            The inline keyboard to be displayed with the response.
	 * @return A new BotResponse with the given text and inline keyboard.
	 */
	public static BotResponse withInlineKeyboard(String text, InlineKeyboardMarkup inlineKeyboard) {
		return new BotResponse(text, inlineKeyboard);
	}

	/**
	 * Checks if the response has a keyboard.
	 *
	 * @return True if the response has a keyboard, false otherwise.
	 */
	public boolean hasKeyboard() {
		return inlineKeyboard != null;
	}
}
