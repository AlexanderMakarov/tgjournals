package com.aleksandrmakarov.journals.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Test Journals Bot for integration testing. This bot captures the last response instead of sending
 * it to Telegram.
 */
@Configuration
public class TestJournalsBot extends com.aleksandrmakarov.journals.bot.JournalsBot {
  private String lastResponse;
  private InlineKeyboardMarkup lastInlineKeyboard;

  public TestJournalsBot(String botToken, String botUsername, String webhookPath) {
    super(botToken, botUsername, webhookPath);
  }

  @Override
  public void execute(BotApiMethod<?> method) {
    // Capture the response instead of sending to Telegram
    if (method instanceof SendMessage sendMessage) {
      lastResponse = sendMessage.getText();
      var replyMarkup = sendMessage.getReplyMarkup();
      if (replyMarkup instanceof InlineKeyboardMarkup inlineKeyboard) {
        lastInlineKeyboard = inlineKeyboard;
      } else {
        lastInlineKeyboard = null;
      }
    }
  }

  public String getLastResponse() {
    return lastResponse;
  }

  public InlineKeyboardMarkup getLastInlineKeyboard() {
    return lastInlineKeyboard;
  }

  @Bean
  @Primary
  public static TestJournalsBot testJournalsBot() {
    return new TestJournalsBot("test-token", "test-bot", "/webhook");
  }
}
