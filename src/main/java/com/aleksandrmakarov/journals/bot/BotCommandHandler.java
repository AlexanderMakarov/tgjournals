package com.aleksandrmakarov.journals.bot;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.aleksandrmakarov.journals.model.JournalWithQuestion;
import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.SessionJournals;
import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.HealthService;
import com.aleksandrmakarov.journals.service.JournalService;
import com.aleksandrmakarov.journals.service.SessionService;
import com.aleksandrmakarov.journals.service.TranslationService;
import com.aleksandrmakarov.journals.service.UserService;

/**
 * Handler for bot commands. Used HTML markup for formatting messages because
 * "MarkdownV2" https://core.telegram.org/bots/api#markdownv2-style is too
 * restrictive - need escape all [1..126] characters.
 */
@Service
public class BotCommandHandler {

	private static final Logger logger = LoggerFactory.getLogger(BotCommandHandler.class);

	/** Record containing session display information and metadata. */
	public record SessionDisplayResult(String displayText, Session session, boolean hasQuestions) {
	}

	@Autowired
	private UserService userService;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private JournalService journalService;

	@Autowired
	private TranslationService translationService;

	@Autowired
	private HealthService healthService;

	public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private void requireAdmin(User user, String locale) {
		if (user == null || user.role() != UserRole.ADMIN) {
			throw new ForbiddenException(translationService.t("bot.forbidden", locale));
		}
	}

	public String getTranslation(String key, String locale, Object... args) {
		return translationService.t(key, locale, args);
	}

	/**
	 * Handles a callback query (button press on inline keyboard).
	 *
	 * @param callbackData
	 *            The callback data from the button
	 * @param user
	 *            The user who pressed the button
	 * @param locale
	 *            The user's locale
	 * @return BotResponse with the result
	 */
	public BotResponse handleCallbackQuery(String callbackData, User user, String locale) {
		if (callbackData == null || !callbackData.startsWith("ps:")) {
			return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
		}

		String[] parts = callbackData.split(":");
		if (parts.length < 2) {
			return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
		}

		String action = parts[1];

		if ("cancel".equals(action)) {
			userService.clearUserState(user.id(), false);
			return BotResponse.text(translationService.t("bot.command.unknown", locale));
		}

		if ("page".equals(action) && parts.length >= 3) {
			try {
				int pageIndex = Integer.parseInt(parts[2]);
				String payload = user.statePayload();
				if (payload == null) {
					payload = "bot.participants.title";
				}
				userService.setParticipantSelectState(user.id(), payload, pageIndex);
				String titleKey = payload.startsWith("LAST:")
						? "bot.journals.last"
						: (payload.startsWith("LAST5:")
								? "bot.journals.last5"
								: (payload.startsWith("LAST50:")
										? "bot.journals.last50"
										: (payload.equals("PROMOTE") || payload.equals("BAN") || payload.equals("UNBAN")
												? "bot.participants.title"
												: "bot.participants.title")));
				return buildParticipantsPageResponse(pageIndex, locale, titleKey);
			} catch (NumberFormatException e) {
				return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
			}
		}

		if ("select".equals(action) && parts.length >= 3) {
			try {
				Long targetUserId = Long.parseLong(parts[2]);
				// Find user by database ID from participants list
				List<Participant> participants = userService.getParticipantsOrderedByLastJournal();
				User target = participants.stream().filter(p -> p.user().id().equals(targetUserId))
						.map(Participant::user).findFirst().orElse(null);

				if (target == null) {
					return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
				}

				String payload = user.statePayload();
				if (payload == null) {
					userService.clearUserState(user.id(), false);
					return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
				}

				if (payload.equals("PROMOTE")) {
					userService.changeRole(target, UserRole.ADMIN);
					userService.clearUserState(user.id(), false);
					return BotResponse
							.text(translationService.t("bot.promote.success", locale, target.getDisplayName()));
				} else if (payload.equals("BAN")) {
					userService.changeRole(target, UserRole.BANNED);
					userService.clearUserState(user.id(), false);
					return BotResponse.text(translationService.t("bot.ban.success", locale, target.getDisplayName()));
				} else if (payload.equals("UNBAN")) {
					userService.changeRole(target, UserRole.PLAYER);
					userService.clearUserState(user.id(), false);
					return BotResponse.text(translationService.t("bot.unban.success", locale, target.getDisplayName()));
				} else if (payload.startsWith("LAST:")) {
					int count = Integer.parseInt(payload.substring("LAST:".length()));
					List<SessionJournals> journals = sessionService.getJournalsForLastSessions(target.id(), count);
					userService.clearUserState(user.id(), false);
					String key = count == 1
							? "bot.journals.last"
							: (count == 5 ? "bot.journals.last5" : "bot.journals.last50");
					return BotResponse.text(formatJournalsForDisplay(key, journals, locale));
				}
			} catch (NumberFormatException e) {
				return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
			}
		}

		return BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
	}

	/**
	 * Handles the command received from the user.
	 *
	 * @param messageText
	 *            The text of the message received from the user.
	 * @param user
	 *            The user who sent the message.
	 * @param update
	 *            The update received from the user.
	 * @param locale
	 *            The locale of the user.
	 * @return The response to the user.
	 */
	public BotResponse handleCommand(String messageText, User user, Update update, String locale) {
		// If user is in an active state and sends a command, clear the state (cancel
		// the flow).
		if (messageText != null && messageText.startsWith("/") && user.stateType() != null) {
			userService.clearUserState(user.id(), user.stateType() == StateType.QA_FLOW);
		}
		if (messageText == null) {
			return handleTextInput(user, null, locale);
		}
		String command = messageText.split(" ")[0].toLowerCase();

		switch (command) {
			case "/start" :
				return BotResponse.text(translationService.t("bot.welcome", locale));

			case "/help" :
				return BotResponse.text(getHelpMessage(user.role(), locale));

			case "/admins" :
				return BotResponse.text(handleAdminsCommand(user, locale));

			case "/set_questions" :
				return BotResponse.text(handleSetQuestionsCommand(user, messageText, locale));

			case "/before" :
				return BotResponse.text(handleBeforeCommand(user, locale));

			case "/after" :
				return BotResponse.text(handleAfterCommand(user, locale));

			case "/last5" :
				return handleLast5CommandResponse(user, locale);

			case "/last" :
				return handleLastCommandResponse(user, locale);

			case "/last50" :
				return handleLast50CommandResponse(user, locale);

			case "/participants" :
				return BotResponse.text(handleParticipantsCommand(user, locale));

			case "/promote" :
				return handlePromoteCommandResponse(user, messageText, locale);

			case "/ban" :
				return handleBanCommandResponse(user, messageText, locale);

			case "/unban" :
				return handleUnbanCommandResponse(user, messageText, locale);

			case "/session" :
				return BotResponse.text(handleSessionCommand(user, messageText, locale));

			case "/status" :
				return BotResponse.text(handleStatusCommand(user, locale));

			default :
				if (messageText.startsWith("/")) {
					return BotResponse.text(translationService.t("bot.command.unknown", locale));
				}
				return handleTextInput(user, messageText, locale);
		}
	}

	private String getHelpMessage(UserRole role, String locale) {
		StringBuilder help = new StringBuilder(translationService.t("bot.help.intro", locale) + "\n\n");

		if (role == UserRole.ADMIN) {
			help.append(translationService.t("bot.help.admin.title", locale)).append("\n");
			help.append("/session - ").append(translationService.t("bot.help.admin.session", locale)).append("\n");
			help.append("/set_questions - ").append(translationService.t("bot.help.admin.set_questions", locale))
					.append("\n");
			help.append("/participants - ").append(translationService.t("bot.help.admin.participants", locale))
					.append("\n");
			help.append("/promote - ").append(translationService.t("bot.help.admin.promote", locale)).append("\n");
			help.append("/ban - ").append(translationService.t("bot.help.admin.ban", locale)).append("\n");
			help.append("/unban - ").append(translationService.t("bot.help.admin.unban", locale)).append("\n");
			help.append("/status - ").append(translationService.t("bot.help.admin.status", locale)).append("\n\n");
		}

		help.append(translationService.t("bot.help.player.title", locale)).append("\n");
		help.append("/before - ").append(translationService.t("bot.help.player.before", locale)).append("\n");
		help.append("/after - ").append(translationService.t("bot.help.player.after", locale)).append("\n");
		help.append("/last - ").append(translationService.t("bot.help.player.last", locale)).append("\n");
		help.append("/last5 - ").append(translationService.t("bot.help.player.last5", locale)).append("\n");
		help.append("/last50 - ").append(translationService.t("bot.help.player.last50", locale)).append("\n");
		help.append("/admins - ").append(translationService.t("bot.help.player.admins", locale));

		return help.toString();
	}

	/**
	 * Handles the `/status` command. Only for admins. Returns health status
	 * information as text.
	 */
	private String handleStatusCommand(User user, String locale) {
		requireAdmin(user, locale);
		Map<String, Object> healthStatus = healthService.getHealthStatus();
		StringBuilder response = new StringBuilder();
		response.append("📊 <b>Service Status</b>\n\n");
		for (Map.Entry<String, Object> entry : healthStatus.entrySet()) {
			String key = capitalizeFirst(entry.getKey());
			Object value = entry.getValue();
			response.append(key).append(": ").append(value != null ? value.toString() : "null").append("\n");
		}
		return response.toString().trim();
	}

	private String capitalizeFirst(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	/**
	 * Handles the `/session` command. Only for admins. If no arguments - prints
	 * current session and questions. If name is provided - finishes current session
	 * and creates new one.
	 */
	private String handleSessionCommand(User user, String messageText, String locale) {
		requireAdmin(user, locale);
		String[] parts = messageText.split(" ", 2);

		// If no session name - just print current session (if exists).
		if (parts.length == 1) {
			var activeSession = sessionService.getActiveSession();
			if (activeSession == null) {
				return translationService.t("bot.session.not_found", locale);
			}
			SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession, locale);
			StringBuilder response = new StringBuilder(displayResult.displayText());
			if (!displayResult.hasQuestions()) {
				response.append("\n\n").append(translationService.t("bot.session.questions.not_found", locale));
			}
			return response.toString();
		}

		// Otherwise finish current session and ...
		String newSessionName = parts[1].trim();
		StringBuilder response = new StringBuilder();
		Session finishedSession = sessionService.finishActiveSession();
		if (finishedSession != null) {
			response.append(translationService.t("bot.session.finished", locale, finishedSession.name()))
					.append("\n\n");
		}

		// ... create new session.
		Session session = sessionService.createNewSession(newSessionName);
		response.append(translationService.t("bot.session.created", locale, newSessionName)).append("\n\n");
		SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(session, locale);
		response.append(displayResult.displayText());
		if (!displayResult.hasQuestions()) {
			response.append(translationService.t("bot.session.questions.not_found", locale));
		} else {
			response.append("\n").append(translationService.t("bot.session.questions.update_hint", locale));
		}
		return response.toString();
	}

	/**
	 * Handles the `/set_questions` command. Only for admins. Prints current session
	 * and questions, explanation, and switches user to "question update" mode.
	 */
	private String handleSetQuestionsCommand(User user, @SuppressWarnings("unused") String messageText, String locale) {
		requireAdmin(user, locale);

		// Get active session.
		Session activeSession = sessionService.getActiveSession();
		if (activeSession == null) {
			return translationService.t("bot.session.set_questions.not_found", locale);
		}

		// Add to response current session with questions.
		SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession, locale);
		StringBuilder response = new StringBuilder(displayResult.displayText());

		// Add explanation for questions update and return response.
		String explanation = translationService.t("bot.session.questions.update_explanation", locale);
		if (explanation.startsWith("\n\n")) {
			explanation = explanation.substring(2);
		}
		response.append("\n").append(explanation);
		userService.setQuestionsUpdateMode(user.id(), activeSession.id());
		return response.toString();
	}

	/**
	 * Handles the `/before` command. Checks what questions are available and
	 * switches user to "question flow" mode.
	 */
	private String handleBeforeCommand(User user, String locale) {
		Session activeSession = sessionService.getActiveSession();
		if (activeSession == null) {
			return translationService.t("bot.before.no_session", locale);
		}

		// Get questions and check we have at least one 'before' question.
		List<Question> questions = sessionService.getQuestions(activeSession.id());
		if (questions.isEmpty()) {
			return translationService.t("bot.before.no_questions", locale);
		} else if (questions.get(0).type() == QuestionType.AFTER) {
			return translationService.t("bot.before.no_before_questions", locale);
		}

		// Update user state and start flow of answering questions.
		userService.setQuestionFlowState(user.id(), activeSession.id(), 0);
		return translationService.t("bot.before.start", locale, activeSession.name(),
				activeSession.createdAt().format(DATETIME_FORMATTER), questions.get(0).text());
	}

	/**
	 * Handles the `/after` command. Checks what questions are available and
	 * switches user to "question flow" mode.
	 */
	private String handleAfterCommand(User user, String locale) {
		Session activeSession = sessionService.getActiveSession();
		if (activeSession == null) {
			return translationService.t("bot.after.no_session", locale);
		}

		// Get questions and check we have at least one 'after' question.
		List<Question> questions = sessionService.getQuestions(activeSession.id());
		if (questions.isEmpty()) {
			return translationService.t("bot.after.no_questions", locale);
		}

		// Switch to next quesion (stored only "asked" one).
		int currentIndex = user.stateQuestionIndex() + 1;
		if (currentIndex < 0) {
			// Handle case when 'after' is the first question.
			currentIndex = 0;
		}

		// Check 'after' question(s) exists and it is of right type.
		if (questions.size() <= currentIndex) {
			return translationService.t("bot.after.no_after_questions", locale);
		}
		Question currentQuestion = questions.get(currentIndex);
		if (currentQuestion.type() != QuestionType.AFTER) {
			return translationService.t("bot.after.wrong_type", locale);
		}

		// Update user state and start flow of answering questions.
		userService.setQuestionFlowState(user.id(), activeSession.id(), currentIndex);
		return translationService.t("bot.after.start", locale, activeSession.name(),
				activeSession.createdAt().format(DATETIME_FORMATTER), currentQuestion.text());
	}

	private String formatJournalsForDisplay(String prefixKey, List<SessionJournals> sessionJournals, String locale) {
		String prefix = translationService.t(prefixKey, locale);
		if (sessionJournals.isEmpty()) {
			return translationService.t("bot.journals.not_found", locale, prefix);
		}

		StringBuilder sb = new StringBuilder(prefix).append(":\n\n");
		for (SessionJournals sessionJournal : sessionJournals) {
			StringBuilder journalEntries = new StringBuilder();
			for (JournalWithQuestion journalWithQuestion : sessionJournal.journals()) {
				journalEntries.append(translationService.t("bot.journals.entry", locale,
						journalWithQuestion.questionType().toString(), journalWithQuestion.question(),
						journalWithQuestion.journal().answer())).append("\n");
			}
			sb.append(translationService.t("bot.journals.format", locale,
					sessionJournal.sessionDate().format(DATETIME_FORMATTER), sessionJournal.sessionName(),
					journalEntries.toString()));
		}
		return sb.toString();
	}

	/** Handles the `/last` command. Returns last journal for the user. */
	private BotResponse handleLastCommandResponse(User user, String locale) {
		if (user.role() == UserRole.ADMIN) {
			userService.setParticipantSelectState(user.id(), "LAST:1", 0);
			return buildParticipantsPageResponse(0, locale, "bot.journals.last");
		}
		List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 1);
		return BotResponse.text(formatJournalsForDisplay("bot.journals.last", journals, locale));
	}

	/** Handles the `/last5` command. Returns last 5 journals for the user. */
	private BotResponse handleLast5CommandResponse(User user, String locale) {
		if (user.role() == UserRole.ADMIN) {
			userService.setParticipantSelectState(user.id(), "LAST:5", 0);
			return buildParticipantsPageResponse(0, locale, "bot.journals.last5");
		}
		List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 5);
		return BotResponse.text(formatJournalsForDisplay("bot.journals.last5", journals, locale));
	}

	/** Handles the `/last50` command. Returns last 50 journals for the user. */
	private BotResponse handleLast50CommandResponse(User user, String locale) {
		if (user.role() == UserRole.ADMIN) {
			userService.setParticipantSelectState(user.id(), "LAST:50", 0);
			return buildParticipantsPageResponse(0, locale, "bot.journals.last50");
		}
		List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 50);
		return BotResponse.text(formatJournalsForDisplay("bot.journals.last50", journals, locale));
	}

	private String handleAdminsCommand(@SuppressWarnings("unused") User unused, String locale) {
		List<User> admins = userService.getAdmins();
		if (admins.isEmpty()) {
			return translationService.t("bot.admins.not_found", locale);
		}
		StringBuilder response = new StringBuilder(translationService.t("bot.admins.title", locale)).append("\n");
		for (User admin : admins) {
			response.append(translationService.t("bot.admins.entry", locale, admin.getDisplayName())).append("\n");
		}
		return response.toString();
	}

	/**
	 * Handles the `/participants` command. Only for admins. Returns list of players
	 * ordered by last journal.
	 */
	private String handleParticipantsCommand(User user, String locale) {
		requireAdmin(user, locale);

		List<Participant> participants = userService.getParticipantsOrderedByLastJournal();
		StringBuilder response = new StringBuilder(translationService.t("bot.participants.title", locale)).append("\n");
		boolean hasAny = false;
		for (Participant participant : participants) {
			if (participant.sessionCount() == 0) {
				continue;
			}
			hasAny = true;
			response.append(translationService.t("bot.participants.entry", locale, participant.user().getDisplayName(),
					participant.sessionCount())).append("\n");
		}
		if (!hasAny) {
			return translationService.t("bot.participants.not_found", locale);
		}
		return response.toString();
	}

	/**
	 * Handles the `/promote` command. Only for admins. Promotes a user to admin
	 * role.
	 */
	private BotResponse handlePromoteCommandResponse(User user, String messageText, String locale) {
		requireAdmin(user, locale);
		String[] parts = messageText.split(" ", 2);
		if (parts.length != 2 || parts[1].trim().isEmpty()) {
			userService.setParticipantSelectState(user.id(), "PROMOTE", 0);
			return buildParticipantsPageResponse(0, locale, "bot.participants.title");
		}
		String username = parts[1].trim();
		if (username.startsWith("@")) {
			username = username.substring(1);
		}
		User targetUser = userService.findUserByUsername(username);
		if (targetUser == null) {
			return BotResponse.text(translationService.t("bot.promote.not_found", locale, username));
		}
		userService.changeRole(targetUser, UserRole.ADMIN);
		return BotResponse.text(translationService.t("bot.promote.success", locale, targetUser.getDisplayName()));
	}

	/** Handles the `/ban` command. Only for admins. Bans a user from the bot. */
	private BotResponse handleBanCommandResponse(User user, String messageText, String locale) {
		requireAdmin(user, locale);
		String[] parts = messageText.split(" ", 2);
		if (parts.length != 2 || parts[1].trim().isEmpty()) {
			userService.setParticipantSelectState(user.id(), "BAN", 0);
			return buildParticipantsPageResponse(0, locale, "bot.participants.title");
		}
		String username = parts[1].trim();
		if (username.startsWith("@")) {
			username = username.substring(1);
		}
		User targetUser = userService.findUserByUsername(username);
		if (targetUser == null) {
			return BotResponse.text(translationService.t("bot.ban.not_found", locale, username));
		}
		userService.changeRole(targetUser, UserRole.BANNED);
		return BotResponse.text(translationService.t("bot.ban.success", locale, targetUser.getDisplayName()));
	}

	/**
	 * Handles the `/unban` command. Only for admins. Unbans a user from the bot.
	 */
	private BotResponse handleUnbanCommandResponse(User user, String messageText, String locale) {
		requireAdmin(user, locale);
		String[] parts = messageText.split(" ", 2);
		if (parts.length != 2 || parts[1].trim().isEmpty()) {
			userService.setParticipantSelectState(user.id(), "UNBAN", 0);
			return buildParticipantsPageResponse(0, locale, "bot.participants.title");
		}
		String username = parts[1].trim();
		if (username.startsWith("@")) {
			username = username.substring(1);
		}
		User targetUser = userService.findUserByUsername(username);
		if (targetUser == null) {
			return BotResponse.text(translationService.t("bot.unban.not_found", locale, username));
		}
		userService.changeRole(targetUser, UserRole.PLAYER);
		return BotResponse.text(translationService.t("bot.unban.success", locale, targetUser.getDisplayName()));
	}

	/**
	 * Handles the text input from the user when it doesn't have command prefix.
	 * Checks user state and handles it.
	 */
	private BotResponse handleTextInput(User user, String messageText, String locale) {
		if (user.stateType() == null) {
			return BotResponse.text(translationService.t("bot.error.not_in_state", locale));
		}
		return switch (user.stateType()) {
			case QUESTIONS_UPDATE -> BotResponse.text(handleQuestionsUpdateFlow(user, messageText, locale));
			case QA_FLOW -> BotResponse.text(handleQAFlow(user, messageText, locale));
			case PARTICIPANT_SELECT -> {
				// If user sends a command while in participant select, clear state
				// Otherwise, show the participant selection page again
				int pageIndex = Math.max(0, user.stateQuestionIndex());
				String payload = user.statePayload();
				String titleKey = payload != null && payload.startsWith("LAST:")
						? "bot.journals.last"
						: (payload != null && payload.startsWith("LAST5:")
								? "bot.journals.last5"
								: (payload != null && payload.startsWith("LAST50:")
										? "bot.journals.last50"
										: "bot.participants.title"));
				yield buildParticipantsPageResponse(pageIndex, locale, titleKey);
			}
			default -> BotResponse.text(translationService.t("bot.error.unsupported_state", locale));
		};
	}

	private static final int PARTICIPANTS_PAGE_SIZE = 10;

	private BotResponse buildParticipantsPageResponse(int pageIndex, String locale, String titleKey) {
		List<Participant> participants = userService.getParticipantsOrderedByLastJournal();
		int total = participants.size();
		int from = Math.max(0, pageIndex * PARTICIPANTS_PAGE_SIZE);
		int to = Math.min(total, from + PARTICIPANTS_PAGE_SIZE);
		if (total == 0) {
			return BotResponse.text(translationService.t("bot.participants.not_found", locale));
		}
		StringBuilder sb = new StringBuilder(translationService.t(titleKey, locale)).append("\n");
		sb.append("[").append(from + 1).append("-").append(to).append("/").append(total).append("]");

		List<InlineKeyboardRow> rows = new ArrayList<>();

		// Add participant buttons - one button per participant on current page
		for (int i = from; i < to; i++) {
			Participant p = participants.get(i);
			InlineKeyboardButton button = InlineKeyboardButton.builder()
					.text(p.user().getDisplayName() + " - " + p.sessionCount())
					.callbackData("ps:select:" + p.user().id()).build();
			rows.add(new InlineKeyboardRow(List.of(button)));
		}

		// Add navigation buttons
		List<InlineKeyboardButton> navButtons = new ArrayList<>();
		boolean hasPrev = pageIndex > 0;
		boolean hasNext = to < total;

		if (hasPrev) {
			InlineKeyboardButton prevButton = InlineKeyboardButton.builder().text("◀ Prev")
					.callbackData("ps:page:" + (pageIndex - 1)).build();
			navButtons.add(prevButton);
		}

		InlineKeyboardButton cancelButton = InlineKeyboardButton.builder().text("Cancel").callbackData("ps:cancel")
				.build();
		navButtons.add(cancelButton);

		if (hasNext) {
			InlineKeyboardButton nextButton = InlineKeyboardButton.builder().text("Next ▶")
					.callbackData("ps:page:" + (pageIndex + 1)).build();
			navButtons.add(nextButton);
		}

		if (!navButtons.isEmpty()) {
			rows.add(new InlineKeyboardRow(navButtons));
		}

		InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();

		return BotResponse.withInlineKeyboard(sb.toString(), inlineKeyboard);
	}

	/**
	 * Handles the text input from the user when it is in "question update" state.
	 * Parses incoming text into list of Question entities using prefixes and
	 * updates session questions.
	 */
	private String handleQuestionsUpdateFlow(User user, String messageText, String locale) {
		Session activeSession = sessionService.getActiveSession();
		if (activeSession == null) {
			userService.clearUserState(user.id(), false);
			return translationService.t("bot.questions.update.no_session", locale);
		}

		// Parse incoming text into list of Question entities using prefixes.
		String beforePrefix = QuestionType.BEFORE.name() + ": ";
		String afterPrefix = QuestionType.AFTER.name() + ": ";
		String[] lines = messageText.split("\n");
		int questionOrder = 1; // Starts with 1.
		List<Question> parsedQuestions = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}
			// Parse questions basing on prefixes.
			if (line.toUpperCase().startsWith(beforePrefix)) {
				String text = line.substring(beforePrefix.length());
				parsedQuestions.add(new Question(null, text, QuestionType.BEFORE, questionOrder++, activeSession.id()));
			} else if (line.toUpperCase().startsWith(afterPrefix)) {
				String text = line.substring(afterPrefix.length());
				parsedQuestions.add(new Question(null, text, QuestionType.AFTER, questionOrder++, activeSession.id()));
			}
		}

		// Update questions and exit update mode.
		sessionService.updateSessionQuestions(activeSession, parsedQuestions);
		logger.info("Updated {} session questions to: {}", activeSession.id(),
				parsedQuestions.stream().map(q -> q.type().name() + ": " + q.text()).collect(Collectors.joining(", ")));
		userService.clearUserState(user.id(), false);

		// Build display result.
		String displayResult = buildCurrentQuestionsDisplay(activeSession, locale);
		return translationService.t("bot.questions.update.success", locale, displayResult) + "\n";
	}

	/**
	 * Handles the text input from the user when it is in "question flow" state.
	 * Saves answer and responses with next question or completion message.
	 */
	private String handleQAFlow(User user, String messageText, String locale) {
		var session = sessionService.getActiveSession();
		if (session == null) {
			userService.clearUserState(user.id(), true);
			return translationService.t("bot.qa.flow.no_session", locale);
		}

		if (user.stateSessionId() == null || !session.id().equals(user.stateSessionId())) {
			userService.clearUserState(user.id(), true);
			return translationService.t("bot.qa.flow.session_changed", locale, session.name());
		}
		int previousIndex = user.stateQuestionIndex();
		if (previousIndex < 0) {
			userService.clearUserState(user.id(), true);
			return translationService.t("bot.qa.flow.index_error", locale);
		}

		// Get questions and current question index.
		List<Question> questions = sessionService.getQuestions(session.id());
		int questionsCount = questions.size();
		if (previousIndex >= questionsCount) {
			userService.clearUserState(user.id(), true);
			return translationService.t("bot.qa.flow.question_error", locale);
		}

		// Save answer.
		Question currentQuestion = questions.get(previousIndex);
		journalService.saveJournal(messageText, user, session, currentQuestion);

		// Get next question.
		int nextIndex = previousIndex + 1;
		if (nextIndex < questionsCount) {
			Question nextQuestion = questions.get(nextIndex);

			// Check if next question is of the same type as the current question.
			if (nextQuestion.type() != currentQuestion.type()) {

				// If we got next question of type "after" - stop flow for now.
				if (nextQuestion.type() == QuestionType.AFTER) {
					userService.clearUserState(user.id(), false);
					return translationService.t("bot.qa.flow.done_for_now", locale);
				}

				// If we got "before" question after "after" question - it is a bug.
				userService.clearUserState(user.id(), true);
				return translationService.t("bot.qa.flow.type_error", locale);
			}

			// Ask next question.
			userService.setQuestionFlowState(user.id(), session.id(), nextIndex);
			return translationService.t("bot.qa.flow.answer_saved", locale, nextQuestion.text());
		} else {

			// Last question - exit flow.
			userService.clearUserState(user.id(), true);
			return translationService.t("bot.qa.flow.done", locale);
		}
	}

	/**
	 * Builds a formatted display string for the current session and its questions.
	 *
	 * @param activeSession
	 *            The active session to display.
	 * @param locale
	 *            The locale for translations.
	 * @return SessionDisplayResult containing display text, session, and
	 *         hasQuestions flag
	 */
	private SessionDisplayResult buildSessionAndQuestionsDisplay(Session activeSession, String locale) {
		StringBuilder response = new StringBuilder(translationService.t("bot.session.current.title", locale))
				.append("\n");
		response.append(translationService.t("bot.session.current.name", locale, activeSession.name())).append("\n");
		response.append(translationService.t("bot.session.current.created", locale,
				activeSession.createdAt().format(DATETIME_FORMATTER))).append("\n\n");
		String currentQuestionsDisplay = buildCurrentQuestionsDisplay(activeSession, locale);
		if (!currentQuestionsDisplay.isEmpty()) {
			response.append(translationService.t("bot.session.questions.title", locale)).append("\n");
			response.append(currentQuestionsDisplay);
		}
		return new SessionDisplayResult(response.toString(), activeSession, !currentQuestionsDisplay.isEmpty());
	}

	private String buildCurrentQuestionsDisplay(Session activeSession, String locale) {
		StringBuilder response = new StringBuilder();
		List<Question> questions = sessionService.getQuestions(activeSession.id());
		questions.stream().map(
				q -> translationService.t("bot.questions.display.type", locale, q.type().toString(), q.text()) + "\n")
				.forEach(response::append);
		return response.toString();
	}
}
