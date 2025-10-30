package com.aleksandrmakarov.journals.controller;

import com.aleksandrmakarov.journals.bot.JournalsBot;
import com.aleksandrmakarov.journals.security.WebhookSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Webhook controller for handling Telegram bot updates. Receives updates from Telegram and
 * processes them through the bot.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

  private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

  @Autowired private JournalsBot bot;
  @Autowired private WebhookSecurityService webhookSecurityService;

  /**
   * Handles incoming webhook updates from Telegram. Validates the request using webhook secret
   * token for security.
   *
   * @param update The Telegram update object
   * @param request The HTTP request for security validation
   * @return ResponseEntity with the bot's response or OK status
   */
  @PostMapping
  public ResponseEntity<?> webhook(@RequestBody Update update, HttpServletRequest request) {

    // Validate webhook security
    var validationResult = webhookSecurityService.validateWebhookRequest(request);
    if (!validationResult.isValid()) {
      logger.warn(
          "Unauthorized webhook request from IP: {} - {}",
          request.getRemoteAddr(),
          validationResult.getReason());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
    }

    // Log update details.
    StringBuilder details = new StringBuilder();
    var message = update.getMessage();
    if (message != null) {
      details.append("messageId=").append(message.getMessageId()).append(", ");
      var from = message.getFrom();
      if (from != null) {
        details.append("fromId=").append(from.getId()).append(", ");
        details.append("fromUsername=").append(from.getUserName()).append(", ");
        details.append("fromFirstName=").append(from.getFirstName()).append(", ");
        details.append("fromLastName=").append(from.getLastName()).append(", ");
      }
      var chat = message.getChat();
      if (chat != null) {
        details.append("chatId=").append(chat.getId()).append(", ");
      }
      details.append("text=").append(message.getText()).append(", ");
    } else {
      details.append("no message");
    }
    logger.info("Processing update {}: {}", update.getUpdateId(), details.toString());

    try {
      // Process the webhook update through the bot
      BotApiMethod<?> response = bot.consumeUpdate(update);

      if (response != null) {
        // Send the response to Telegram using the bot's API.
        bot.execute(response);
      } else {
        logger.warn("Null response generated for update id={}", update.getUpdateId());
      }

      // Always return OK to Telegram.
      return ResponseEntity.ok("OK");
    } catch (Exception e) {
      logger.error(
          "Error processing webhook update {}: {}", update.getUpdateId(), e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error processing webhook: " + e.getMessage());
    }
  }
}
