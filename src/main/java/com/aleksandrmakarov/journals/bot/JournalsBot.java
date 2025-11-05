package com.aleksandrmakarov.journals.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.client.AbstractTelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.webhook.TelegramWebhookBot;

import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.UserService;

/** Telegram bot that handles commands and messages from users. */
public class JournalsBot implements TelegramWebhookBot {

	private static final Logger logger = LoggerFactory.getLogger(JournalsBot.class);

	@Autowired
	private UserService userService;

	@Autowired
	private BotCommandHandler commandHandler;

	private final String botToken;
	private final String botUsername;
	private final String webhookPath;
	private final AbstractTelegramClient telegramClient;

	public JournalsBot(String botToken, String botUsername, String webhookPath) {
		this.botToken = botToken;
		this.botUsername = botUsername;
		this.webhookPath = webhookPath;
		this.telegramClient = new OkHttpTelegramClient(botToken);
	}

	public String getBotUsername() {
		return botUsername;
	}

	public String getBotToken() {
		return this.botToken;
	}

	public void execute(BotApiMethod<?> method) {
		try {
			telegramClient.execute(method);
			logger.debug("Successfully sent message to Telegram");
		} catch (Exception e) {
			logger.error("Error executing Telegram API method: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to send message to Telegram", e);
		}
	}

	@Override
	public String getBotPath() {
		return webhookPath;
	}

	@Override
	public BotApiMethod<?> consumeUpdate(Update update) {

		// Check if the update has a callback query (button press on inline keyboard).
		if (update.hasCallbackQuery()) {
			CallbackQuery callbackQuery = update.getCallbackQuery();
			String callbackData = callbackQuery.getData();
			Long chatId = callbackQuery.getMessage().getChatId();
			Long userId = callbackQuery.getFrom().getId();
			String username = callbackQuery.getFrom().getUserName();
			String firstName = callbackQuery.getFrom().getFirstName();
			String lastName = callbackQuery.getFrom().getLastName();
			String queryId = callbackQuery.getId();

			// Register or update user.
			var user = userService.findOrCreateUser(userId, username, firstName, lastName);
			var logPrefix = "CallbackQuery from " + user.role() + " " + userId + " (" + username + ", " + firstName
					+ " " + lastName + ")";
			logger.info("{} received: '{}'", logPrefix, callbackData);

			// Get user locale from Telegram (defaults to "en" if not available)
			String locale = callbackQuery.getFrom().getLanguageCode();
			if (locale == null || locale.isEmpty()) {
				locale = "en";
			}

			// Check user is not banned.
			if (user.role() == UserRole.BANNED) {
				logger.info("{} is banned, skipping callback handling", logPrefix);
				String bannedMessage = commandHandler.getTranslation("bot.banned", locale);
				execute(AnswerCallbackQuery.builder().callbackQueryId(queryId).text(bannedMessage).build());
				return createSendMessage(chatId, bannedMessage);
			}

			try {
				BotResponse response = commandHandler.handleCallbackQuery(callbackData, user, locale);
				String text = response != null ? response.text() : null;
				logger.info("{} is answered: {}", logPrefix, (text == null ? "<null>" : text.replace("\n", "⏎")));

				if (text == null || text.trim().isEmpty()) {
					logger.warn("Empty response from callback handler for: {}", callbackData);
					text = commandHandler.getTranslation("bot.error.empty_response", locale);
				}

				// Answer the callback query to remove loading state
				execute(AnswerCallbackQuery.builder().callbackQueryId(queryId).build());

				return createSendMessage(chatId, text, response);
			} catch (ForbiddenException fe) {
				execute(AnswerCallbackQuery.builder().callbackQueryId(queryId).text(fe.getMessage()).showAlert(true)
						.build());
				return createSendMessage(chatId, fe.getMessage());
			} catch (Exception e) {
				logger.error("{} failed: {}", logPrefix, e.getMessage(), e);
				String errorMessage = commandHandler.getTranslation("bot.error.occurred", locale);
				execute(AnswerCallbackQuery.builder().callbackQueryId(queryId).text(errorMessage).showAlert(true)
						.build());
				return createSendMessage(chatId, errorMessage);
			}
		}

		// Check if the update has a message with text.
		if (update.hasMessage() && update.getMessage().getText() != null) {
			String messageText = update.getMessage().getText();
			Long chatId = update.getMessage().getChatId();
			Long userId = update.getMessage().getFrom().getId();
			String username = update.getMessage().getFrom().getUserName();
			String firstName = update.getMessage().getFrom().getFirstName();
			String lastName = update.getMessage().getFrom().getLastName();

			// Register or update user.
			var user = userService.findOrCreateUser(userId, username, firstName, lastName);
			var logPrefix = "Message from " + user.role() + " " + userId + " (" + username + ", " + firstName + " "
					+ lastName + ")";
			logger.info("{} received: '{}'", logPrefix, messageText);

			// Get user locale from Telegram (defaults to "en" if not available)
			String locale = update.getMessage().getFrom().getLanguageCode();
			if (locale == null || locale.isEmpty()) {
				locale = "en";
			}

			// Check user is not banned.
			if (user.role() == UserRole.BANNED) {
				logger.info("{} is banned, skipping command handling", logPrefix);
				String bannedMessage = commandHandler.getTranslation("bot.banned", locale);
				return createSendMessage(chatId, bannedMessage);
			}

			try {
				BotResponse response = commandHandler.handleCommand(messageText, user, update, locale);
				String text = response != null ? response.text() : null;
				logger.info("{} is answered: {}", logPrefix, (text == null ? "<null>" : text.replace("\n", "⏎")));

				if (text == null || text.trim().isEmpty()) {
					logger.warn("Empty response from command handler for: {}", messageText);
					text = commandHandler.getTranslation("bot.error.empty_response", locale);
				}

				return createSendMessage(chatId, text, response);
			} catch (ForbiddenException fe) {
				return createSendMessage(chatId, fe.getMessage());
			} catch (Exception e) {
				logger.error("{} failed: {}", logPrefix, e.getMessage(), e);
				String errorMessage = commandHandler.getTranslation("bot.error.occurred", locale);
				return createSendMessage(chatId, errorMessage);
			}
		} else {
			logger.warn(
					"Received update {} without text message. Update type: message={}, callbackQuery={}, editedMessage={}",
					update.getUpdateId(), update.hasMessage(), update.hasCallbackQuery(), update.hasEditedMessage());
		}
		return null;
	}

	private SendMessage createSendMessage(Long chatId, String text) {
		return createSendMessage(chatId, text, null);
	}

	private SendMessage createSendMessage(Long chatId, String text, BotResponse response) {
		SendMessage.SendMessageBuilder builder = SendMessage.builder().chatId(chatId.toString()).text(text)
				// Use HTML markup for formatting messages because "MarkdownV2"
				// https://core.telegram.org/bots/api#markdownv2-style
				// is too restrictive - need escape all [1..126] characters.
				.parseMode("HTML");
		if (response != null && response.hasKeyboard()) {
			builder.replyMarkup(response.inlineKeyboard());
		}
		return builder.build();
	}

	@Override
	public void runDeleteWebhook() {
		// Implementation for deleting webhook
		// This is typically handled by the application configuration
		logger.debug("Delete webhook requested");
	}

	@Override
	public void runSetWebhook() {
		// Implementation for setting webhook
		// This is typically handled by the application configuration
		logger.debug("Set webhook requested");
	}
}
