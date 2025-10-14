package com.aleksandrmakarov.journals.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import com.aleksandrmakarov.journals.repository.UserRepository;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Simple integration tests for the WebhookController. Tests basic functionality without complex
 * database operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SimpleWebhookIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private JournalRepository journalRepository;

  static {
    try {
      Files.deleteIfExists(Path.of("test-journals.db"));
      Files.deleteIfExists(Path.of("test-journals.db-wal"));
      Files.deleteIfExists(Path.of("test-journals.db-shm"));
    } catch (IOException ignored) {}
  }

  @BeforeEach
  void setUp() {
    // Clean up all data
    journalRepository.deleteAll();
    questionRepository.deleteAll();
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    // Create coach user
    User coach =
        new User(
            null,
            1001L,
            "admin_user",
            "Admin",
            "Adminin",
            UserRole.ADMIN,
            LocalDateTime.now(),
            null,
            null,
            null,
            null);
    userRepository.save(coach);

    // Create player user
    User player =
        new User(
            null,
            2001L,
            "player_user",
            "Player",
            "Johnson",
            UserRole.PLAYER,
            LocalDateTime.now(),
            null,
            null,
            null,
            null);
    userRepository.save(player);
  }

  private Update createUpdate(
      Long userId, String username, String firstName, String lastName, String messageText) {
    Update update = new Update();
    update.setUpdateId(1);

    Message message = new Message();
    message.setMessageId(1);
    message.setText(messageText);
    message.setDate((int) (System.currentTimeMillis() / 1000));

    Chat chat = new Chat();
    chat.setId(userId);
    chat.setType("private");
    message.setChat(chat);

    org.telegram.telegrambots.meta.api.objects.User from =
        new org.telegram.telegrambots.meta.api.objects.User();
    from.setId(userId);
    from.setUserName(username);
    from.setFirstName(firstName);
    from.setLastName(lastName);
    message.setFrom(from);

    update.setMessage(message);
    return update;
  }

  private ResponseEntity<String> sendWebhookRequest(Update update) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Update> request = new HttpEntity<>(update, headers);
    return restTemplate.exchange("/webhook", HttpMethod.POST, request, String.class);
  }

  /**
   * Helper method to assert that the response body contains the expected text. Encodes the expected
   * text to match the JSON format with Unicode escape sequences.
   */
  private void assertResponseContains(String responseBody, String expectedText) {
    // Encode the expected text to match JSON format
    String encodedExpectedText =
        expectedText
            .replace("👤", "\\uD83D\\uDC64")
            .replace("📝", "\\uD83D\\uDCDD")
            .replace("📋", "\\uD83D\\uDCCB")
            .replace("🔵", "\\uD83D\\uDD35")
            .replace("🔴", "\\uD83D\\uDD34")
            .replace("❓", "\\u2753")
            .replace("✅", "\\u2705")
            .replace("📅", "\\uD83D\\uDCC5")
            .replace("🧭", "\\uD83D\\uDCAD")
            .replace("\n", "\\n");
    if (!responseBody.contains(encodedExpectedText)) {
      throw new AssertionError(
          "Expected response to contain\n- " + encodedExpectedText + "\ngot\n- " + responseBody);
    }
  }

  @Test
  void test_start_welcome() {
    Update startCommand = createUpdate(2001L, "new_user", "New", "User", "/start");
    ResponseEntity<String> response = sendWebhookRequest(startCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "Welcome to AM Journals Bot. If you are a player, use `/before` and `/after` to answer questions before and after the session. If you are an admin, use `/sessions` to create sessions. Use `/help` to see all available commands.");
  }

  @Test
  void test_help_help() {
    Update helpCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/help");
    ResponseEntity<String> response = sendWebhookRequest(helpCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👤 *Player Commands:*\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/history - View all journals\n");
  }

  @Test
  void test_before_noActiveSession() {
    Update beforeCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    ResponseEntity<String> response = sendWebhookRequest(beforeCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_after_noActiveSession() {
    Update afterCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    ResponseEntity<String> response = sendWebhookRequest(afterCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "No active session found. Please ask your admin to set questions first.");
  }

  @Test
  void test_last_noJournals() {
    Update lastCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    ResponseEntity<String> response = sendWebhookRequest(lastCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_last5_noJournals() {
    Update last5Command = createUpdate(2001L, "player_user", "Player", "Johnson", "/last5");
    ResponseEntity<String> response = sendWebhookRequest(last5Command);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_history_noJournals() {
    Update historyCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/history");
    ResponseEntity<String> response = sendWebhookRequest(historyCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_participants_noParticipants() {
    // Test participants command - should show no players since we have no journals
    Update participantsCommand =
        createUpdate(1001L, "admin_user", "Coach", "Smith", "/participants");
    ResponseEntity<String> response = sendWebhookRequest(participantsCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📋 *Participants:*\n\n👤 Player Johnson - 0 journals\n");
  }

  @Test
  void test_unknown_command() {
    Update unknownCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "random text");
    ResponseEntity<String> response = sendWebhookRequest(unknownCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "Unknown command. Use /help to see available commands.");
  }

  @Test
  void test_sessionFlow_onePlayer() {
    // Step 1: Coach creates a session and checks questions
    Update createSession = createUpdate(1001L, "admin_user", "Coach", "Smith", "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(createSession);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "Session 'Default Session' created successfully!");

    Update coachQuestions = createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*");
    assertResponseContains(body, "Please provide questions in the following format:");

    // Step 2: Coach provides questions
    Update coachSetsQuestions =
        createUpdate(
            1001L,
            "admin_user",
            "Coach",
            "Smith",
            "Before: What is your goal for this session?\n"
                + "Before: How do you feel before starting?\n"
                + "After: Did you achieve your goal?\n"
                + "After: How do you feel after the session?");
    response = sendWebhookRequest(coachSetsQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "Questions updated successfully! Players can now use /before and /after commands.");

    // Step 3: Player starts before questions
    Update playerBefore = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    response = sendWebhookRequest(playerBefore);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    // Check complete response format for before command
    assertResponseContains(body, "📝 *Session:* Default Session (created:");
    assertResponseContains(body, "Let's start with the pre-session questions:");
    assertResponseContains(body, "What is your goal for this session?");
    assertResponseContains(body, "Please answer this question:");

    // Step 4: Player answers first 'before' question
    Update playerAnswer1 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "I want to improve my technique");
    response = sendWebhookRequest(playerAnswer1);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "✅ Answer saved!\n\n❓ How do you feel before starting?\n\nPlease answer this question:");

    // Step 5: Player answers second before question - should automatically transition to after
    // questions
    Update playerAnswer2 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "I feel excited and ready");
    response = sendWebhookRequest(playerAnswer2);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "✅ Done for now, good luck with the session, run `/after` command once you finish it.");

    // Step 6: Player starts after questions
    Update playerAfter = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    response = sendWebhookRequest(playerAfter);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    // Check complete response format for after command
    assertResponseContains(body, "📝 *Session:* Default Session (created:");
    assertResponseContains(body, "Let's start with the post-session questions:");
    assertResponseContains(body, "Did you achieve your goal?");
    assertResponseContains(body, "Please answer this question:");

    // Step 7: Player answers first after question
    Update playerAnswer3 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "Yes, I improved significantly");
    response = sendWebhookRequest(playerAnswer3);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "✅ Answer saved!\n\n❓ How do you feel after the session?\n\nPlease answer this question:");

    // Step 8: Player answers second after question
    Update playerAnswer4 =
        createUpdate(
            2001L, "player_user", "Player", "Johnson", "I feel accomplished and motivated");
    response = sendWebhookRequest(playerAnswer4);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "✅ Done, thanks for your answers!");

    // Step 9: Player checks their last journal
    Update playerLast = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    response = sendWebhookRequest(playerLast);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "📝 *Last Journal Entry:*\n\n🔵 *Before Questions:*\n- What is your goal for this session?\n- How do you feel before starting?\n\n🔴 *After Questions:*\n- Did you achieve your goal?\n- How do you feel after the session?\n\n*Answers:*\n- I want to improve my technique\n- I feel excited and ready\n- Yes, I improved significantly\n- I feel accomplished and motivated");
  }

  @Test
  void test_updateQuestions_ok() {
    // Step 1: Coach sets initial questions
    // Ensure session exists
    Update ensureSession = createUpdate(1001L, "admin_user", "Coach", "Smith", "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(ensureSession);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "created successfully!");

    Update coachQuestions1 = createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachQuestions1);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*");

    Update coachSetsInitial =
        createUpdate(
            1001L,
            "admin_user",
            "Coach",
            "Smith",
            "Before: What is your main focus today?\n" + "After: How did the session go?");
    response = sendWebhookRequest(coachSetsInitial);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "Questions updated successfully! Players can now use /before and /after commands.");

    // Step 2: Coach checks current questions
    Update coachCheckQuestions = createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachCheckQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    // Check complete response format
    assertResponseContains(body, "📝 *Current Session:*");
    assertResponseContains(body, "Name: Default Session");
    assertResponseContains(body, "Created:");
    assertResponseContains(body, "📋 *Current Questions:*");
    assertResponseContains(body, "🔵 *Before Questions:*");
    assertResponseContains(body, "- What is your main focus today?");
    assertResponseContains(body, "🔴 *After Questions:*");
    assertResponseContains(body, "- How did the session go?");
    assertResponseContains(body, "Please provide questions in the following format:");
    assertResponseContains(body, "Send empty string to cancel.");

    // Step 3: Coach updates questions
    Update coachUpdatesQuestions =
        createUpdate(
            1001L,
            "admin_user",
            "Coach",
            "Smith",
            "Before: What is your main focus today?\n"
                + "Before: Any concerns before we start?\n"
                + "After: How did the session go?\n"
                + "After: What did you learn?");
    response = sendWebhookRequest(coachUpdatesQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "Questions updated successfully! Players can now use /before and /after commands.");

    // Step 4: Coach verifies updated questions
    Update coachVerifyQuestions = createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachVerifyQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*\nName: Default Session\nCreated:");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n\n🔵 *Before Questions:*\n- What is your main focus today?\n- Any concerns before we start?\n\n🔴 *After Questions:*\n- How did the session go?\n- What did you learn?\n\nSend new questions to update, or send empty message to cancel:");
  }

  @Test
  void test_updateQuestions_cancel() {
    // Step 1: Coach sets initial questions
    // Ensure session exists
    Update ensureSession2 = createUpdate(1001L, "admin_user", "Coach", "Smith", "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(ensureSession2);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "created successfully!");

    Update coachQuestions1 = createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachQuestions1);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*");

    Update coachSetsInitial =
        createUpdate(
            1001L,
            "admin_user",
            "Coach",
            "Smith",
            "Before: What is your main focus today?\n" + "After: How did the session go?");
    response = sendWebhookRequest(coachSetsInitial);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "Questions updated successfully! Players can now use /before and /after commands.");

    // Step 2: Coach re-enters question update mode
    Update coachReenterQuestions =
        createUpdate(1001L, "admin_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachReenterQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*\nName: Default Session\nCreated:");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n\n🔵 *Before Questions:*\n- What is your main focus today?\n\n🔴 *After Questions:*\n- How did the session go?\n");
    assertResponseContains(body, "Please provide questions in the following format:");
    assertResponseContains(body, "Send empty string to cancel.");

    // Step 3: Coach cancels update (empty string)
    Update coachCancel = createUpdate(1001L, "admin_user", "Coach", "Smith", "");
    response = sendWebhookRequest(coachCancel);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "Question update cancelled.");
  }

  @Test
  void test_sessionFlow_reuse() {
    // Pre-create a session with questions in the database
    Session existingSession =
        new Session(
            null,
            "Previous Session",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1));
    Session savedSession = sessionRepository.save(existingSession);

    // Create questions for the previous session
    Question beforeQuestion =
        new Question(null, "What was your goal?", QuestionType.BEFORE, 1, savedSession.id());
    Question afterQuestion =
        new Question(null, "How did it go?", QuestionType.AFTER, 1, savedSession.id());
    questionRepository.save(beforeQuestion);
    questionRepository.save(afterQuestion);

    // Step 1: Admin creates a new session using /session command
    Update createSessionCommand =
        createUpdate(1001L, "admin_user", "Admin", "Adminin", "/session New Training Session");
    ResponseEntity<String> response = sendWebhookRequest(createSessionCommand);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "Session 'New Training Session' created successfully!");

    // Step 2: Admin checks that questions are automatically reused from previous session
    Update checkQuestionsCommand =
        createUpdate(1001L, "admin_user", "Admin", "Adminin", "/questions");
    response = sendWebhookRequest(checkQuestionsCommand);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*\nName: New Training Session\nCreated:");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n\n🔵 *Before Questions:*\n- What was your goal?\n\n🔴 *After Questions:*\n- How did it go?\n");
    assertResponseContains(body, "Please provide questions in the following format:");
    assertResponseContains(body, "Send empty string to cancel.");

    // Step 3: Player uses /before command and gets the reused questions
    Update playerBefore = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    response = sendWebhookRequest(playerBefore);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Session:* New Training Session (created:");
    assertResponseContains(body, "Let's start with the pre-session questions:");
    assertResponseContains(body, "What was your goal?");
    assertResponseContains(body, "Please answer this question:");

    // Step 4: Player answers the before question
    Update playerAnswer =
        createUpdate(2001L, "player_user", "Player", "Johnson", "I want to improve my technique");
    response = sendWebhookRequest(playerAnswer);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "✅ Done for now, good luck with the session, run `/after` command once you finish it.");

    // Step 5: Player uses /after command and gets the reused questions
    Update playerAfter = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    response = sendWebhookRequest(playerAfter);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Session:* New Training Session (created:");
    assertResponseContains(body, "Let's start with the post-session questions:");
    assertResponseContains(body, "How did it go?");
    assertResponseContains(body, "Please answer this question:");

    // Step 6: Player answers the after question
    Update playerAfterAnswer =
        createUpdate(
            2001L, "player_user", "Player", "Johnson", "It went great, I improved significantly");
    response = sendWebhookRequest(playerAfterAnswer);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "✅ Done, thanks for your answers!");

    // Step 7: Verify the journal contains the reused questions
    Update playerLast = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    response = sendWebhookRequest(playerLast);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "📝 *Last Journal Entry:*\n\n🔵 *Before Questions:*\n- What was your goal?\n\n🔴 *After Questions:*\n- How did it go?\n\n*Answers:*\n- I want to improve my technique\n- It went great, I improved significantly");
  }
}
