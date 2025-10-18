package com.aleksandrmakarov.journals.bot;

import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/** Telegram bot that handles commands and messages from users. */
public class JournalsBot extends TelegramWebhookBot {

  private static final Logger logger = LoggerFactory.getLogger(JournalsBot.class);

  @Autowired private UserService userService;

  @Autowired private BotCommandHandler commandHandler;

  private final String botToken;
  private final String botUsername;
  private final String webhookPath;

  public JournalsBot(String botToken, String botUsername, String webhookPath) {
    super(botToken);
    this.botToken = botToken;
    this.botUsername = botUsername;
    this.webhookPath = webhookPath;
  }

  @Override
  public String getBotUsername() {
    return botUsername;
  }

  @Override
  public String getBotToken() {
    return this.botToken;
  }

  @Override
  public String getBotPath() {
    return webhookPath;
  }

  @Override
  public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
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
        logger.info("{} is answered: {}", logPrefix, response);
        
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
    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText(text);
    message.setParseMode("Markdown");
    return message;
  }
}
