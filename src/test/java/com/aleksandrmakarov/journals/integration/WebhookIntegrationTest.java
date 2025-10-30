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
import com.aleksandrmakarov.journals.repository.SqliteJournalRepository;
import com.aleksandrmakarov.journals.repository.SqliteQuestionRepository;
import com.aleksandrmakarov.journals.repository.SqliteSessionRepository;
import com.aleksandrmakarov.journals.repository.SqliteUserRepository;
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
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Integration tests for the WebhookController. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WebhookIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private SqliteUserRepository userRepository;
  @Autowired private SqliteSessionRepository sessionRepository;
  @Autowired private SqliteQuestionRepository questionRepository;
  @Autowired private SqliteJournalRepository journalRepository;
  @Autowired private TestJournalsBot testBot;

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

    Chat chat = new Chat(user.telegramId(), "private");
    message.setChat(chat);

    org.telegram.telegrambots.meta.api.objects.User from =
        new org.telegram.telegrambots.meta.api.objects.User(
            user.telegramId(), user.username(), false);
    from.setFirstName(user.firstName());
    from.setLastName(user.lastName());
    message.setFrom(from);

    update.setMessage(message);
    return update;
  }

  private ResponseEntity<String> sendWebhookRequest(TestUser user, String messageText) {
    Update update = createUpdate(user, messageText);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Update> request = new HttpEntity<>(update, headers);
    return restTemplate.exchange("/webhook", HttpMethod.POST, request, String.class);
  }

  private String sendWebhookRequestAndGetResponse(TestUser user, String messageText) {
    ResponseEntity<String> response = sendWebhookRequest(user, messageText);
    assertEquals("OK", response.getBody());
    return testBot.getLastResponse();
  }

  @Test
  void test_start_welcome() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/start");
    // Assert
    assertThat(response)
        .contains(
            "Welcome to AM Journals Bot. Use /before and /after to answer questions before and after the session. Use /admins to see list of admins.");
  }

  @Test
  void test_help_player() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/help");
    // Assert
    assertThat(response)
        .contains(
            "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👤 <b>Player Commands:</b>\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n/admins - View list of admins");
  }

  @Test
  void test_help_admin() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(admin, "/help");
    // Assert
    assertThat(response)
        .contains(
            "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👨‍🏫 <b>Admin Commands:</b>\n/session - View/replace current session\n/set_questions - Set current session questions\n/participants - View all participants\n/promote - Promote a user to admin role\n/ban - Ban a user, journals will stay\n/unban - Unban a user\n\n👤 <b>Player Commands:</b>\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n/admins - View list of admins");
  }

  @Test
  void test_before_noActiveSession() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    // Assert
    assertThat(response)
        .contains("No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_after_noActiveSession() {
    // Arrange
    createPlayerUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    // Assert
    assertThat(response)
        .contains("No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_last_noJournals() {
    // Arrange
    createPlayerUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    // Assert
    assertThat(response).contains("No journals found.");
  }

  @Test
  void test_last5_noJournals() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last5");
    // Assert
    assertThat(response).contains("No journals found.");
  }

  @Test
  void test_last50_noJournals() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last50");
    // Assert
    assertThat(response).contains("No journals found.");
  }

  @Test
  void test_admins_noAdmins() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/admins");
    // Assert
    assertThat(response).contains("No admins found.");
  }

  @Test
  void test_admins_oneAdmin() {
    // Arrange
    createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/admins");
    // Assert
    assertThat(response).contains("📋 <b>Admins:</b>\n👤 Coach Smith (@admin_user)");
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
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last5");

    // Assert. Expect to see journals from 2 sessions, session 1 with all questions
    // answered, session 3 with only one question answered.
    assertThat(response)
        .contains(
            "Last 5 journals:\n\n📅 2025-10-17 12:00:00 'Session 1':\n(BEFORE) S1 B1 - S1 B1 answer\n(BEFORE) S1 B2 - S1 B2 answer\n(AFTER) S1 A1 - S1 A1 answer\n(AFTER) S1 A2 - S1 A2 answer\n📅 2025-10-19 12:00:00 'Session 3':\n(BEFORE) S3 B1 - S3 B1 answer\n");
  }

  @Test
  void test_participants_noParticipantsWithJournals() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(admin, "/participants");
    // Assert
    assertThat(response).contains("No participants found.");
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
    journalRepository.saveBatch(
        List.of(
            new Journal(null, "Answer 21", now, player1.id(), session2.id(), question2Ids.get(0)),
            new Journal(null, "Answer 22", now, player1.id(), session2.id(), question2Ids.get(1))));
    // Act
    String response = sendWebhookRequestAndGetResponse(admin, "/participants");
    // Assert
    assertThat(response)
        .contains("📋 <b>Participants:</b>\n👤 Player Johnson (@player_user) - 1 session(s)");
  }

  @Test
  void test_textInput_unknownCommand() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/unknown");
    // Assert
    assertThat(response).contains("Unknown command. Use /help to see available commands.");
  }

  @Test
  void test_textInput_noState() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "random text");
    // Assert
    assertThat(response)
        .contains(
            "You are not in a state of handling direct input. Run some command first, use /help to see a list.");
  }

  @Test
  void test_sessionFlow_onePlayer() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Coach creates a session.
    String response = sendWebhookRequestAndGetResponse(admin, "/session Default Session");
    assertThat(response)
        .contains(
            "✅ Session 'Default Session' created successfully!\n\nNo questions found for active session.\n"
                + QUESTIONS_UPDATE_EXPLANATION);

    // Step 2: Coach provides questions.
    response =
        sendWebhookRequestAndGetResponse(admin, "Before: B1?\nBefore: B2\nAfter: A1\nAfter: A2");
    assertThat(response)
        .contains(
            "Questions updated successfully to:\nBEFORE: B1?\nBEFORE: B2\nAFTER: A1\nAFTER: A2\n\n");

    // Step 3: Player starts before questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    // Assert
    assertThat(response).contains("📝 <b>Session:</b> Default Session (created: ");
    assertThat(response)
        .contains(")\nPlease answer the following pre-session questions, send empty string to cancel the flow:\n❓ B1?");

    // Step 4: Player answers first 'before' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 answer");
    assertThat(response).contains("☑️ Answer saved!\n❓ B2");

    // Step 5: Player answers second 'before' question - should automatically
    // transition to 'after' questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B2 answer");
    assertThat(response)
        .contains(
            "✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 6: Player starts 'after' questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    // Assert
    assertThat(response).contains("📝 <b>Session:</b> Default Session (created: ");
    assertThat(response)
        .contains(")\nPlease answer the following post-session questions, send empty string to cancel the flow:\n❓ A1");

    // Step 7: Player answers first 'after' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A1 answer");
    assertThat(response).contains("☑️ Answer saved!\n❓ A2");

    // Step 8: Player answers second 'after' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A2 answer");
    assertThat(response).contains("✅ Done, thank you for your answers!");

    // Step 9: Player checks their last journal.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertThat(response).contains("Last journal:\n\n📅 ");
    assertThat(response)
        .contains(
            " 'Default Session':\n(BEFORE) B1? - B1 answer\n(BEFORE) B2 - B2 answer\n(AFTER) A1 - A1 answer\n(AFTER) A2 - A2 answer\n");
  }

  @Test
  void test_updateQuestions_ok() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Create session.
    String response = sendWebhookRequestAndGetResponse(admin, "/session Default Session");
    assertThat(response).contains("created successfully!");

    // Step 2: Check current questions.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    // Assert
    assertThat(response).contains("📝 <b>Current Session:</b>");

    // Step 3: Set questions.
    response = sendWebhookRequestAndGetResponse(admin, "Before: B1 initial?\nAfter: A1 initial?");

    // Step 2: Coach checks current questions
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertThat(response).contains("📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertThat(response)
        .contains("\n📋 <b>Questions:</b>\nBEFORE: B1 initial?\nAFTER: A1 initial?");

    // Step 3: Coach updates questions
    response =
        sendWebhookRequestAndGetResponse(
            admin, "Before: B1 updated?\nAfter: A1 updated?\nAfter: A2 updated?");
    assertThat(response)
        .contains(
            "Questions updated successfully to:\nBEFORE: B1 updated?\nAFTER: A1 updated?\nAFTER: A2 updated?");

    // Step 4: Coach verifies updated questions
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertThat(response).contains("📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertThat(response)
        .contains(
            "\n📋 <b>Questions:</b>\nBEFORE: B1 updated?\nAFTER: A1 updated?\nAFTER: A2 updated?");
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
    String response = sendWebhookRequestAndGetResponse(admin, "/session Default Session");
    assertThat(response)
        .contains(
            "✅ Session 'Default Session' created successfully!\n\n📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertThat(response)
        .contains(
            "\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Coach runs /set_questions command.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertThat(response).contains("📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertThat(response)
        .contains(
            "\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\n" + QUESTIONS_UPDATE_EXPLANATION);

    // Step 3: Coach cancels update (empty string)
    response = sendWebhookRequestAndGetResponse(admin, "");
    assertThat(response).contains("Questions update cancelled.");
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
    String response = sendWebhookRequestAndGetResponse(admin, "/session New Training Session");
    assertThat(response)
        .contains(
            "✅ Session 'New Training Session' created successfully!\n\n📝 <b>Current Session:</b>\nName: New Training Session\nCreated: ");
    assertThat(response)
        .contains(
            "\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Player uses `/before` command and gets the reused questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    assertThat(response).contains("📝 <b>Session:</b> New Training Session (created:");
    assertThat(response)
        .contains(")\nPlease answer the following pre-session questions, send empty string to cancel the flow:\n❓ B1");

    // Step 3: Player answers the before question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 answer");
    assertThat(response)
        .contains(
            "✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 4: Player uses /after command and gets the reused questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    assertThat(response).contains("📝 <b>Session:</b> New Training Session (created:");
    assertThat(response)
        .contains(")\nPlease answer the following post-session questions, send empty string to cancel the flow:\n❓ A1");

    // Step 5: Player answers the after question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A1 answer");
    assertThat(response).contains("✅ Done, thank you for your answers!");

    // Step 6: Player checks the journal.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertThat(response).contains("Last journal:\n\n📅 ");
    assertThat(response)
        .contains(" 'New Training Session':\n(BEFORE) B1 - B1 answer\n(AFTER) A1 - A1 answer\n");
  }

  @Test
  void test_banAndUnban_userJournalsPersist() {
    TestUser admin = createAdminUser();
    User player = createPlayerUser();
    Long playerId = player.id();
    LocalDateTime now = LocalDateTime.now();
    Session session = sessionRepository.save(new Session(null, "Session 1", now, now));
    Question q1 = new Question(null, "Q1", QuestionType.BEFORE, 1, session.id());
    Question savedQ1 = questionRepository.save(q1);
    journalRepository.save(new Journal(null, "A1", now, playerId, session.id(), savedQ1.id()));

    String response = sendWebhookRequestAndGetResponse(admin, "/ban @" + PLAYER.username());
    assertThat(response).contains("is banned.");

    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertThat(response)
        .contains("You are banned from the bot. Please contact the admin to unban.");
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    assertThat(response)
        .contains("You are banned from the bot. Please contact the admin to unban.");

    response = sendWebhookRequestAndGetResponse(admin, "/unban @" + PLAYER.username());
    assertThat(response).contains("is unbanned.");

    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertThat(response).contains("Last journal:\n\n📅 ");
    assertThat(response)
        .contains(" 'Session 1':\n(BEFORE) Q1 - A1\n");
  }
}
