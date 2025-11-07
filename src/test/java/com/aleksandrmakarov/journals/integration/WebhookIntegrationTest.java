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
import com.fasterxml.jackson.databind.ObjectMapper;

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
  @Autowired private ObjectMapper objectMapper;

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

  @Test
  void test_jsonDeserialization_triggersBuilderMethods() {
    // This test sends raw JSON payloads matching the actual format Telegram sends in webhook updates
    // to ensure Jackson deserialization uses builder methods, which the native-image agent needs to capture.
    // This covers issues met in native image run so far:
    // - User$UserBuilder.userName(String) - via "username" field in JSON
    // - Chat$ChatBuilder.firstName(String) - via "first_name" field in JSON
    //
    // The payloads match the format received in WebhookController, using camelCase "username" field.
    
    int timestamp = (int) (System.currentTimeMillis() / 1000);
    long playerId = PLAYER.telegramId();
    
    // Test payload 1: CallbackQuery with username in both from and chat
    // This matches the actual format Telegram sends for callback queries
    String jsonPayload1 = """
        {
          "update_id": 1,
          "callback_query": {
            "id": "7103611918865400490",
            "from": {
              "id": %d,
              "first_name": "Test",
              "is_bot": false,
              "last_name": "User",
              "username": "testuser",
              "language_code": "en"
            },
            "message": {
              "message_id": 619,
              "from": {
                "id": 8385797027,
                "first_name": "amjournals",
                "is_bot": true,
                "username": "amjournalsbot"
              },
              "date": %d,
              "chat": {
                "id": %d,
                "type": "private",
                "first_name": "TestChat",
                "last_name": "ChatLastName",
                "username": "testchat"
              },
              "text": "Test message",
              "reply_markup": {
                "inline_keyboard": [[{
                  "text": "Test Button",
                  "callback_data": "test:data"
                }], [{
                  "text": "Cancel",
                  "callback_data": "ps:cancel"
                }]]
              }
            },
            "data": "ps:cancel",
            "chat_instance": "1351648298235224897"
          }
        }
        """.formatted(playerId, timestamp, playerId);

    // Test payload 2: Message with username in both from and chat, with entities
    String jsonPayload2 = """
        {
          "update_id": 2,
          "message": {
            "message_id": 620,
            "from": {
              "id": %d,
              "first_name": "Test",
              "is_bot": false,
              "last_name": "User",
              "username": "testuser",
              "language_code": "en"
            },
            "date": %d,
            "chat": {
              "id": %d,
              "type": "private",
              "first_name": "TestChat",
              "last_name": "ChatLastName",
              "username": "testchat"
            },
            "text": "/last",
            "entities": [{
              "type": "bot_command",
              "offset": 0,
              "length": 5
            }]
          }
        }
        """.formatted(playerId, timestamp, playerId);

    // Test payload 3: Another CallbackQuery with username in both from and chat
    String jsonPayload3 = """
        {
          "update_id": 3,
          "callback_query": {
            "id": "7103611920246976766",
            "from": {
              "id": %d,
              "first_name": "Test",
              "is_bot": false,
              "last_name": "User",
              "username": "testuser",
              "language_code": "en"
            },
            "message": {
              "message_id": 621,
              "from": {
                "id": 8385797027,
                "first_name": "amjournals",
                "is_bot": true,
                "username": "amjournalsbot"
              },
              "date": %d,
              "chat": {
                "id": %d,
                "type": "private",
                "first_name": "TestChat",
                "last_name": "ChatLastName",
                "username": "testchat"
              },
              "text": "Test message",
              "reply_markup": {
                "inline_keyboard": [[{
                  "text": "Test Button",
                  "callback_data": "test:data"
                }], [{
                  "text": "Cancel",
                  "callback_data": "ps:cancel"
                }]]
              }
            },
            "data": "ps:select:1",
            "chat_instance": "1351648298235224897"
          }
        }
        """.formatted(playerId, timestamp, playerId);

    // Test payload 4: Message with username in both from and chat, with entities
    String jsonPayload4 = """
        {
          "update_id": 4,
          "message": {
            "message_id": 623,
            "from": {
              "id": %d,
              "first_name": "Test",
              "is_bot": false,
              "last_name": "User",
              "username": "testuser",
              "language_code": "en"
            },
            "date": %d,
            "chat": {
              "id": %d,
              "type": "private",
              "first_name": "TestChat",
              "last_name": "ChatLastName",
              "username": "testchat"
            },
            "text": "/start",
            "entities": [{
              "type": "bot_command",
              "offset": 0,
              "length": 6
            }]
          }
        }
        """.formatted(playerId, timestamp, playerId);

    // First, directly deserialize JSON payloads using ObjectMapper to ensure builder methods are called
    // This helps the agent capture builder method calls that might not be triggered via webhook
    try {
      // Deserialize payload 1 (CallbackQuery) to trigger userName() builder in callback_query.from
      Update update1 = objectMapper.readValue(jsonPayload1, Update.class);
      assertNotNull(update1, "Update1 should not be null");
      assertNotNull(update1.getCallbackQuery(), "CallbackQuery1 should not be null");
      assertNotNull(update1.getCallbackQuery().getFrom(), "From User1 should not be null");
      assertEquals("testuser", update1.getCallbackQuery().getFrom().getUserName(), "Username1 should match");
      
      // Deserialize payload 2 (Message) to trigger userName() builder in message.from
      Update update2 = objectMapper.readValue(jsonPayload2, Update.class);
      assertNotNull(update2, "Update2 should not be null");
      assertNotNull(update2.getMessage(), "Message2 should not be null");
      assertNotNull(update2.getMessage().getFrom(), "From User2 should not be null");
      assertEquals("testuser", update2.getMessage().getFrom().getUserName(), "Username2 should match");
      
      // Deserialize payload 4 (Message) to trigger userName() builder
      Update update4 = objectMapper.readValue(jsonPayload4, Update.class);
      assertNotNull(update4, "Update4 should not be null");
      assertNotNull(update4.getMessage(), "Message4 should not be null");
      assertNotNull(update4.getMessage().getFrom(), "From User4 should not be null");
      assertEquals("testuser", update4.getMessage().getFrom().getUserName(), "Username4 should match");
    } catch (Exception e) {
      throw new AssertionError("Failed to deserialize JSON payloads: " + e.getMessage(), e);
    }

    // Send all payloads through the webhook endpoint to trigger builder methods via Spring's ObjectMapper
    // This ensures the agent captures builder method calls during actual webhook processing
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    // Send payload 1: CallbackQuery with username in both from and chat
    HttpEntity<String> request1 = new HttpEntity<>(jsonPayload1, headers);
    ResponseEntity<String> response1 = restTemplate.exchange("/webhook", HttpMethod.POST, request1, String.class);
    assertEquals("OK", response1.getBody());
    
    // Send payload 2: Message with username in both from and chat
    HttpEntity<String> request2 = new HttpEntity<>(jsonPayload2, headers);
    ResponseEntity<String> response2 = restTemplate.exchange("/webhook", HttpMethod.POST, request2, String.class);
    assertEquals("OK", response2.getBody());
    
    // Send payload 3: Another CallbackQuery with username in both from and chat
    HttpEntity<String> request3 = new HttpEntity<>(jsonPayload3, headers);
    ResponseEntity<String> response3 = restTemplate.exchange("/webhook", HttpMethod.POST, request3, String.class);
    assertEquals("OK", response3.getBody());
    
    // Send payload 4: Message with username in both from and chat to ensure userName() builder is triggered
    HttpEntity<String> request4 = new HttpEntity<>(jsonPayload4, headers);
    ResponseEntity<String> response4 = restTemplate.exchange("/webhook", HttpMethod.POST, request4, String.class);
    assertEquals("OK", response4.getBody());
    String botResponse4 = testBot.getLastResponse();
    assertContains(
        botResponse4,
        "Welcome to AM Journals Bot. Use /before and /after to answer questions before and after the session. Use /admins to see list of admins.");
  }

  @Test
  void test_apiResponseDeserialization_triggersReflectionHints() {
    // This test deserializes ApiResponse JSON to ensure the native-image agent captures
    // reflection hints for org.telegram.telegrambots.meta.api.objects.ApiResponse.
    // This covers the issue where sending messages to Telegram fails with:
    // "Cannot construct instance of `org.telegram.telegrambots.meta.api.objects.ApiResponse`"
    // and "Cannot reflectively read or write field 'errorCode'"
    //
    // The ApiResponse is the wrapper that Telegram API returns when sending messages.
    // It contains the actual response object (e.g., Message), an "ok" boolean, and optionally "errorCode".
    
    // Sample ApiResponse JSON that Telegram returns when sending messages (success cases)
    // These match the actual format returned by Telegram Bot API
    String apiResponseJsonSuccess1 = """
        {
          "ok": true,
          "result": {
            "message_id": 123,
            "from": {
              "id": 8385797027,
              "is_bot": true,
              "first_name": "amjournals",
              "username": "amjournalsbot"
            },
            "chat": {
              "id": 1653938535,
              "type": "private",
              "first_name": "Alexander",
              "last_name": "Makarov",
              "username": "username"
            },
            "date": 1762432512,
            "text": "📋 <b>Admins:</b>some"
          }
        }
        """;
    
    String apiResponseJsonSuccess2 = """
        {
          "ok": true,
          "result": {
            "message_id": 124,
            "from": {
              "id": 8385797027,
              "is_bot": true,
              "first_name": "amjournals",
              "username": "amjournalsbot"
            },
            "chat": {
              "id": 1653938535,
              "type": "private",
              "first_name": "Alexander",
              "last_name": "Makarov",
              "username": "username"
            },
            "date": 1762432513,
            "text": "Last journal\\n[1-1/1]",
            "reply_markup": {
              "inline_keyboard": [[{
                "text": "some - 2",
                "callback_data": "ps:select:1"
              }], [{
                "text": "Cancel",
                "callback_data": "ps:cancel"
              }]]
            }
          }
        }
        """;
    
    String apiResponseJsonSuccess3 = """
        {
          "ok": true,
          "result": true
        }
        """;
    
    String apiResponseJsonSuccess4 = """
        {
          "ok": true,
          "result": {
            "message_id": 125,
            "from": {
              "id": 8385797027,
              "is_bot": true,
              "first_name": "amjournals",
              "username": "amjournalsbot"
            },
            "chat": {
              "id": 1653938535,
              "type": "private",
              "first_name": "Alexander",
              "last_name": "Makarov",
              "username": "username"
            },
            "date": 1762432514,
            "text": "Last journal:\\n\\n📅 2025-11-05 10:58:11 'test 2':\\n(BEFORE) Modified Before q - 21 answer\\n"
          }
        }
        """;
    
    // Sample ApiResponse JSON with errorCode field (error case)
    // This ensures the errorCode field is also captured by the native agent
    // This matches the actual error format that Telegram returns
    String apiResponseJsonError = """
        {
          "ok": false,
          "error_code": 400,
          "description": "Bad Request: message text is empty"
        }
        """;
    
    // Deserialize ApiResponse using ObjectMapper to trigger reflection hints
    // This ensures the native agent captures the constructor/deserializer and fields for ApiResponse
    try {
      // Deserialize success response 1: Simple message
      var typeRefMessage = new com.fasterxml.jackson.core.type.TypeReference<org.telegram.telegrambots.meta.api.objects.ApiResponse<org.telegram.telegrambots.meta.api.objects.message.Message>>() {};
      org.telegram.telegrambots.meta.api.objects.ApiResponse<org.telegram.telegrambots.meta.api.objects.message.Message> apiResponseSuccess1 = 
          objectMapper.readValue(apiResponseJsonSuccess1, typeRefMessage);
      
      assertNotNull(apiResponseSuccess1, "ApiResponse success1 should not be null");
      assertTrue(apiResponseSuccess1.getOk(), "ApiResponse1 should be ok");
      assertNotNull(apiResponseSuccess1.getResult(), "ApiResponse1 result should not be null");
      assertEquals(123, apiResponseSuccess1.getResult().getMessageId(), "Message ID1 should match");
      assertTrue(apiResponseSuccess1.getResult().getText().contains("Admins"), "Message text1 should contain expected content");
      
      // Deserialize success response 2: Message with inline keyboard
      org.telegram.telegrambots.meta.api.objects.ApiResponse<org.telegram.telegrambots.meta.api.objects.message.Message> apiResponseSuccess2 = 
          objectMapper.readValue(apiResponseJsonSuccess2, typeRefMessage);
      
      assertNotNull(apiResponseSuccess2, "ApiResponse success2 should not be null");
      assertTrue(apiResponseSuccess2.getOk(), "ApiResponse2 should be ok");
      assertNotNull(apiResponseSuccess2.getResult(), "ApiResponse2 result should not be null");
      assertEquals(124, apiResponseSuccess2.getResult().getMessageId(), "Message ID2 should match");
      assertNotNull(apiResponseSuccess2.getResult().getReplyMarkup(), "Reply markup should not be null");
      
      // Deserialize success response 3: Boolean result (for answerCallbackQuery)
      var typeRefBoolean = new com.fasterxml.jackson.core.type.TypeReference<org.telegram.telegrambots.meta.api.objects.ApiResponse<Boolean>>() {};
      org.telegram.telegrambots.meta.api.objects.ApiResponse<Boolean> apiResponseSuccess3 = 
          objectMapper.readValue(apiResponseJsonSuccess3, typeRefBoolean);
      
      assertNotNull(apiResponseSuccess3, "ApiResponse success3 should not be null");
      assertTrue(apiResponseSuccess3.getOk(), "ApiResponse3 should be ok");
      assertNotNull(apiResponseSuccess3.getResult(), "ApiResponse3 result should not be null");
      assertTrue(apiResponseSuccess3.getResult(), "Result3 should be true");
      
      // Deserialize success response 4: Another message
      org.telegram.telegrambots.meta.api.objects.ApiResponse<org.telegram.telegrambots.meta.api.objects.message.Message> apiResponseSuccess4 = 
          objectMapper.readValue(apiResponseJsonSuccess4, typeRefMessage);
      
      assertNotNull(apiResponseSuccess4, "ApiResponse success4 should not be null");
      assertTrue(apiResponseSuccess4.getOk(), "ApiResponse4 should be ok");
      assertNotNull(apiResponseSuccess4.getResult(), "ApiResponse4 result should not be null");
      assertEquals(125, apiResponseSuccess4.getResult().getMessageId(), "Message ID4 should match");
      
      // Deserialize error response to trigger errorCode field access
      org.telegram.telegrambots.meta.api.objects.ApiResponse<org.telegram.telegrambots.meta.api.objects.message.Message> apiResponseError = 
          objectMapper.readValue(apiResponseJsonError, typeRefMessage);
      
      assertNotNull(apiResponseError, "ApiResponse error should not be null");
      assertTrue(!apiResponseError.getOk(), "ApiResponse should not be ok");
      // Access errorCode field to ensure reflection hints are captured
      Integer errorCode = apiResponseError.getErrorCode();
      assertNotNull(errorCode, "ErrorCode should not be null");
      assertEquals(400, errorCode.intValue(), "Error code should match");
      
      // Simulate what the library does when it encounters an error response:
      // It throws a TelegramApiRequestException with the error details.
      // This ensures reflection hints are captured for the exception constructor.
      // The library constructs the exception with format: "Error executing {method} query: [{errorCode}] {description}"
      if (!apiResponseError.getOk() && apiResponseError.getErrorCode() != null) {
        // Construct TelegramApiRequestException to trigger reflection hints for exception class
        // This matches what PartialBotApiMethod.deserializeResponseInternal does at line 59
        // The exception is constructed with a message that includes the full method name and error details
        String methodName = "org.telegram.telegrambots.meta.api.methods.send.SendMessage";
        String errorMessage = "Error executing " + methodName + " query: [" + apiResponseError.getErrorCode() + "] Bad Request: message text is empty";
        org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException exception = 
            new org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException(errorMessage);
        assertNotNull(exception, "TelegramApiRequestException should be constructed");
        // Access the exception's message to ensure reflection hints are captured
        assertNotNull(exception.getMessage(), "Exception message should not be null");
        assertTrue(exception.getMessage().contains("400"), "Exception message should contain error code");
        assertTrue(exception.getMessage().contains("Bad Request: message text is empty"), 
            "Exception message should contain error description");
      }
    } catch (Exception e) {
      throw new AssertionError("Failed to deserialize ApiResponse: " + e.getMessage(), e);
    }
  }

  @Test
  void test_sendMessageSerialization_triggersReflectionHints() {
    // This test serializes SendMessage objects to ensure the native-image agent captures
    // reflection hints for serializing SendMessage when sending messages to Telegram.
    // This covers the issue where sending messages to Telegram fails with:
    // "Bad Request: message text is empty" because the text field is not serialized.
    //
    // The SendMessage object needs to be serialized to JSON when sending to Telegram API.
    // The native image needs reflection hints to access fields like text, chatId, parseMode, replyMarkup.
    
    try {
      // Create SendMessage objects matching what the application creates
      // This ensures all fields used by the application are captured
      
      // SendMessage 1: Simple message with text and chatId (most common case)
      org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage1 = 
          org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
              .chatId("1653938535")
              .text("📋 <b>Admins:</b>\n👤 Alexander Makarov (@i4ellendger)")
              .parseMode("HTML")
              .build();
      
      // Serialize SendMessage1 to JSON to trigger reflection hints for serialization
      String json1 = objectMapper.writeValueAsString(sendMessage1);
      assertNotNull(json1, "Serialized JSON1 should not be null");
      assertTrue(json1.contains("1653938535"), "JSON1 should contain chatId");
      assertTrue(json1.contains("Admins"), "JSON1 should contain message text");
      assertTrue(json1.contains("HTML"), "JSON1 should contain parseMode");
      
      // SendMessage 2: Message with inline keyboard (replyMarkup)
      org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
          org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
              .text("Button 1")
              .callbackData("callback1")
              .build();
      org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow row = 
          new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
              java.util.List.of(button));
      org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup inlineKeyboard = 
          org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder()
              .keyboard(java.util.List.of(row))
              .build();
      
      org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage2 = 
          org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
              .chatId("1653938535")
              .text("Test message with keyboard")
              .parseMode("HTML")
              .replyMarkup(inlineKeyboard)
              .build();
      
      // Serialize SendMessage2 to JSON to trigger reflection hints for replyMarkup serialization
      String json2 = objectMapper.writeValueAsString(sendMessage2);
      assertNotNull(json2, "Serialized JSON2 should not be null");
      assertTrue(json2.contains("1653938535"), "JSON2 should contain chatId");
      assertTrue(json2.contains("Test message"), "JSON2 should contain message text");
      assertTrue(json2.contains("reply_markup") || json2.contains("replyMarkup"), 
          "JSON2 should contain replyMarkup");
      
      // SendMessage 3: Message without parseMode (default case)
      org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage3 = 
          org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
              .chatId("123456789")
              .text("Simple message without HTML")
              .build();
      
      // Serialize SendMessage3 to JSON
      String json3 = objectMapper.writeValueAsString(sendMessage3);
      assertNotNull(json3, "Serialized JSON3 should not be null");
      assertTrue(json3.contains("123456789"), "JSON3 should contain chatId");
      assertTrue(json3.contains("Simple message"), "JSON3 should contain message text");
      
      // SendMessage 4: Message with special characters and HTML entities
      org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage4 = 
          org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
              .chatId("1653938535")
              .text("Last journal:\n\n📅 2025-11-05 10:58:11 'test 2':\n(BEFORE) Modified Before q - 21 answer\n")
              .parseMode("HTML")
              .build();
      
      // Serialize SendMessage4 to JSON
      String json4 = objectMapper.writeValueAsString(sendMessage4);
      assertNotNull(json4, "Serialized JSON4 should not be null");
      assertTrue(json4.contains("1653938535"), "JSON4 should contain chatId");
      assertTrue(json4.contains("Last journal"), "JSON4 should contain message text");
      
      // Verify that all SendMessage objects have the text field accessible
      // This ensures reflection hints are captured for the text field getter
      assertNotNull(sendMessage1.getText(), "SendMessage1 text should not be null");
      assertNotNull(sendMessage2.getText(), "SendMessage2 text should not be null");
      assertNotNull(sendMessage3.getText(), "SendMessage3 text should not be null");
      assertNotNull(sendMessage4.getText(), "SendMessage4 text should not be null");
      
      // Verify chatId is accessible
      assertNotNull(sendMessage1.getChatId(), "SendMessage1 chatId should not be null");
      assertNotNull(sendMessage2.getChatId(), "SendMessage2 chatId should not be null");
      assertNotNull(sendMessage3.getChatId(), "SendMessage3 chatId should not be null");
      assertNotNull(sendMessage4.getChatId(), "SendMessage4 chatId should not be null");
      
      // Also test AnswerCallbackQuery serialization since it's used in the application
      // AnswerCallbackQuery 1: Simple answer without text
      org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallbackQuery1 = 
          org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
              .callbackQueryId("query123")
              .build();
      
      // Serialize AnswerCallbackQuery1 to JSON
      String answerJson1 = objectMapper.writeValueAsString(answerCallbackQuery1);
      assertNotNull(answerJson1, "Serialized AnswerCallbackQuery JSON1 should not be null");
      assertTrue(answerJson1.contains("query123"), "AnswerCallbackQuery JSON1 should contain callbackQueryId");
      
      // AnswerCallbackQuery 2: Answer with text and showAlert
      org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallbackQuery2 = 
          org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
              .callbackQueryId("query456")
              .text("Error message")
              .showAlert(true)
              .build();
      
      // Serialize AnswerCallbackQuery2 to JSON
      String answerJson2 = objectMapper.writeValueAsString(answerCallbackQuery2);
      assertNotNull(answerJson2, "Serialized AnswerCallbackQuery JSON2 should not be null");
      assertTrue(answerJson2.contains("query456"), "AnswerCallbackQuery JSON2 should contain callbackQueryId");
      assertTrue(answerJson2.contains("Error message"), "AnswerCallbackQuery JSON2 should contain text");
      
      // Verify that AnswerCallbackQuery fields are accessible
      assertNotNull(answerCallbackQuery1.getCallbackQueryId(), "AnswerCallbackQuery1 callbackQueryId should not be null");
      assertNotNull(answerCallbackQuery2.getCallbackQueryId(), "AnswerCallbackQuery2 callbackQueryId should not be null");
      assertNotNull(answerCallbackQuery2.getText(), "AnswerCallbackQuery2 text should not be null");
      
    } catch (Exception e) {
      throw new AssertionError("Failed to serialize SendMessage/AnswerCallbackQuery: " + e.getMessage(), e);
    }
  }
}
