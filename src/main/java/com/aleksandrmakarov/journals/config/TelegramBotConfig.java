package com.aleksandrmakarov.journals.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aleksandrmakarov.journals.bot.JournalsBot;

/**
 * Configuration for Telegram bot using webhook mode. For webhook bots, we don't
 * need to register with TelegramBotsApi. The webhook controller handles
 * incoming updates directly.
 */
@Configuration(proxyBeanMethods = false)
public class TelegramBotConfig {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${telegram.bot.username}")
	private String botUsername;

	@Value("${telegram.bot.webhook.path:/webhook}")
	private String webhookPath;

	@Bean
	public JournalsBot journalsBot() {
		return new JournalsBot(botToken, botUsername, webhookPath);
	}
}
