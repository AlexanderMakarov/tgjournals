package com.aleksandrmakarov.journals.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class TranslationServiceImpl implements TranslationService {

	private final MessageSource messageSource;

	public TranslationServiceImpl(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	@Override
	public String t(String key, String locale, Object... args) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		if (locale == null || locale.isEmpty()) {
			locale = "en";
		}
		Locale targetLocale = parseLocale(locale);
		try {
			return messageSource.getMessage(key, args, targetLocale);
		} catch (Exception e) {
			return messageSource.getMessage(key, args, Locale.ENGLISH);
		}
	}

	private Locale parseLocale(String languageCode) {
		if (languageCode == null || languageCode.isEmpty()) {
			return Locale.ENGLISH;
		}
		String normalizedCode = languageCode.toLowerCase().trim();
		if (normalizedCode.contains("-")) {
			String[] parts = normalizedCode.split("-");
			return Locale.forLanguageTag(parts[0] + "-" + parts[1]);
		}
		return Locale.ENGLISH;
	}
}
