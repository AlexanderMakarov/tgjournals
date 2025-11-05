package com.aleksandrmakarov.journals.integration;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.springframework.test.context.TestConstructor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.aleksandrmakarov.journals.config.TestDatabaseInitializer;
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

/** Integration tests for the WebhookController. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class WebhookIntegrationTest {

  static {
    // Ensure test database is created before Spring context loads
    TestDatabaseInitializer.class.getName();
  }

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private JournalRepository journalRepository;
  @Autowired private TestJournalsBot testBot;

  // Test user record
  public record TestUser(Long telegramId, String username, String firstName, String lastName) {}

  // Test user constants
  private static final TestUser ADMIN = new TestUser(1001L, "admin_user", "Coach", "Smith");
  private static final TestUser PLAYER = new TestUser(2001L, "player_user", "Player", "Johnson");

  // Test database is automatically created by TestDatabaseInitializer if it doesn't exist

  private static void assertContains(String actual, String expectedSubstring) {
    if (!actual.contains(expectedSubstring)) {
      throw new AssertionError(
          String.format(
              "Expecting:%n  \"%s\"%nto contain:%n  \"%s\"%nbut it did not.",
              actual, expectedSubstring));
    }
  }

  private static void assertDoesNotContain(String actual, String unexpectedSubstring) {
    if (actual.contains(unexpectedSubstring)) {
      throw new AssertionError(
          String.format(
              "Expecting:%n  \"%s\"%nnot to contain:%n  \"%s\"%nbut it did.",
              actual, unexpectedSubstring));
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
            null,
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
            null,
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
    from.setLanguageCode("en");
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

  private void verifyParticipantSelectionKeyboard(InlineKeyboardMarkup keyboard, int expectedItems) {
    assertNotNull(keyboard, "Keyboard is null");
    assertNotNull(keyboard.getKeyboard(), "Keyboard rows are null");
    assertTrue(
        keyboard.getKeyboard().size() >= expectedItems,
        "Keyboard rows count " + keyboard.getKeyboard().size() + " is less than expected " + expectedItems);
    
    // Verify participant buttons (one per participant)
    for (int i = 0; i < expectedItems; i++) {
      InlineKeyboardRow row = keyboard.getKeyboard().get(i);
      assertNotNull(row, "Row is null at index " + i);
      assertEquals(1, row.size(), "Row size at index " + i + " is not 1");
      InlineKeyboardButton button = row.get(0);
      assertTrue(button.getText() != null && !button.getText().isEmpty(), "Button text is empty at index " + i);
      assertTrue(button.getCallbackData() != null && button.getCallbackData().startsWith("ps:select:"), "Callback data should start with 'ps:select:' at index " + i + ", got: " + button.getCallbackData());
    }
    
    // Verify navigation buttons (last row)
    InlineKeyboardRow navRow = keyboard.getKeyboard().get(keyboard.getKeyboard().size() - 1);
    assertNotNull(navRow, "Nav row is null");
    assertTrue(navRow.size() >= 1, "Nav row size is less than 1");
    // Check for Cancel button
    boolean hasCancel = navRow.stream().anyMatch(b -> "Cancel".equals(b.getText()) && "ps:cancel".equals(b.getCallbackData()));
    assertTrue(hasCancel, "Cancel button not found in nav row");
  }

  private Update createCallbackQueryUpdate(TestUser user, String callbackData, int messageId) {
    Update update = new Update();
    update.setUpdateId(1);

    Message message = new Message();
    message.setMessageId(messageId);
    message.setDate((int) (System.currentTimeMillis() / 1000));
    Chat chat = new Chat(user.telegramId(), "private");
    message.setChat(chat);

    org.telegram.telegrambots.meta.api.objects.User from =
        new org.telegram.telegrambots.meta.api.objects.User(
            user.telegramId(), user.username(), false);
    from.setFirstName(user.firstName());
    from.setLastName(user.lastName());
    from.setLanguageCode("en");

    CallbackQuery callbackQuery = new CallbackQuery();
    callbackQuery.setId("callback_query_id_" + System.currentTimeMillis());
    callbackQuery.setFrom(from);
    callbackQuery.setData(callbackData);
    callbackQuery.setMessage(message);

    update.setCallbackQuery(callbackQuery);
    return update;
  }

  private String sendCallbackQueryAndGetResponse(TestUser user, String callbackData, int messageId) {
    Update update = createCallbackQueryUpdate(user, callbackData, messageId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Update> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.exchange("/webhook", HttpMethod.POST, request, String.class);
    assertEquals("OK", response.getBody());
    return testBot.getLastResponse();
  }
  

  @Test
  void test_start_welcome() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/start");
    // Assert
    assertContains(
        response,
        "Welcome to AM Journals Bot. Use /before and /after to answer questions before and after the session. Use /admins to see list of admins.");
  }

  @Test
  void test_help_player() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/help");
    // Assert
    assertContains(
        response,
        "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👤 <b>Player Commands:</b>\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n/admins - View list of admins");
  }

  @Test
  void test_help_admin() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(admin, "/help");
    // Assert
    assertContains(
        response,
        "Bot allows to create and view journals with answers on questions for each session (before and after), players can answer questions one-by-one, and admin can view all journals.\n\n👨‍🏫 <b>Admin Commands:</b>\n/session - View/replace current session\n/set_questions - Set current session questions\n/participants - View all participants\n/promote - Promote a user to admin role\n/ban - Ban a user, journals will stay\n/unban - Unban a user\n\n👤 <b>Player Commands:</b>\n/before - Answer pre-session questions\n/after - Answer post-session questions\n/last - View last journal\n/last5 - View last 5 journals\n/last50 - View last 50 journals\n/admins - View list of admins");
  }

  @Test
  void test_before_noActiveSession() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    // Assert
    assertContains(response, "No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_after_noActiveSession() {
    // Arrange
    createPlayerUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    // Assert
    assertContains(response, "No active session found. Please ask your admin to create one first.");
  }

  @Test
  void test_last_noJournals() {
    // Arrange
    createPlayerUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    // Assert
    assertContains(response, "No journals found.");
  }

  @Test
  void test_last5_noJournals() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last5");
    // Assert
    assertContains(response, "No journals found.");
  }

  @Test
  void test_last50_noJournals() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/last50");
    // Assert
    assertContains(response, "No journals found.");
  }

  @Test
  void test_admins_noAdmins() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/admins");
    // Assert
    assertContains(response,"No admins found.");
  }

  @Test
  void test_admins_oneAdmin() {
    // Arrange
    createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/admins");
    // Assert
    assertContains(response,"📋 <b>Admins:</b>\n👤 Coach Smith (@admin_user)");
  }

  @Test
  void test_last5_manyOptionsPlayer() {
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
    assertContains(
        response,
        "Last 5 journals:\n\n📅 2025-10-17 12:00:00 'Session 1':\n(BEFORE) S1 B1 - S1 B1 answer\n(BEFORE) S1 B2 - S1 B2 answer\n(AFTER) S1 A1 - S1 A1 answer\n(AFTER) S1 A2 - S1 A2 answer\n📅 2025-10-19 12:00:00 'Session 3':\n(BEFORE) S3 B1 - S3 B1 answer\n");
  }

  @Test
  void test_participants_noParticipantsWithJournals() {
    // Arrange
    TestUser admin = createAdminUser();
    // Act
    String response = sendWebhookRequestAndGetResponse(admin, "/participants");
    // Assert
    assertContains(response,"No participants found.");
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
    assertContains(response,"📋 <b>Participants:</b>\n👤 Player Johnson (@player_user) - 1 session(s)");
  }

  @Test
  void test_textInput_unknownCommand() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "/unknown");
    // Assert
    assertContains(response,"Unknown command. Use /help to see available commands.");
  }

  @Test
  void test_textInput_noState() {
    // Act
    String response = sendWebhookRequestAndGetResponse(PLAYER, "random text");
    // Assert
    assertContains(response,"You are not in a state of handling direct input. Run some command first, use /help to see a list.");
  }

  @Test
  void test_sessionFlow_full() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Coach creates a session.
    String response = sendWebhookRequestAndGetResponse(admin, "/session Default Session");
    assertContains(response,"✅ Session 'Default Session' created successfully!\n\n📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(response,"\n\nNo questions found for active session.\nUse /set_questions command to set questions.");


    // Step 2: Coach runs set_questions command.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertContains(response,"📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(response,"\n\nPlease provide questions in the following format:\n```\nBefore: Question to answer before the session?\nAfter: Question 1 to answer after the session?\nAfter: Question 2 to answer after the session?\n```\nRun any command to cancel.");

    // Step 3: Coach provides questions.
    response =
        sendWebhookRequestAndGetResponse(admin, "Before: B1?\nBefore: B2\nAfter: A1\nAfter: A2");
    assertContains(response,"Questions updated successfully to:\nBEFORE: B1?\nBEFORE: B2\nAFTER: A1\nAFTER: A2\n\n");

    // Step 4: Player starts before questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    // Assert
    assertContains(response,"📝 <b>Session:</b> Default Session (created: ");
    assertContains(response,")\nPlease answer the following pre-session questions, run any command to cancel the flow:\n❓ B1?");

    // Step 5: Player answers first 'before' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 answer");
    assertContains(response,"☑️ Answer saved!\n❓ B2");

    // Step 6: Player answers second 'before' question - should automatically
    // transition to 'after' questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B2 answer");
    assertContains(response,"✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 7: Player starts 'after' questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    // Assert
    assertContains(response,"📝 <b>Session:</b> Default Session (created: ");
    assertContains(response,")\nPlease answer the following post-session questions, run any command to cancel the flow:\n❓ A1");

    // Step 8: Player answers first 'after' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A1 answer");
    assertContains(response,"☑️ Answer saved!\n❓ A2");

    // Step 9: Player answers second 'after' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A2 answer");
    assertContains(response,"✅ Done, thank you for your answers!");

    // Step 10: Player checks their last journal.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response, "Last journal:\n\n📅 ");
    assertContains(response," 'Default Session':\n(BEFORE) B1? - B1 answer\n(BEFORE) B2 - B2 answer\n(AFTER) A1 - A1 answer\n(AFTER) A2 - A2 answer\n");

    // Step 11: Player executes /before again and answers questions with updated answers.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    assertContains(response,"📝 <b>Session:</b> Default Session (created: ");
    assertContains(response,")\nPlease answer the following pre-session questions, run any command to cancel the flow:\n❓ B1?");

    // Step 12: Player answers first 'before' question with updated answer.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 updated answer");
    assertContains(response,"☑️ Answer saved!\n❓ B2");

    // Step 13: Player answers second 'before' question with updated answer.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B2 updated answer");
    assertContains(response,"✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 14: Player executes /after again and answers questions with updated answers.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    assertContains(response,"📝 <b>Session:</b> Default Session (created: ");
    assertContains(response,")\nPlease answer the following post-session questions, run any command to cancel the flow:\n❓ A1");

    // Step 15: Player answers first 'after' question with updated answer.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A1 updated answer");
    assertContains(response,"☑️ Answer saved!\n❓ A2");

    // Step 16: Player answers second 'after' question with updated answer.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A2 updated answer");
    assertContains(response,"✅ Done, thank you for your answers!");

    // Step 17: Player checks their last journal - should only contain updated answers, no duplicates.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response, "Last journal:\n\n📅 ");
    assertContains(response, " 'Default Session':\n(BEFORE) B1? - B1 updated answer\n(BEFORE) B2 - B2 updated answer\n(AFTER) A1 - A1 updated answer\n(AFTER) A2 - A2 updated answer\n");
    // Verify no old answers are present
    assertDoesNotContain(response, "B1 answer");
    assertDoesNotContain(response, "B2 answer");
    assertDoesNotContain(response, "A1 answer");
    assertDoesNotContain(response, "A2 answer");
  }

  @Test
  void test_sessionFlow_cancel() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Coach creates a session.
    String response = sendWebhookRequestAndGetResponse(admin, "/session first session");
    assertContains(response, "✅ Session 'first session' created successfully!\n\n📝 <b>Current Session:</b>\nName: first session\nCreated: ");
    assertContains(response, "\n\nNo questions found for active session.\nUse /set_questions command to set questions.");

    // Step 2: Coach runs set_questions command.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertContains(response, "📝 <b>Current Session:</b>\nName: first session\nCreated: ");
    assertContains(response,"\n\nPlease provide questions in the following format:\n```\nBefore: Question to answer before the session?\nAfter: Question 1 to answer after the session?\nAfter: Question 2 to answer after the session?\n```\nRun any command to cancel.");

    // Step 3: Coach provides questions.
    response =
        sendWebhookRequestAndGetResponse(admin, "Before: B1?\nBefore: B2\nAfter: A1\nAfter: A2");
    assertContains(response,"Questions updated successfully to:\nBEFORE: B1?\nBEFORE: B2\nAFTER: A1\nAFTER: A2\n\n");

    // Step 4: Player starts before questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    // Assert
    assertContains(response,"📝 <b>Session:</b> first session (created: ");
    assertContains(response,")\nPlease answer the following pre-session questions, run any command to cancel the flow:\n❓ B1?");

    // Step 5: Player answers first 'before' question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 answer");
    assertContains(response,"☑️ Answer saved!\n❓ B2");

    // Step 6: Player cancels flow by running /last command
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response, "Last journal:\n\n📅 ");
    assertContains(response," 'first session':\n(BEFORE) B1? - B1 answer\n");

    // Step 7: Player sends random text
    response = sendWebhookRequestAndGetResponse(PLAYER, "random text");
    assertContains(response,"You are not in a state of handling direct input. Run some command first, use /help to see a list.");
  }

  @Test
  void test_updateQuestions_ok() {
    // Arrange
    TestUser admin = createAdminUser();

    // Step 1: Create session.
    String response = sendWebhookRequestAndGetResponse(admin, "/session Default Session");
    assertContains(response,"created successfully!");

    // Step 2: Check current questions.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    // Assert
    assertContains(response,"📝 <b>Current Session:</b>");

    // Step 3: Set questions.
    sendWebhookRequestAndGetResponse(admin, "Before: B1 initial?\nAfter: A1 initial?");

    // Step 4: Coach checks current questions
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertContains(response,"📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(response, "\n📋 <b>Questions:</b>\nBEFORE: B1 initial?\nAFTER: A1 initial?");

    // Step 5: Coach updates questions
    response =
        sendWebhookRequestAndGetResponse(
            admin, "Before: B1 updated?\nAfter: A1 updated?\nAfter: A2 updated?");
    assertContains(
        response,
        "Questions updated successfully to:\nBEFORE: B1 updated?\nAFTER: A1 updated?\nAFTER: A2 updated?");

    // Step 6: Coach verifies updated questions
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertContains(response,"📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(
        response,
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
    assertContains(
        response,
        "✅ Session 'Default Session' created successfully!\n\n📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(
        response,
        "\n\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Coach runs /set_questions command.
    response = sendWebhookRequestAndGetResponse(admin, "/set_questions");
    assertContains(response,"📝 <b>Current Session:</b>\nName: Default Session\nCreated: ");
    assertContains(
        response,
        "\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\nPlease provide questions in the following format:\n```\nBefore: Question to answer before the session?\nAfter: Question 1 to answer after the session?\nAfter: Question 2 to answer after the session?\n```\nRun any command to cancel.");

    // Step 3: Coach cancels update by running a command (e.g., /help)
    response = sendWebhookRequestAndGetResponse(admin, "/help");
    assertContains(response,"Bot allows to create and view journals");

    // Step 4: Coach sends random text
    response = sendWebhookRequestAndGetResponse(admin, "random text");
    assertContains(response,"You are not in a state of handling direct input. Run some command first, use /help to see a list.");
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
    assertContains(
        response,
        "✅ Session 'New Training Session' created successfully!\n\n📝 <b>Current Session:</b>\nName: New Training Session\nCreated: ");
    assertContains(
        response,
        "\n📋 <b>Questions:</b>\nBEFORE: B1\nAFTER: A1\n\nUse /set_questions command if need to update questions.");

    // Step 2: Player uses `/before` command and gets the reused questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    assertContains(response,"📝 <b>Session:</b> New Training Session (created:");
    assertContains(response, ")\nPlease answer the following pre-session questions, run any command to cancel the flow:\n❓ B1");

    // Step 3: Player answers the before question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "B1 answer");
    assertContains(
        response,
        "✅ Done for now, good luck with the session, run /after command once you finish it.");

    // Step 4: Player uses /after command and gets the reused questions.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/after");
    assertContains(response,"📝 <b>Session:</b> New Training Session (created:");
    assertContains(response, ")\nPlease answer the following post-session questions, run any command to cancel the flow:\n❓ A1");

    // Step 5: Player answers the after question.
    response = sendWebhookRequestAndGetResponse(PLAYER, "A1 answer");
    assertContains(response,"✅ Done, thank you for your answers!");

    // Step 6: Player checks the journal.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response,"Last journal:\n\n📅 ");
    assertContains(response, " 'New Training Session':\n(BEFORE) B1 - B1 answer\n(AFTER) A1 - A1 answer\n");
  }

  @Test
  void test_last_adminUserWithJournals() {
    // Arrange: Admin views journals of a separate player
    TestUser admin = createAdminUser();
    User player = createPlayerUser();
    Long playerId = player.id();
    LocalDateTime now = LocalDateTime.now();
    Session session = sessionRepository.save(new Session(null, "Session 1", now, now));
    Question q1 = new Question(null, "Q1", QuestionType.BEFORE, 1, session.id());
    Question q2 = new Question(null, "Q2", QuestionType.AFTER, 2, session.id());
    List<Long> questionIds = questionRepository.saveBatch(List.of(q1, q2));
    journalRepository.saveBatch(
        List.of(
            new Journal(null, "A1", now, playerId, session.id(), questionIds.get(0)),
            new Journal(null, "A2", now, playerId, session.id(), questionIds.get(1))));

    // Act: Admin uses /last command (should show participant selection with inline keyboard)
    sendWebhookRequest(admin, "/last");
    String response = testBot.getLastResponse();
    InlineKeyboardMarkup keyboard = testBot.getLastInlineKeyboard();

    // Assert: Should show participant list with inline keyboard (admin also appears in list with 0 sessions)
    assertContains(response, "Last journal\n[1-2/2]");
    assertNotNull(keyboard, "Keyboard is null but should be present. Response was: " + response);
    verifyParticipantSelectionKeyboard(keyboard, 2);
    
    // Get player's button callback data
    InlineKeyboardRow playerRow = keyboard.getKeyboard().get(0);
    String playerCallbackData = playerRow.get(0).getCallbackData();
    assertTrue(playerCallbackData != null && playerCallbackData.startsWith("ps:select:"), "Callback data should start with 'ps:select:', got: " + playerCallbackData);

    // Act: Admin selects player (by pressing player's button)
    response = sendCallbackQueryAndGetResponse(admin, playerCallbackData, 1);

    // Assert: Should show player's journals
    assertContains(
        response,
        "Last journal:\n\n📅 "
            + now.format(com.aleksandrmakarov.journals.bot.BotCommandHandler.DATETIME_FORMATTER)
            + " 'Session 1':\n(BEFORE) Q1 - A1\n(AFTER) Q2 - A2\n");
  }

  @Test
  void test_last_adminHimselfWithJournals() {
    // Arrange: Admin has their own journals (as a player)
    User adminUser = userRepository.save(
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
            null,
            null));
    Long adminId = adminUser.id();
    LocalDateTime now = LocalDateTime.now();
    Session session = sessionRepository.save(new Session(null, "Session 1", now, now));
    Question q1 = new Question(null, "Q1", QuestionType.BEFORE, 1, session.id());
    Question q2 = new Question(null, "Q2", QuestionType.AFTER, 2, session.id());
    List<Long> questionIds = questionRepository.saveBatch(List.of(q1, q2));
    journalRepository.saveBatch(
        List.of(
            new Journal(null, "Admin A1", now, adminId, session.id(), questionIds.get(0)),
            new Journal(null, "Admin A2", now, adminId, session.id(), questionIds.get(1))));

    // Act: Admin uses /last command
    sendWebhookRequest(ADMIN, "/last");
    String response = testBot.getLastResponse();
    InlineKeyboardMarkup keyboard = testBot.getLastInlineKeyboard();

    // Assert: Should show participant selection with admin in the list and inline keyboard
    assertContains(response, "Last journal\n[1-1/1]");
    assertNotNull(keyboard, "Keyboard is null but should be present. Response was: " + response);
    verifyParticipantSelectionKeyboard(keyboard, 1);
    
    // Get admin's button callback data
    InlineKeyboardRow adminRow = keyboard.getKeyboard().get(0);
    String adminCallbackData = adminRow.get(0).getCallbackData();
    assertTrue(adminCallbackData != null && adminCallbackData.startsWith("ps:select:"), "Callback data should start with 'ps:select:', got: " + adminCallbackData);

    // Act: Admin selects themselves from the list (by pressing admin's button)
    response = sendCallbackQueryAndGetResponse(ADMIN, adminCallbackData, 1);

    // Assert: Should show admin's own journals
    assertContains(
        response,
        "Last journal:\n\n📅 "
            + now.format(com.aleksandrmakarov.journals.bot.BotCommandHandler.DATETIME_FORMATTER)
            + " 'Session 1':\n(BEFORE) Q1 - Admin A1\n(AFTER) Q2 - Admin A2\n");
  }

  @Test
  void test_banAndUnban_userJournalsPersist() {
    // Arrange
    TestUser admin = createAdminUser();
    User player = createPlayerUser();
    Long playerId = player.id();
    LocalDateTime now = LocalDateTime.now();
    Session session = sessionRepository.save(new Session(null, "Session 1", now, now));
    Question q1 = new Question(null, "Q1", QuestionType.BEFORE, 1, session.id());
    Question savedQ1 = questionRepository.save(q1);
    journalRepository.save(new Journal(null, "A1", now, playerId, session.id(), savedQ1.id()));

    // Step 1: Admin uses /ban command (should show participant selection with inline keyboard)
    sendWebhookRequest(admin, "/ban");
    String response = testBot.getLastResponse();
    InlineKeyboardMarkup keyboard = testBot.getLastInlineKeyboard();
    assertContains(response, "📋 <b>Participants:</b>\n[1-2/2]");
    assertNotNull(keyboard);
    verifyParticipantSelectionKeyboard(keyboard, 2);
    
    // Get player's button callback data
    InlineKeyboardRow playerRow = keyboard.getKeyboard().get(0);
    String playerCallbackData = playerRow.get(0).getCallbackData();
    assertTrue(playerCallbackData != null && playerCallbackData.startsWith("ps:select:"), "Callback data should start with 'ps:select:', got: " + playerCallbackData);

    // Step 2: Admin selects player from list (by pressing player's button)
    response = sendCallbackQueryAndGetResponse(admin, playerCallbackData, 1);
    assertContains(response,"is banned.");

    // Step 3: Player tries to use the bot and is banned.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response, "You are banned from the bot. Please contact the admin to unban.");
    response = sendWebhookRequestAndGetResponse(PLAYER, "/before");
    assertContains(response, "You are banned from the bot. Please contact the admin to unban.");

    // Step 4: Admin uses /unban command (should show participant selection with inline keyboard)
    sendWebhookRequest(admin, "/unban");
    response = testBot.getLastResponse();
    keyboard = testBot.getLastInlineKeyboard();
    assertContains(response, "📋 <b>Participants:</b>\n[1-2/2]");
    assertNotNull(keyboard);
    verifyParticipantSelectionKeyboard(keyboard, 2);
    
    // Get player's button callback data
    playerRow = keyboard.getKeyboard().get(0);
    playerCallbackData = playerRow.get(0).getCallbackData();
    assertTrue(playerCallbackData != null && playerCallbackData.startsWith("ps:select:"), "Callback data should start with 'ps:select:', got: " + playerCallbackData);

    // Step 5: Admin selects player from list (by pressing player's button)
    response = sendCallbackQueryAndGetResponse(admin, playerCallbackData, 1);
    assertContains(response,"is unbanned.");

    // Step 6: Player uses the bot and is unbanned.
    response = sendWebhookRequestAndGetResponse(PLAYER, "/last");
    assertContains(response,"Last journal:\n\n📅 ");
    assertContains(response, " 'Session 1':\n(BEFORE) Q1 - A1\n");
  }
}
