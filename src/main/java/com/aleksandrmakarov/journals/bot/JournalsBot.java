package com.aleksandrmakarov.journals.bot;

import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.client.AbstractTelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.webhook.TelegramWebhookBot;

/** Telegram bot that handles commands and messages from users. */
public class JournalsBot implements TelegramWebhookBot {

  private static final Logger logger = LoggerFactory.getLogger(JournalsBot.class);

  @Autowired private UserServiceImpl userService;

  @Autowired private BotCommandHandler commandHandler;

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
    logger.debug("Received webhook update: {}", update.getUpdateId());

    if (update.hasMessage() && update.getMessage().getText() != null) {
      String messageText = update.getMessage().getText();
      Long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();
      String username = update.getMessage().getFrom().getUserName();
      String firstName = update.getMessage().getFrom().getFirstName();
      String lastName = update.getMessage().getFrom().getLastName();

      // Register or update user
      var user = userService.findOrCreateUser(userId, username, firstName, lastName);
      var logPrefix =
          "Message from "
              + user.role()
              + " "
              + userId
              + " ("
              + username
              + ", "
              + firstName
              + " "
              + lastName
              + ")";
      logger.info("{} received: '{}'", logPrefix, messageText);
      try {
        String response = commandHandler.handleCommand(messageText, user, update);
        logger.info("{} is answered: {}", logPrefix, response.replace("\n", "⏎"));

        // Validate response
        if (response == null || response.trim().isEmpty()) {
          logger.warn("Empty response from command handler for: {}", messageText);
          response = "Sorry, I didn't understand that. Use /help to see available commands.";
        }

        return createSendMessage(chatId, response);
      } catch (ForbiddenException fe) {
        return createSendMessage(chatId, fe.getMessage());
      } catch (Exception e) {
        logger.error("{} failed: {}", logPrefix, e.getMessage(), e);
        return createSendMessage(chatId, "Sorry, an error occurred. Please try again.");
      }
    } else {
      logger.debug("Received update without text message: {}", update);
    }
    return null;
  }

  private SendMessage createSendMessage(Long chatId, String text) {
    return SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        // Used HTML markup for formatting messages because "MarkdownV2"
        // https://core.telegram.org/bots/api#markdownv2-style
        // is too restrictive - need escape all [1..126] characters.
        .parseMode("HTML")
        .build();
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
