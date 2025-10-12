package com.aleksandrmakarov.journals.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for validating Telegram webhook requests using secret token authentication. This ensures
 * that only Telegram can send requests to our webhook endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSecurityService {

  @Value("${telegram.bot.webhook.secret:}")
  private String webhookSecret;

  /**
   * Validates the webhook request by checking the secret token.
   *
   * @param request The HTTP request
   * @return ValidationResult containing success status and reason
   */
  public ValidationResult validateWebhookRequest(HttpServletRequest request) {
    // If no secret is configured, allow all requests (development mode)
    if (!StringUtils.hasText(webhookSecret)) {
      return ValidationResult.success(
          "No webhook secret configured - allowing all requests (development mode)");
    }

    String telegramSecret = request.getHeader("X-Telegram-Bot-Api-Secret-Token");

    if (!StringUtils.hasText(telegramSecret)) {
      return ValidationResult.failure("Missing X-Telegram-Bot-Api-Secret-Token header");
    }

    // Simple string comparison
    boolean isValid = webhookSecret.equals(telegramSecret);

    if (!isValid) {
      return ValidationResult.failure("Invalid webhook secret token");
    } else {
      return ValidationResult.success("Webhook request validated successfully");
    }
  }

  /** Result of webhook validation containing success status and reason. */
  public static class ValidationResult {
    private final boolean valid;
    private final String reason;

    private ValidationResult(boolean valid, String reason) {
      this.valid = valid;
      this.reason = reason;
    }

    public static ValidationResult success(String reason) {
      return new ValidationResult(true, reason);
    }

    public static ValidationResult failure(String reason) {
      return new ValidationResult(false, reason);
    }

    public boolean isValid() {
      return valid;
    }

    public String getReason() {
      return reason;
    }
  }
}
