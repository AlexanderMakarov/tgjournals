package com.aleksandrmakarov.journals.integration;

import static com.aleksandrmakarov.journals.bot.BotCommandHandler.QUESTIONS_UPDATE_EXPLANATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.QuestionType;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import com.aleksandrmakarov.journals.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
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

/** Integration tests for the WebhookController. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WebhookIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private JournalRepository journalRepository;

  // Test user record
  public record TestUser(Long telegramId, String username, String firstName, String lastName) {}

  // Test user constants
  private static final TestUser ADMIN = new TestUser(1001L, "admin_user", "Coach", "Smith");
  private static final TestUser PLAYER = new TestUser(2001L, "player_user", "Player", "Johnson");

  static {
    try {
      Files.deleteIfExists(Path.of("test-journals.db"));
      Files.deleteIfExists(Path.of("test-journals.db-wal"));
      Files.deleteIfExists(Path.of("test-journals.db-shm"));
    } catch (IOException ignored) {
    }
  }

  @BeforeEach
  void setUp() {
    // Clean up all data
    journalRepository.deleteAll();
    questionRepository.deleteAll();
    sessionRepository.deleteAll();
    userRepository.deleteAll();
  }

  private TestUser createAdminUser() {
    User user =
        new User(
            null,
            ADMIN.telegramId(),
            ADMIN.username(),
            ADMIN.firstName(),
            ADMIN.lastName(),
            UserRole.ADMIN,
            LocalDateTime.now(),
            null,
            null,
            0,
            null);
    user = userRepository.save(user);
    return new TestUser(user.telegramId(), user.username(), user.firstName(), user.lastName());
  }

  private User createPlayerUser() {
    User user =
        new User(
            null,
            PLAYER.telegramId(),
            PLAYER.username(),
            PLAYER.firstName(),
            PLAYER.lastName(),
            UserRole.PLAYER,
            LocalDateTime.now(),
            null,
            null,
            0,
            null);
    user = userRepository.save(user);
    return user;
  }

  private Update createUpdate(TestUser user, String messageText) {
    Update update = new Update();
    update.setUpdateId(1);

    Message message = new Message();
    message.setMessageId(1);
    message.setText(messageText);
    message.setDate((int) (System.currentTimeMillis() / 1000));

    Chat chat = new Chat();
    chat.setId(user.telegramId());
    chat.setType("private");
    message.setChat(chat);

    org.telegram.telegrambots.meta.api.objects.User from =
        new org.telegram.telegrambots.meta.api.objects.User();
    from.setId(user.telegramId());
    from.setUserName(user.username());
    from.setFirstName(user.firstName());
    from.setLastName(user.lastName());
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
            .replace("👨‍🏫", "\\uD83D\\uDC68‍\\uD83C\\uDFEB")
            .replace("📝", "\\uD83D\\uDCDD")
            .replace("📋", "\\uD83D\\uDCCB")
            .replace("🔵", "\\uD83D\\uDD35")
            .replace("🔴", "\\uD83D\\uDD34")
            .replace("📅", "\\uD83D\\uDCC5")
            .replace("\n", "\\n");
    // Use AssertJ because error message is more readable.
    assertThat(responseBody).contains(encodedExpectedText);
  }

  @Test
  void test_start_welcome() {
    Update startCommand = createUpdate(PLAYER, "/start");
    ResponseEntity<String> response = sendWebhookRequest(startCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "Welcome to AM Journals Bot. Use /before and /after to answer questions before and after the session. Use /admins to see list of admins.");
  }

  @Test
  void test_help_player() {
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/help"));
    // Assert
    String body = response.getBody();
    assertResponseContains(
        body,
        "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👤 *Player Commands:*\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n");
  }

  @Test
  void test_help_admin() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(admin, "/help"));
    // Assert
    String body = response.getBody();
    assertResponseContains(
        body,
        "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👨‍🏫 *Admin Commands:*\n/session - View/replace current session\n/set_questions - Set current session questions\n/participants - View all participants\n/promote - Promote a user to admin role\n\n👤 *Player Commands:*\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n");
  }

  @Test
  void test_before_noActiveSession() {
    Update beforeCommand = createUpdate(PLAYER, "/before");
    ResponseEntity<String> response = sendWebhookRequest(beforeCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body, "No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_after_noActiveSession() {
    // Arrange
    createPlayerUser();
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/after"));
    // Assert
    String body = response.getBody();
    assertResponseContains(
        body, "No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_last_noJournals() {
    // Arrange
    createPlayerUser();
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/last"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_last5_noJournals() {
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/last5"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_last50_noJournals() {
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/last50"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "No journals found.");
  }

  @Test
  void test_admins_noAdmins() {
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/admins"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "No admins found.");
  }

  @Test
  void test_admins_oneAdmin() {
    // Arrange
    createAdminUser();
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/admins"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "📋 *Admins:*\n\n👤 Coach Smith (@admin_user)\n");
  }

  @Test
  void test_last5_manyOptions() {
    // Arrange
    User player = createPlayerUser();
    Long playerId = player.id();
    // Session 1 has all questions answered.
    LocalDateTime time = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
    Session session1 = sessionRepository.save(new Session(null, "Session 1", time, time));
    Question q11 = new Question(null, "S1 B1", QuestionType.BEFORE, 1, session1.id());
    Question q12 = new Question(null, "S1 B2", QuestionType.BEFORE, 2, session1.id());
    Question q13 = new Question(null, "S1 A1", QuestionType.AFTER, 1, session1.id());
    Question q14 = new Question(null, "S1 A2", QuestionType.AFTER, 2, session1.id());
    java.util.List<Long> s1QuestionIds = questionRepository.saveBatch(List.of(q11, q12, q13, q14));
    journalRepository.saveBatch(
        List.of(
            new Journal(null, "S1 B1 answer", time, playerId, session1.id(), s1QuestionIds.get(0)),
            new Journal(null, "S1 B2 answer", time, playerId, session1.id(), s1QuestionIds.get(1)),
            new Journal(null, "S1 A1 answer", time, playerId, session1.id(), s1QuestionIds.get(2)),
            new Journal(
                null, "S1 A2 answer", time, playerId, session1.id(), s1QuestionIds.get(3))));
    // Session 2 has no questions answered.
    time = time.plusDays(1);
    Session session2 = sessionRepository.save(new Session(null, "Session 2", time, time));
    Question q21 = new Question(null, "S2 B1", QuestionType.BEFORE, 1, session2.id());
    questionRepository.saveBatch(List.of(q21));
    // Session 3 has only one question answered.
    time = time.plusDays(1);
    Session session3 = sessionRepository.save(new Session(null, "Session 3", time, time));
    Question q31 = new Question(null, "S3 B1", QuestionType.BEFORE, 1, session3.id());
    Question q32 = new Question(null, "S3 A1", QuestionType.AFTER, 1, session3.id());
    java.util.List<Long> s3QuestionIds = questionRepository.saveBatch(List.of(q31, q32));
    journalRepository.saveBatch(
        List.of(
            new Journal(
                null, "S3 B1 answer", time, playerId, session3.id(), s3QuestionIds.get(0))));

    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(PLAYER, "/last5"));

    // Assert. Expect to see journals from 2 sessions, session 1 with all questions
    // answered, session 3 with only one question answered.
    String body = response.getBody();
    assertResponseContains(
        body,
        "Last 5 journals:\n\n📅 2025-10-17 12:00:00 'Session 1':\n(BEFORE) S1 B1 - S1 B1 answer\n(BEFORE) S1 B2 - S1 B2 answer\n(AFTER) S1 A1 - S1 A1 answer\n(AFTER) S1 A2 - S1 A2 answer\n\n📅 2025-10-19 12:00:00 'Session 3':\n(BEFORE) S3 B1 - S3 B1 answer\n\n");
  }

  @Test
  void test_participants_noParticipantsWithJournals() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(admin, "/participants"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "📋 *Participants:*\n\n");
  }

  @Test
  void test_participants_twoParticipantsWithJournals() {
    // Arrange
    LocalDateTime now = LocalDateTime.now();
    TestUser admin = createAdminUser();
    User player1 = createPlayerUser();
    // Session 1 has one question answered.
    Session session1 = sessionRepository.save(new Session(null, "Session 1", now, now));
    Question question1 = new Question(null, "Question 1", QuestionType.BEFORE, 1, session1.id());
    questionRepository.save(question1);
    // Session 2 has two questions answered.
    Session session2 = sessionRepository.save(new Session(null, "Session 2", now, now));
    Question question21 = new Question(null, "Question 21", QuestionType.AFTER, 1, session2.id());
    Question question22 = new Question(null, "Question 22", QuestionType.AFTER, 2, session2.id());
    List<Long> question2Ids = questionRepository.saveBatch(List.of(question21, question22));
    journalRepository.saveBatch(List.of(
        new Journal(null, "Answer 21", now, player1.id(), session2.id(), question2Ids.get(0)),
        new Journal(null, "Answer 22", now, player1.id(), session2.id(), question2Ids.get(1)))
    );
    // Act
    ResponseEntity<String> response = sendWebhookRequest(createUpdate(admin, "/participants"));
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "📋 *Participants:*\n\n👤 Player Johnson (@player_user) - 1 session(s)\n");
  }

  @Test
  void test_textInput_unknownCommand() {
    // Arrange
    Update unknownCommand = createUpdate(PLAYER, "/unknown");
    // Act
    ResponseEntity<String> response = sendWebhookRequest(unknownCommand);
    // Assert
    String body = response.getBody();
    assertResponseContains(body, "Unknown command. Use `/help` to see available commands.");
  }

  @Test
  void test_textInput_noState() {
    Update unknownCommand = createUpdate(PLAYER, "random text");
    ResponseEntity<String> response = sendWebhookRequest(unknownCommand);
    String body = response.getBody();
    assertResponseContains(body, "No state found. Use `/help` to see available commands.");
  }

  @Test
  void test_sessionFlow_onePlayer() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Coach creates a session.
    Update createSession = createUpdate(admin, "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(createSession);
    String body = response.getBody();
    // Check that without "previous session" coach is switched to questions update
    // mode.
    assertResponseContains(
        body,
        "✅ Session 'Default Session' created successfully!\n\nNo questions found for active session.\n"
            + QUESTIONS_UPDATE_EXPLANATION);

    // Step 2: Coach provides questions.
    Update coachSetsQuestions =
        createUpdate(admin, "Before: B1?\nBefore: B2\nAfter: A1\nAfter: A2");
    response = sendWebhookRequest(coachSetsQuestions);
    body = response.getBody();
    assertResponseContains(
        body,
        "Questions updated successfully to:\n- BEFORE: B1?\n- BEFORE: B2\n- AFTER: A1\n- AFTER: A2\n\n");

    // Step 3: Player starts before questions.
    Update playerBefore = createUpdate(PLAYER, "/before");
    response = sendWebhookRequest(playerBefore);
    body = response.getBody();
    // Check complete response format for /before command.
    assertResponseContains(body, "📝 *Session:* Default Session (created: ");
    assertResponseContains(body, ")\nPlease answer the following pre-session questions:\n❓ B1?");

    // Step 4: Player answers first 'before' question.
    Update playerAnswer1 = createUpdate(PLAYER, "B1 answer");
    response = sendWebhookRequest(playerAnswer1);
    body = response.getBody();
    assertResponseContains(body, "☑️ Answer saved!\n❓ B2");

    // Step 5: Player answers second 'before' question - should automatically
    // transition to 'after' questions.
    Update playerAnswer2 = createUpdate(PLAYER, "B2 answer");
    response = sendWebhookRequest(playerAnswer2);
    body = response.getBody();
    assertResponseContains(
        body, "✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 6: Player starts 'after' questions.
    Update playerAfter = createUpdate(PLAYER, "/after");
    response = sendWebhookRequest(playerAfter);
    body = response.getBody();
    // Check complete response format for /after command.
    assertResponseContains(body, "📝 *Session:* Default Session (created: ");
    assertResponseContains(body, ")\nPlease answer the following post-session questions:\n❓ A1");

    // Step 7: Player answers first 'after' question.
    Update playerAnswer3 = createUpdate(PLAYER, "A1 answer");
    response = sendWebhookRequest(playerAnswer3);
    body = response.getBody();
    assertResponseContains(body, "☑️ Answer saved!\n❓ A2");

    // Step 8: Player answers second 'after' question.
    Update playerAnswer4 = createUpdate(PLAYER, "A2 answer");
    response = sendWebhookRequest(playerAnswer4);
    body = response.getBody();
    assertResponseContains(body, "✅ Done, thank you for your answers!");

    // Step 9: Player checks their last journal.
    Update playerLast = createUpdate(PLAYER, "/last");
    response = sendWebhookRequest(playerLast);
    body = response.getBody();
    assertResponseContains(body, "Last journal:\n\n📅 ");
    assertResponseContains(
        body,
        " 'Default Session':\n(BEFORE) B1? - B1 answer\n(BEFORE) B2 - B2 answer\n(AFTER) A1 - A1 answer\n(AFTER) A2 - A2 answer\n\n");
  }

  @Test
  void test_updateQuestions_ok() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Create session.
    Update ensureSession = createUpdate(admin, "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(ensureSession);
    String body = response.getBody();
    assertResponseContains(body, "created successfully!");

    // Step 2: Check current questions.
    Update coachQuestions1 = createUpdate(admin, "/set_questions");
    response = sendWebhookRequest(coachQuestions1);
    body = response.getBody();
    assertResponseContains(body, "📝 *Current Session:*");

    // Step 3: Set questions.
    Update coachSetsInitial =
        createUpdate(
            admin, "Before: What is your main focus today?\n" + "After: How did the session go?");
    response = sendWebhookRequest(coachSetsInitial);
    body = response.getBody();
    assertResponseContains(
        body,
        "Questions updated successfully to:\n- BEFORE: What is your main focus today?\n- AFTER: How did the session go?\n\n");

    // Step 2: Coach checks current questions
    Update coachCheckQuestions = createUpdate(admin, "/set_questions");
    response = sendWebhookRequest(coachCheckQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    // Check complete response format
    assertResponseContains(body, "📝 *Current Session:*\nName: Default Session\nCreated: ");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n- BEFORE: What is your main focus today?\n- AFTER: How did the session go?\n\n");

    // Step 3: Coach updates questions
    Update coachUpdatesQuestions =
        createUpdate(
            admin,
            "Before: What is your main focus today?\n"
                + "Before: Any concerns before we start?\n"
                + "After: How did the session go?\n"
                + "After: What did you learn?");
    response = sendWebhookRequest(coachUpdatesQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(
        body,
        "Questions updated successfully to:\n- BEFORE: What is your main focus today?\n- BEFORE: Any concerns before we start?\n- AFTER: How did the session go?\n- AFTER: What did you learn?\n\n");

    // Step 4: Coach verifies updated questions
    Update coachVerifyQuestions = createUpdate(admin, "/set_questions");
    response = sendWebhookRequest(coachVerifyQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "📝 *Current Session:*\nName: Default Session\nCreated: ");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n- BEFORE: What is your main focus today?\n- BEFORE: Any concerns before we start?\n- AFTER: How did the session go?\n- AFTER: What did you learn?\n\n");
  }

  @Test
  void test_updateQuestionsForNewSession_cancel() {
    // Arrange.
    TestUser admin = createAdminUser();
    // Create "previous session" with questions.
    LocalDateTime time = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
    Session previousSession =
        sessionRepository.save(new Session(null, "Previous Session", time, time));
    Question question1 = new Question(null, "B1", QuestionType.BEFORE, 1, previousSession.id());
    Question question2 = new Question(null, "A1", QuestionType.AFTER, 2, previousSession.id());
    questionRepository.saveBatch(List.of(question1, question2));

    // Step 1: Coach creates a session.
    Update createSession = createUpdate(admin, "/session Default Session");
    ResponseEntity<String> response = sendWebhookRequest(createSession);
    String body = response.getBody();
    assertResponseContains(
        body,
        "✅ Session 'Default Session' created successfully!\n\n📝 *Current Session:*\nName: Default Session\nCreated: ");
    assertResponseContains(
        body,
        "\n\n📋 *Current Questions:*\n- BEFORE: B1\n- AFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Coach runs /set_questions command.
    Update coachRunsQuestions = createUpdate(admin, "/set_questions");
    response = sendWebhookRequest(coachRunsQuestions);
    body = response.getBody();
    assertResponseContains(body, "📝 *Current Session:*\nName: Default Session\nCreated: ");
    assertResponseContains(
        body,
        "\n\n📋 *Current Questions:*\n- BEFORE: B1\n- AFTER: A1\n\n"
            + QUESTIONS_UPDATE_EXPLANATION);

    // Step 3: Coach cancels update (empty string)
    Update coachCancel = createUpdate(admin, "");
    response = sendWebhookRequest(coachCancel);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertResponseContains(body, "Questions update cancelled.");
  }

  @Test
  void test_sessionFlow_reuse() {
    // Arrange
    TestUser admin = createAdminUser();
    // Pre-create a session with questions in the database.
    Session existingSession =
        new Session(
            null,
            "Previous Session",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1));
    Session savedSession = sessionRepository.save(existingSession);
    Question beforeQuestion = new Question(null, "B1", QuestionType.BEFORE, 1, savedSession.id());
    Question afterQuestion = new Question(null, "A1", QuestionType.AFTER, 2, savedSession.id());
    questionRepository.save(beforeQuestion);
    questionRepository.save(afterQuestion);

    // Step 1: Admin creates a new session using `/session` command.
    Update createSessionCommand = createUpdate(admin, "/session New Training Session");
    ResponseEntity<String> response = sendWebhookRequest(createSessionCommand);
    String body = response.getBody();
    assertResponseContains(
        body,
        "✅ Session 'New Training Session' created successfully!\n\n📝 *Current Session:*\nName: New Training Session\nCreated: ");
    assertResponseContains(
        body,
        "📋 *Current Questions:*\n- BEFORE: B1\n- AFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Player uses `/before` command and gets the reused questions.
    Update playerBefore = createUpdate(PLAYER, "/before");
    response = sendWebhookRequest(playerBefore);
    body = response.getBody();
    assertResponseContains(body, "📝 *Session:* New Training Session (created:");
    assertResponseContains(body, ")\nPlease answer the following pre-session questions:\n❓ B1");

    // Step 3: Player answers the before question.
    Update playerAnswer = createUpdate(PLAYER, "B1 answer");
    response = sendWebhookRequest(playerAnswer);
    body = response.getBody();
    assertResponseContains(
        body, "✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 4: Player uses /after command and gets the reused questions.
    Update playerAfter = createUpdate(PLAYER, "/after");
    response = sendWebhookRequest(playerAfter);
    body = response.getBody();
    assertResponseContains(body, "📝 *Session:* New Training Session (created:");
    assertResponseContains(body, ")\nPlease answer the following post-session questions:\n❓ A1");

    // Step 5: Player answers the after question.
    Update playerAfterAnswer = createUpdate(PLAYER, "A1 answer");
    response = sendWebhookRequest(playerAfterAnswer);
    body = response.getBody();
    assertResponseContains(body, "✅ Done, thank you for your answers!");

    // Step 6: Player checks the journal.
    Update playerLast = createUpdate(PLAYER, "/last");
    response = sendWebhookRequest(playerLast);
    body = response.getBody();
    assertResponseContains(body, "Last journal:\n\n📅 ");
    assertResponseContains(
        body, " 'New Training Session':\n(BEFORE) B1 - B1 answer\n(AFTER) A1 - A1 answer\n\n");
  }
}
