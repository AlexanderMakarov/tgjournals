package com.aleksandrmakarov.journals.controller;

import com.aleksandrmakarov.journals.bot.JournalsBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
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

  /**
   * Handles incoming webhook updates from Telegram.
   *
   * @param update The Telegram update object
   * @return ResponseEntity with the bot's response or OK status
   */
  @PostMapping
  public ResponseEntity<?> webhook(@RequestBody Update update) {
    logger.debug("Received webhook request with update ID: {}", update.getUpdateId());

    try {
      // Process the webhook update through the bot
      BotApiMethod<?> response = bot.onWebhookUpdateReceived(update);

      if (response != null) {
        logger.debug("Webhook processed successfully, returning bot response");
        return ResponseEntity.ok(response);
      } else {
        logger.debug("Webhook processed successfully, no response needed");
        return ResponseEntity.ok("OK");
      }
    } catch (Exception e) {
      logger.error(
          "Error processing webhook update {}: {}", update.getUpdateId(), e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error processing webhook: " + e.getMessage());
    }
  }
}
