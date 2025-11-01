package com.aleksandrmakarov.journals.bot;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.aleksandrmakarov.journals.model.JournalWithQuestion;
import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.SessionJournals;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.JournalService;
import com.aleksandrmakarov.journals.service.SessionService;
import com.aleksandrmakarov.journals.service.UserService;

/**
 * Handler for bot commands. Used HTML markup for formatting messages because "MarkdownV2"
 * https://core.telegram.org/bots/api#markdownv2-style is too restrictive - need escape all [1..126]
 * characters.
 */
@Service
public class BotCommandHandler {

  /** Record containing session display information and metadata. */
  public record SessionDisplayResult(String displayText, Session session, boolean hasQuestions) {}

  @Autowired private UserService userService;

  @Autowired private SessionService sessionService;

  @Autowired private JournalService journalService;

  public static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // State operations are handled by userService now
  public static final String BEFORE_PREFIX = "Before: ";
  public static final String AFTER_PREFIX = "After: ";

  /** Explanation for questions updates. */
  public static final String QUESTIONS_UPDATE_EXPLANATION =
      "Please provide questions in the following format:\n"
          + "```\n"
          + BEFORE_PREFIX
          + "Question to answer before the session?\n"
          + AFTER_PREFIX
          + "Question 1 to answer after the session?\n"
          + AFTER_PREFIX
          + "Question 2 to answer after the session?\n```"
          + "Send empty string to cancel.";

  private static void requireAdmin(User user) {
    if (user == null || user.role() != UserRole.ADMIN) {
      throw new ForbiddenException(
          "Only admins are allowed to perform this action. Use /help for details.");
    }
  }

  /**
   * Handles the command received from the user.
   *
   * @param messageText The text of the message received from the user.
   * @param user The user who sent the message.
   * @param update The update received from the user.
   * @return The response to the user.
   */
  public String handleCommand(String messageText, User user, Update update) {
    String command = messageText.split(" ")[0].toLowerCase();

    switch (command) {
      case "/start":
        return "Welcome to AM Journals Bot. Use /before and /after to answer questions before and after the session. Use /admins to see list of admins.";

      case "/help":
        return getHelpMessage(user.role());

      case "/admins":
        return handleAdminsCommand(user);

      case "/set_questions":
        return handleSetQuestionsCommand(user, messageText);

      case "/before":
        return handleBeforeCommand(user);

      case "/after":
        return handleAfterCommand(user);

      case "/last5":
        return handleLast5Command(user);

      case "/last":
        return handleLastCommand(user);

      case "/last50":
        return handleLast50Command(user);

      case "/participants":
        return handleParticipantsCommand(user);

      case "/promote":
        return handlePromoteCommand(user, messageText);

      case "/ban":
          return handleBanCommand(user, messageText);

      case "/unban":
          return handleUnbanCommand(user, messageText);

      case "/session":
        return handleSessionCommand(user, messageText);

      default:
        if (messageText != null && messageText.startsWith("/")) {
          return "Unknown command. Use /help to see available commands.";
        }
        return handleTextInput(user, messageText);
    }
  }

  private String getHelpMessage(UserRole role) {
    StringBuilder help =
        new StringBuilder(
            "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n");

    if (role == UserRole.ADMIN) {
      help.append("👨‍🏫 <b>Admin Commands:</b>\n");
      help.append("/session - View/replace current session\n");
      help.append("/set_questions - Set current session questions\n");
      help.append("/participants - View all participants\n");
      help.append("/promote - Promote a user to admin role\n");
      help.append("/ban - Ban a user, journals will stay\n");
      help.append("/unban - Unban a user\n\n");
    }

    help.append("👤 <b>Player Commands:</b>\n");
    help.append("/before - Answer pre-session questions\n");
    help.append("/after - Answer post-session questions\n");
    help.append("/last - View last journal\n");
    help.append("/last5 - View last 5 journals\n");
    help.append("/last50 - View last 50 journals\n");
    help.append("/admins - View list of admins");

    return help.toString();
  }

  /**
   * Handles the `/session` command. Only for admins. If no arguments - prints current session and
   * questions. If name is provided - finishes current session and creates new one.
   */
  private String handleSessionCommand(User user, String messageText) {
    requireAdmin(user);
    String[] parts = messageText.split(" ", 2);

    // If no session name - just print current session (if exists).
    if (parts.length == 1) {
      var activeSession = sessionService.getActiveSession();
      if (activeSession == null) {
        return "No active session found. Use `/session {name}` to create a new session.";
      }
      SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession);
      return displayResult.displayText();
    }

    // Otherwise finish current session and create new one.
    String newSessionName = parts[1].trim();
    StringBuilder response = new StringBuilder();
    var finishedSession = sessionService.finishActiveSession();
    if (finishedSession != null) {
      response.append("✅ Session '").append(finishedSession.name()).append("' was finished.\n\n");
    }
    var session = sessionService.createNewSession(newSessionName);
    response.append("✅ Session '").append(newSessionName).append("' created successfully!\n\n");
    SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(session);
    // Either print existing questions or switch to question update (aka "set") mode.
    if (displayResult.hasQuestions()) {
      response
          .append(displayResult.displayText())
          .append("Use /set_questions command if need to update questions.");
    } else {
      response
          .append("No questions found for active session.\n")
          .append(QUESTIONS_UPDATE_EXPLANATION);
      userService.setQuestionsUpdateMode(user.id(), session.id());
    }
    return response.toString();
  }

  /**
   * Handles the `/set_questions` command. Only for admins. Prints current session and questions,
   * explanation, and switches user to "question update" mode.
   */
  private String handleSetQuestionsCommand(User user, String messageText) {
    requireAdmin(user);

    // Get active session.
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Use `/session <name>` to create a new session.";
    }

    // Add to response current session with questions.
    SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession);
    StringBuilder response = new StringBuilder(displayResult.displayText());

    // Add explanation for questions update and return response.
    response.append(QUESTIONS_UPDATE_EXPLANATION);
    userService.setQuestionsUpdateMode(user.id(), activeSession.id());
    return response.toString();
  }

  /**
   * Handles the `/before` command. Checks what questions are available and switches user to
   * "question flow" mode.
   */
  private String handleBeforeCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your admin to create one first.";
    }

    // Get questions and check we have at least one 'before' question.
    List<Question> questions = sessionService.getQuestions(activeSession.id());
    if (questions.isEmpty()) {
      return "No questions found for active session.\nPlease ask your admin to set questions first.";
    } else if (questions.get(0).type() == QuestionType.AFTER) {
      return "No 'before' questions found for active session.\nGood luck with the session, run /after command once you finish it.";
    }

    // Update user state and start flow of answering questions.
    userService.setQuestionFlowState(user.id(), activeSession.id(), 0);
    return "📝 <b>Session:</b> "
        + activeSession.name()
        + " (created: "
        + activeSession.createdAt().format(DATETIME_FORMATTER)
        + ")\nPlease answer the following pre-session questions, send empty string to cancel the flow:\n❓ "
        + questions.get(0).text();
  }

  /**
   * Handles the `/after` command. Checks what questions are available and switches user to
   * "question flow" mode.
   */
  private String handleAfterCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your admin to create one first.";
    }

    // Get questions and check we have at least one 'after' question.
    List<Question> questions = sessionService.getQuestions(activeSession.id());
    if (questions.isEmpty()) {
      return "No questions found for active session.\nPlease ask your admin to set questions first.";
    }

    // Switch to next quesion (stored only "asked" one).
    int currentIndex = user.stateQuestionIndex() + 1;
    if (currentIndex < 0) {
      // Handle case when 'after' is the first question.
      currentIndex = 0;
    }

    // Check 'after' question(s) exists and it is of right type.
    if (questions.size() < currentIndex) {
      return "No 'after' questions found for active session.\n✅ Done, thanks for your answers!";
    }
    Question currentQuestion = questions.get(currentIndex);
    if (currentQuestion.type() != QuestionType.AFTER) {
      return "Wrong type of questions found for active session. Ask your admin to don't edit questions after they started to be answered.";
    }

    // Update user state and start flow of answering questions.
    userService.setQuestionFlowState(user.id(), activeSession.id(), currentIndex);
    return "📝 <b>Session:</b> "
        + activeSession.name()
        + " (created: "
        + activeSession.createdAt().format(DATETIME_FORMATTER)
        + ")\nPlease answer the following post-session questions, send empty string to cancel the flow:\n❓ "
        + currentQuestion.text();
  }

  private String formatJournalsForDisplay(String prefix, List<SessionJournals> sessionJournals) {
    if (sessionJournals.isEmpty()) {
      return prefix + ": No journals found.";
    }

    StringBuilder sb = new StringBuilder(prefix).append(":\n\n");
    for (SessionJournals sessionJournal : sessionJournals) {
      sb.append("📅 ")
          .append(sessionJournal.sessionDate().format(DATETIME_FORMATTER))
          .append(" '")
          .append(sessionJournal.sessionName())
          .append("':\n");
      for (JournalWithQuestion journalWithQuestion : sessionJournal.journals()) {
        sb.append("(")
            .append(journalWithQuestion.questionType())
            .append(") ")
            .append(journalWithQuestion.question())
            .append(" - ")
            .append(journalWithQuestion.journal().answer())
            .append("\n");
      }
    }
    return sb.toString();
  }

  /** Handles the `/last` command. Returns last journal for the user. */
  private String handleLastCommand(User user) {
    List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 1);
    return formatJournalsForDisplay("Last journal", journals);
  }

  /** Handles the `/last5` command. Returns last 5 journals for the user. */
  private String handleLast5Command(User user) {
    List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 5);
    return formatJournalsForDisplay("Last 5 journals", journals);
  }

  /** Handles the `/last50` command. Returns last 50 journals for the user. */
  private String handleLast50Command(User user) {
    List<SessionJournals> journals = sessionService.getJournalsForLastSessions(user.id(), 50);
    return formatJournalsForDisplay("Last 50 journals", journals);
  }

  private String handleAdminsCommand(User user) {
    List<User> admins = userService.getAdmins();
    if (admins.isEmpty()) {
      return "No admins found.";
    }
    StringBuilder response = new StringBuilder("📋 <b>Admins:</b>\n");
    for (User admin : admins) {
      response.append("👤 ").append(admin.getDisplayName()).append("\n");
    }
    return response.toString();
  }

  /**
   * Handles the `/participants` command. Only for admins. Returns list of players ordered by last
   * journal.
   */
  private String handleParticipantsCommand(User user) {
    requireAdmin(user);

    List<Participant> participants = userService.getParticipantsOrderedByLastJournal();
    StringBuilder response = new StringBuilder("📋 <b>Participants:</b>\n");
    boolean hasAny = false;
    for (Participant participant : participants) {
      if (participant.sessionCount() == 0) {
        continue;
      }
      hasAny = true;
      response
          .append("👤 ")
          .append(participant.user().getDisplayName())
          .append(" - ")
          .append(participant.sessionCount())
          .append(" session(s)\n");
    }
    if (!hasAny) {
      return "No participants found.";
    }
    return response.toString();
  }

  /** Handles the `/promote` command. Only for admins. Promotes a user to admin role. */
  private String handlePromoteCommand(User user, String messageText) {
    requireAdmin(user);
    String[] parts = messageText.split(" ", 2);
    if (parts.length != 2) {
      return "Use /promote @username to promote a user to admin role.";
    }
    String username = parts[1].trim();
    if (username.startsWith("@")) {
      username = username.substring(1);
    }
    User targetUser = userService.findUserByUsername(username);
    if (targetUser == null) {
      return "User with username '" + username + "' not found.";
    }
    userService.changeRole(targetUser, UserRole.ADMIN);
    return "User '" + targetUser.getDisplayName() + "' promoted to admin role.";
  }

  /** Handles the `/ban` command. Only for admins. Bans a user from the bot. */
  private String handleBanCommand(User user, String messageText) {
    requireAdmin(user);
    String[] parts = messageText.split(" ", 2);
    if (parts.length != 2) {
      return "Use /ban @username to ban a user from the bot.";
    }
    String username = parts[1].trim();
    if (username.startsWith("@")) {
      username = username.substring(1);
    }
    User targetUser = userService.findUserByUsername(username);
    if (targetUser == null) {
      return "User with username '" + username + "' not found.";
    }
    userService.changeRole(targetUser, UserRole.BANNED);
    return "User '" + targetUser.getDisplayName() + "' is banned.";
  }

  /** Handles the `/unban` command. Only for admins. Unbans a user from the bot. */
  private String handleUnbanCommand(User user, String messageText) {
    requireAdmin(user);
    String[] parts = messageText.split(" ", 2);
    if (parts.length != 2) {
      return "Use /unban @username to unban a user from the bot.";
    }
    String username = parts[1].trim();
    if (username.startsWith("@")) {
      username = username.substring(1);
    }
    User targetUser = userService.findUserByUsername(username);
    if (targetUser == null) {
      return "User with username '" + username + "' not found.";
    }
    userService.changeRole(targetUser, UserRole.PLAYER);
    return "User '" + targetUser.getDisplayName() + "' is unbanned.";
  }

  /**
   * Handles the text input from the user when it doesn't have command prefix. Checks user state and
   * handles it.
   */
  private String handleTextInput(User user, String messageText) {
    if (user.stateType() == null) {
      return "You are not in a state of handling direct input. Run some command first, use /help to see a list.";
    }
    switch (user.stateType()) {
      case QUESTIONS_UPDATE:
        return handleQuestionsUpdateFlow(user, messageText);
      case QA_FLOW:
        return handleQAFlow(user, messageText);
      default:
        return "Unsupported user state. Send empty string and use /help to start new command.";
    }
  }

  /**
   * Handles the text input from the user when it is in "question update" state. Parses incoming
   * text into list of Question entities using prefixes and updates session questions.
   */
  private String handleQuestionsUpdateFlow(User user, String messageText) {
    if (messageText.trim().isEmpty()) {
      userService.clearUserState(user.id(), false);
      return "Questions update cancelled.";
    }

    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      userService.clearUserState(user.id(), false);
      return "No active session found. Use `/session <name>` to create a new session.";
    }

    // Parse incoming text into list of Question entities using prefixes.
    String[] lines = messageText.split("\n");
    int questionOrder = 1; // Starts with 1.
    List<Question> parsedQuestions = new ArrayList<>();
    for (String raw : lines) {
      String line = raw.trim();
      if (line.isEmpty()) {
        continue;
      } else if (line.startsWith(BEFORE_PREFIX)) {
        String text = line.substring(BEFORE_PREFIX.length());
        parsedQuestions.add(
            new Question(null, text, QuestionType.BEFORE, questionOrder++, activeSession.id()));
      } else if (line.startsWith(AFTER_PREFIX)) {
        String text = line.substring(AFTER_PREFIX.length());
        parsedQuestions.add(
            new Question(null, text, QuestionType.AFTER, questionOrder++, activeSession.id()));
      }
    }

    // Update questions and exit update mode.
    sessionService.updateSessionQuestions(activeSession, parsedQuestions);
    userService.clearUserState(user.id(), false);

    // Build display result.
    var displayResult = buildCurrentQuestionsDisplay(activeSession);
    return "Questions updated successfully to:\n" + displayResult;
  }

  /**
   * Handles the text input from the user when it is in "question flow" state. Saves answer and
   * responses with next question or completion message.
   */
  private String handleQAFlow(User user, String messageText) {
    var session = sessionService.getActiveSession();
    if (session == null) {
      userService.clearUserState(user.id(), true);
      return "No active session found. Ask your admin to create a new session.";
    }

    if (user.stateSessionId() == null || !session.id().equals(user.stateSessionId())) {
      userService.clearUserState(user.id(), true);
      return "Previous session was changed or finished and questions are not relevant anymore. Participate in new session '"
          + session.name()
          + "' with `/before` command.";
    }
    int previousIndex = user.stateQuestionIndex();
    if (previousIndex < 0) {
      userService.clearUserState(user.id(), true);
      return "Error with questions index. Ask your admin for help.";
    }

    // Get questions and current question index.
    List<Question> questions = sessionService.getQuestions(session.id());
    int questionsCount = questions.size();
    if (previousIndex >= questionsCount) {
      userService.clearUserState(user.id(), true);
      return "Error with finding right question. Ask your admin to don't edit questions after they started to be answered.";
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
          return "✅ Done for now, good luck with the session, run /after command once you finish it.";
        }

        // If we got "before" question after "after" question - it is a bug.
        userService.clearUserState(user.id(), true);
        return "Error with finding right type of question. Ask your admin to don't edit questions after they started to be answered.";
      }

      // Ask next question.
      userService.setQuestionFlowState(user.id(), session.id(), nextIndex);
      return "☑️ Answer saved!\n❓ " + nextQuestion.text();
    } else {

      // Last question - exit flow.
      userService.clearUserState(user.id(), true);
      return "✅ Done, thank you for your answers!";
    }
  }

  /**
   * Builds a formatted display string for the current session and its questions.
   *
   * @param activeSession The active session to display
   * @return SessionDisplayResult containing display text, session, and hasQuestions flag
   */
  private SessionDisplayResult buildSessionAndQuestionsDisplay(Session activeSession) {
    StringBuilder response = new StringBuilder("📝 <b>Current Session:</b>\n");
    response.append("Name: ").append(activeSession.name()).append("\n");
    response
        .append("Created: ")
        .append(activeSession.createdAt().format(DATETIME_FORMATTER))
        .append("\n\n");
    String currentQuestionsDisplay = buildCurrentQuestionsDisplay(activeSession);
    if (!currentQuestionsDisplay.isEmpty()) {
      response.append("📋 <b>Questions:</b>\n");
      response.append(currentQuestionsDisplay);
    } else {
      response.append(
          "No questions found for active session.\nUse /set_questions command to set questions.");
    }
    return new SessionDisplayResult(
        response.toString(), activeSession, !currentQuestionsDisplay.isEmpty());
  }

  private String buildCurrentQuestionsDisplay(Session activeSession) {
    StringBuilder response = new StringBuilder();
    sessionService.getQuestions(activeSession.id()).stream()
        .map(q -> q.type() + ": " + q.text() + "\n")
        .forEach(response::append);
    if (response.length() > 0) {
      response.append("\n");
    }
    return response.toString();
  }
}
