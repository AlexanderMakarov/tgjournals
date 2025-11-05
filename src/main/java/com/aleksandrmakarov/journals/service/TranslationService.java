package com.aleksandrmakarov.journals.service;

public interface TranslationService {

	/**
	 * Translates the given key to the given locale.
	 *
	 * @param key
	 *            The key to translate.
	 * @param locale
	 *            The locale to translate to.
	 * @param args
	 *            The arguments to substitute into the translated string.
	 * @return The translated string.
	 */
	String t(String key, String locale, Object... args);
}
