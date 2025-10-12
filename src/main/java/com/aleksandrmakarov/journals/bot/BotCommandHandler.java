package com.aleksandrmakarov.journals.bot;

import com.aleksandrmakarov.journals.model.*;
import com.aleksandrmakarov.journals.service.JournalService;
import com.aleksandrmakarov.journals.service.SessionService;
import com.aleksandrmakarov.journals.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public class BotCommandHandler {

  @Autowired private UserService userService;

  @Autowired private SessionService sessionService;

  @Autowired private JournalService journalService;

  // Store user states for question answering flow
  private final Map<Long, QuestionAnswerState> userStates = new ConcurrentHashMap<>();

  public String handleCommand(String messageText, User user, Update update) {
    String command = messageText.split(" ")[0].toLowerCase();

    switch (command) {
      case "/start":
        return "Welcome to Journals Bot! Use /help to see available commands.";

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

      default:
        return handleTextInput(user, messageText);
    }
  }

  private String getHelpMessage(UserRole role) {
    StringBuilder help = new StringBuilder("Available commands:\n\n");

    if (role == UserRole.COACH) {
      help.append("👨‍🏫 <b>Coach Commands:</b>\n");
      help.append("/questions - Set session questions\n");
      help.append("/participants - View all players\n\n");
    }

    help.append("👤 <b>Player Commands:</b>\n");
    help.append("/before - Answer pre-session questions\n");
    help.append("/after - Answer post-session questions\n");
    help.append("/last5 - View last 5 journals\n");
    help.append("/last - View last journal\n");
    help.append("/history - View all journals\n");

    return help.toString();
  }

  private String handleQuestionsCommand(User user, String messageText) {
    if (user.role() != UserRole.COACH) {
      return "Only coaches can set questions.";
    }

    if (messageText.equals("/questions")) {
      Session activeSession = sessionService.getActiveSession();
      if (activeSession == null) {
        return "Please provide questions in the following format:\n\n"
            + "Before: What is your personal goal on this session?\n"
            + "After: Have goal been archived?\n"
            + "After: If yes then how?\n\n"
            + "Send your questions now:";
      }

      // Show existing questions as template
      List<Question> beforeQuestions =
          sessionService.getQuestionsByType(activeSession, QuestionType.BEFORE);
      List<Question> afterQuestions =
          sessionService.getQuestionsByType(activeSession, QuestionType.AFTER);

      StringBuilder response = new StringBuilder("📝 <b>Current Questions:</b>\n\n");

      if (!beforeQuestions.isEmpty()) {
        response.append("🔵 <b>Before Questions:</b>\n");
        for (Question q : beforeQuestions) {
          response.append("Before: ").append(q.text()).append("\n");
        }
        response.append("\n");
      }

      if (!afterQuestions.isEmpty()) {
        response.append("🔴 <b>After Questions:</b>\n");
        for (Question q : afterQuestions) {
          response.append("After: ").append(q.text()).append("\n");
        }
        response.append("\n");
      }

      response.append("Send new questions to update, or send empty message to cancel:");
      return response.toString();
    }

    // Handle empty string (cancel)
    if (messageText.trim().isEmpty()) {
      return "Question update cancelled.";
    }

    // Process questions
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      activeSession = sessionService.createNewSession();
    }

    sessionService.updateSessionQuestions(activeSession, messageText);
    return "Questions updated successfully! Players can now use /before and /after commands.";
  }

  private String handleBeforeCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your coach to set questions first.";
    }

    List<Question> beforeQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.BEFORE);
    if (beforeQuestions.isEmpty()) {
      return "No 'before' questions found for this session.";
    }

    // Start question answering flow
    QuestionAnswerState state =
        new QuestionAnswerState(activeSession, beforeQuestions, QuestionType.BEFORE);
    userStates.put(user.id(), state);

    return "Let's start with the pre-session questions:\n\n"
        + "❓ "
        + beforeQuestions.get(0).text()
        + "\n\n"
        + "Please answer this question:";
  }

  private String handleAfterCommand(User user) {
    Session activeSession = sessionService.getActiveSession();
    if (activeSession == null) {
      return "No active session found. Please ask your coach to set questions first.";
    }

    List<Question> afterQuestions =
        sessionService.getQuestionsByType(activeSession, QuestionType.AFTER);
    if (afterQuestions.isEmpty()) {
      return "No 'after' questions found for this session.";
    }

    // Start question answering flow
    QuestionAnswerState state =
        new QuestionAnswerState(activeSession, afterQuestions, QuestionType.AFTER);
    userStates.put(user.id(), state);

    return "Let's start with the post-session questions:\n\n"
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
    List<Journal> journals =
        journalService.getUserJournals(user, 50); // Limit to 50 for performance
    return journalService.formatJournalsForDisplay(journals);
  }

  private String handleParticipantsCommand(User user) {
    if (user.role() != UserRole.COACH) {
      return "Only coaches can view participants.";
    }

    List<User> players = userService.getPlayersOrderedByLastJournal();
    if (players.isEmpty()) {
      return "No players found.";
    }

    StringBuilder response = new StringBuilder("📋 <b>Participants:</b>\n\n");
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
    if (user.role() != UserRole.COACH) {
      return "Only coaches can promote users.";
    }

    // This is a simple implementation - in production you'd want more security
    return "Use /promote @username to promote a user to coach role.";
  }

  private String handleTextInput(User user, String messageText) {
    // Check if user is a coach and might be updating questions
    if (user.role() == UserRole.COACH) {
      // Check if this looks like question format
      if (messageText.contains("Before:") || messageText.contains("After:")) {
        return handleQuestionsCommand(user, messageText);
      }
    }

    QuestionAnswerState state = userStates.get(user.id());
    if (state == null) {
      return "Unknown command. Use /help to see available commands.";
    }

    // Save answer
    Question currentQuestion = state.getCurrentQuestion();
    journalService.saveJournal(messageText, user, state.getSession(), currentQuestion);

    // Move to next question
    state.nextQuestion();

    if (state.hasMoreQuestions()) {
      return "✅ Answer saved!\n\n"
          + "❓ "
          + state.getCurrentQuestion().text()
          + "\n\n"
          + "Please answer this question:";
    } else {
      // Finished all questions
      userStates.remove(user.id());
      return "✅ Done for now, good luck with the session, run <code>/after</code> command once you finish it.";
    }
  }

  // Inner class for managing question answering state
  private static class QuestionAnswerState {
    private final Session session;
    private final List<Question> questions;
    private int currentIndex;

    public QuestionAnswerState(Session session, List<Question> questions, QuestionType type) {
      this.session = session;
      this.questions = questions;
      this.currentIndex = 0;
    }

    public Question getCurrentQuestion() {
      return questions.get(currentIndex);
    }

    public void nextQuestion() {
      currentIndex++;
    }

    public boolean hasMoreQuestions() {
      return currentIndex < questions.size();
    }

    public Session getSession() {
      return session;
    }
  }
}
