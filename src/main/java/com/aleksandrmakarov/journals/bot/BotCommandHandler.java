package com.aleksandrmakarov.journals.bot;

import com.aleksandrmakarov.journals.model.*;
import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.security.ForbiddenException;
import com.aleksandrmakarov.journals.service.JournalService;
import com.aleksandrmakarov.journals.service.SessionService;
import com.aleksandrmakarov.journals.service.UserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public class BotCommandHandler {

  /** Record containing session display information and metadata. */
  public record SessionDisplayResult(String displayText, Session session, boolean hasQuestions) {}

  @Autowired private UserService userService;

  @Autowired private SessionService sessionService;

  @Autowired private JournalService journalService;

  // State operations are handled by userService now

  private static final String QUESTIONS_UPDATE_EXPLANATION =
      "Please provide questions in the following format:\n\n"
          + "```Before: What is your personal goal on this session?\n"
          + "After: Have goal been archived?\n"
          + "After: If yes then how?\n```\n\n"
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
        return "Welcome to AM Journals Bot. If you are a player, use `/before` and `/after` to answer questions before and after the session. If you are an admin, use `/sessions` to create sessions. Use `/help` to see all available commands.";

      case "/help":
        return getHelpMessage(user.role());

      case "/questions":
        return handleQuestionsCommand(user, messageText);

      case "/before":
        return handleBeforeCommand(user);

      case "/after":
        return handleAfterCommand(user);

      case "/last5":
        return handleLast5Command(user);

      case "/last":
        return handleLastCommand(user);

      case "/history":
        return handleHistoryCommand(user);

      case "/participants":
        return handleParticipantsCommand(user);

      case "/promote":
        return handlePromoteCommand(user, messageText);

      case "/session":
        return handleSessionCommand(user, messageText);

      default:
        return handleTextInput(user, messageText);
    }
  }

  private String getHelpMessage(UserRole role) {
    StringBuilder help =
        new StringBuilder(
            "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n");

    if (role == UserRole.ADMIN) {
      help.append("👨‍🏫 *Admin Commands:*\n");
      help.append("/questions - Set session questions\n");
      help.append("/session - View current session or create new one\n");
      help.append("/participants - View all players\n\n");
    }

    help.append("👤 *Player Commands:*\n");
    help.append("/before - Answer pre-session questions\n");
    help.append("/after - Answer post-session questions\n");
    help.append("/last - View last journal\n");
    help.append("/last5 - View last 5 journals\n");
    help.append("/history - View all journals\n");

    return help.toString();
  }

  private String handleSessionCommand(User user, String messageText) {
    requireAdmin(user);
    String[] parts = messageText.split(" ", 2);

    // If no session name - just print current session (if exists).
    if (parts.length == 1) {
      var activeSession = sessionService.getActiveSession();
      if (activeSession == null) {
        return "No active session found. Use `/session <name>` to create a new session.";
      }
      SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession);
      return displayResult.displayText();
    }

    // Otherwise close current session and create new one.
    String newSessionName = parts[1].trim();
    StringBuilder response = new StringBuilder();
    var finishedSession = sessionService.finishActiveSession();
    if (finishedSession != null) {
      response.append("✅ Session '").append(finishedSession.name()).append("' was finished.\n\n");
    }
    var session = sessionService.createNewSession(newSessionName);
    SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(session);
    response
        .append("✅ Session '")
        .append(newSessionName)
        .append("' created successfully!\n\n")
        .append(displayResult.displayText());
    return response.toString();
  }

  private String handleQuestionsCommand(User user, String messageText) {
    requireAdmin(user);

    // Get active session.
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Use `/session <name>` to create a new session.";
    }

    // Add to response current session and questions.
    SessionDisplayResult displayResult = buildSessionAndQuestionsDisplay(activeSession);
    StringBuilder response = new StringBuilder(displayResult.displayText());

    // If no questions - switch to "question update" mode.
    if (!displayResult.hasQuestions()) {
      response.append(QUESTIONS_UPDATE_EXPLANATION);
      userService.setQuestionUpdateMode(user.id(), activeSession.id());
    } else {
      response.append("\nUse `/questions` command if need to update questions.");
    }
    return response.toString();
  }

  private String handleBeforeCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your admin to create one first.";
    }

    List<Question> beforeQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.BEFORE);
    if (beforeQuestions.isEmpty()) {
      return "No 'before' questions found for this session.";
    }

    userService.setQuestionFlowState(user.id(), activeSession.id(), 0);

    return "📝 *Session:* "
        + activeSession.name()
        + " (created: "
        + activeSession.createdAt().toString()
        + ")\n\n"
        + "Let's start with the pre-session questions:\n\n"
        + "❓ "
        + beforeQuestions.get(0).text()
        + "\n\n"
        + "Please answer this question:";
  }

  private String handleAfterCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your admin to set questions first.";
    }

    List<Question> beforeQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.BEFORE);
    List<Question> afterQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.AFTER);
    if (afterQuestions.isEmpty()) {
      return "No 'after' questions found for this session.";
    }

    userService.setQuestionFlowState(user.id(), activeSession.id(), beforeQuestions.size());

    return "📝 *Session:* "
        + activeSession.name()
        + " (created: "
        + activeSession.createdAt().toString()
        + ")\n\n"
        + "Let's start with the post-session questions:\n\n"
        + "❓ "
        + afterQuestions.get(0).text()
        + "\n\n"
        + "Please answer this question:";
  }

  private String handleLast5Command(User user) {
    List<Journal> journals = journalService.getUserJournals(user, 5);
    return journalService.formatJournalsForDisplay(journals);
  }

  private String handleLastCommand(User user) {
    List<Journal> journals = journalService.getUserJournals(user, 1);
    return journalService.formatJournalsForDisplay(journals);
  }

  private String handleHistoryCommand(User user) {
    List<Journal> journals = journalService.getUserJournals(user, 50);
    return journalService.formatJournalsForDisplay(journals);
  }

  private String handleParticipantsCommand(User user) {
    requireAdmin(user);

    List<User> players = userService.getPlayersOrderedByLastJournal();
    if (players.isEmpty()) {
      return "No players found.";
    }

    StringBuilder response = new StringBuilder("📋 *Participants:*\n\n");
    for (User player : players) {
      Long journalCount = journalService.getUserJournalCount(player);
      response
          .append("👤 ")
          .append(player.getDisplayName())
          .append(" - ")
          .append(journalCount)
          .append(" journals\n");
    }

    return response.toString();
  }

  private String handlePromoteCommand(User user, String messageText) {
    requireAdmin(user);

    return "Use /promote @username to promote a user to admin role.";
  }

  /**
   * Handles the text input from the user when it doesn't have command prefix.
   *
   * @param user The user who sent the message.
   * @param messageText The text of the message received from the user.
   * @return The response to the user.
   */
  private String handleTextInput(User user, String messageText) {
    // Get all user states in one query to avoid multiple DB calls.
    // Read user state via repository-backed user entity
    User freshUser = userService.findUserByTelegramId(user.telegramId());
    StateType currentState = freshUser != null ? freshUser.stateType() : null;

    // Handle question update flow.
    if (currentState == StateType.QUESTION_UPDATE) {
      if (messageText.trim().isEmpty()) {
        userService.clearQuestionUpdateMode(user.id());
        return "Question update cancelled.";
      }
      // duplicate empty-check removed

      Session activeSession = sessionService.getActiveSession();
      if (activeSession == null) {
        activeSession = sessionService.createNewSession("Default Session");
      }

      sessionService.updateSessionQuestions(activeSession, messageText);
      userService.clearQuestionUpdateMode(user.id());
      return "Questions updated successfully! Players can now use /before and /after commands.";
    }

    // Handle QA flow
    if (currentState == StateType.QA_FLOW) {
      return handleQAFlow(user, messageText);
    }

    // No active state - unknown command
    return "Unknown command. Use /help to see available commands.";
  }

  private String handleQAFlow(User user, String messageText) {
    var session = sessionService.getActiveSession();
    if (session == null) {
      userService.clearQuestionFlowState(user.id());
      return "No active session found. Ask your admin to create a new session.";
    }

    User freshUser = userService.findUserByTelegramId(user.telegramId());
    if (freshUser == null || !session.id().equals(freshUser.stateSessionId())) {
      userService.clearQuestionFlowState(user.id());
      return "Session was changed or finished and questions are not relevant anymore. Participate in new session '"
          + session.name()
          + "' with `/before` command.";
    }

    List<Question> beforeQuestions =
        sessionService.getQuestionsByType(session, QuestionType.BEFORE);
    List<Question> afterQuestions = sessionService.getQuestionsByType(session, QuestionType.AFTER);
    int beforeCount = beforeQuestions.size();
    int totalCount = beforeCount + afterQuestions.size();

    int index = freshUser.stateQuestionIndex() != null ? freshUser.stateQuestionIndex() : 0;
    if (index < 0 || index >= totalCount) {
      userService.clearQuestionFlowState(user.id());
      return "Error with questions index. Ask your admin for help.";
    }

    Question currentQuestion =
        index < beforeCount ? beforeQuestions.get(index) : afterQuestions.get(index - beforeCount);
    journalService.saveJournal(messageText, user, session, currentQuestion);

    int nextIndex = index + 1;
    if (nextIndex < beforeCount) {
      userService.setQuestionFlowState(user.id(), session.id(), nextIndex);
      return "✅ Answer saved!\n\n❓ "
          + beforeQuestions.get(nextIndex).text()
          + "\n\nPlease answer this question:";
    } else if (nextIndex == beforeCount) {
      userService.setQuestionFlowState(user.id(), session.id(), nextIndex);
      return "✅ Done for now, good luck with the session, run `/after` command once you finish it.";
    } else if (nextIndex < totalCount) {
      userService.setQuestionFlowState(user.id(), session.id(), nextIndex);
      int afterIndex = nextIndex - beforeCount;
      return "✅ Answer saved!\n\n❓ "
          + afterQuestions.get(afterIndex).text()
          + "\n\nPlease answer this question:";
    } else {
      userService.clearQuestionFlowState(user.id());
      return "✅ Done, thanks for your answers!";
    }
  }

  /**
   * Builds a formatted display string for the current session and its questions.
   *
   * @param activeSession The active session to display
   * @return SessionDisplayResult containing display text, session, and hasQuestions flag
   */
  private SessionDisplayResult buildSessionAndQuestionsDisplay(Session activeSession) {
    StringBuilder response = new StringBuilder("📝 *Current Session:*\n");
    response.append("Name: ").append(activeSession.name()).append("\n");
    response.append("Created: ").append(activeSession.createdAt().toString()).append("\n\n");
    response.append("📋 *Current Questions:*\n\n");

    // Check current questions.
    String currentQuestionsDisplay = buildCurrentQuestionsDisplay(activeSession);
    response.append(currentQuestionsDisplay);
    return new SessionDisplayResult(
        response.toString(), activeSession, currentQuestionsDisplay.isEmpty());
  }

  private String buildCurrentQuestionsDisplay(Session activeSession) {
    StringBuilder response = new StringBuilder();
    List<Question> beforeQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.BEFORE);
    List<Question> afterQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.AFTER);
    if (!beforeQuestions.isEmpty()) {
      response.append("🔵 *Before Questions:*\n");
      for (Question q : beforeQuestions) {
        response.append("- ").append(q.text()).append("\n");
      }
      response.append("\n");
    }
    if (!afterQuestions.isEmpty()) {
      response.append("🔴 *After Questions:*\n");
      for (Question q : afterQuestions) {
        response.append("- ").append(q.text()).append("\n");
      }
      response.append("\n");
    }
    return response.toString();
  }
}
