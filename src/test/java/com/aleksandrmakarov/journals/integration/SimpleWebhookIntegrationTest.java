package com.aleksandrmakarov.journals.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import com.aleksandrmakarov.journals.repository.UserRepository;
import java.time.LocalDateTime;
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

  @BeforeEach
  void setUp() {
    // Clean up all data
    journalRepository.deleteAll();
    questionRepository.deleteAll();
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    // Create coach user
    User coach =
        new User(null, 1001L, "coach_user", "Coach", "Smith", UserRole.COACH, LocalDateTime.now());
    userRepository.save(coach);

    // Create player user
    User player =
        new User(
            null, 2001L, "player_user", "Player", "Johnson", UserRole.PLAYER, LocalDateTime.now());
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

  @Test
  void testStartCommand() {
    Update startCommand = createUpdate(2001L, "new_user", "New", "User", "/start");
    ResponseEntity<String> response = sendWebhookRequest(startCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Welcome to Journals Bot"));
    assertTrue(body.contains("/help"));
  }

  @Test
  void testHelpCommand() {
    Update helpCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/help");
    ResponseEntity<String> response = sendWebhookRequest(helpCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Available commands"));
    assertTrue(body.contains("/before"));
    assertTrue(body.contains("/after"));
  }

  @Test
  void testBeforeCommand_NoActiveSession() {
    Update beforeCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    ResponseEntity<String> response = sendWebhookRequest(beforeCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("No active session found"));
  }

  @Test
  void testAfterCommand_NoActiveSession() {
    Update afterCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    ResponseEntity<String> response = sendWebhookRequest(afterCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("No active session found"));
  }

  @Test
  void testLast5Command_NoJournals() {
    Update last5Command = createUpdate(2001L, "player_user", "Player", "Johnson", "/last5");
    ResponseEntity<String> response = sendWebhookRequest(last5Command);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("No journals found"));
  }

  @Test
  void testLastCommand_NoJournals() {
    Update lastCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    ResponseEntity<String> response = sendWebhookRequest(lastCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("No journals found"));
  }

  @Test
  void testHistoryCommand_NoJournals() {
    Update historyCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "/history");
    ResponseEntity<String> response = sendWebhookRequest(historyCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("No journals found"));
  }

  @Test
  void testParticipantsCommand_NoParticipants() {
    // Test participants command - should show no players since we have no journals
    Update participantsCommand =
        createUpdate(1001L, "coach_user", "Coach", "Smith", "/participants");
    ResponseEntity<String> response = sendWebhookRequest(participantsCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Participants"));
    assertTrue(body.contains("Player Johnson"));
    assertTrue(body.contains("0 journals"));
  }

  @Test
  void testUnknownCommand() {
    Update unknownCommand = createUpdate(2001L, "player_user", "Player", "Johnson", "random text");
    ResponseEntity<String> response = sendWebhookRequest(unknownCommand);

    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Unknown command"));
  }

  @Test
  void testCompleteBeforeAfterSessionFlow() {
    // Step 1: Coach sets up questions
    Update coachQuestions = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    ResponseEntity<String> response = sendWebhookRequest(coachQuestions);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Please provide questions"));

    // Step 2: Coach provides questions
    Update coachSetsQuestions =
        createUpdate(
            1001L,
            "coach_user",
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
    assertTrue(body.contains("Questions updated successfully"));

    // Step 3: Player starts before questions
    Update playerBefore = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    response = sendWebhookRequest(playerBefore);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Let's start with the pre-session questions"));
    assertTrue(body.contains("What is your goal for this session?"));

    // Step 4: Player answers first before question
    Update playerAnswer1 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "I want to improve my technique");
    response = sendWebhookRequest(playerAnswer1);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Answer saved"));
    assertTrue(body.contains("How do you feel before starting?"));

    // Step 5: Player answers second before question
    Update playerAnswer2 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "I feel excited and ready");
    response = sendWebhookRequest(playerAnswer2);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Done for now, good luck with the session"));

    // Step 6: Player starts after questions
    Update playerAfter = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    response = sendWebhookRequest(playerAfter);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Let's start with the post-session questions"));
    assertTrue(body.contains("Did you achieve your goal?"));

    // Step 7: Player answers first after question
    Update playerAnswer3 =
        createUpdate(2001L, "player_user", "Player", "Johnson", "Yes, I improved significantly");
    response = sendWebhookRequest(playerAnswer3);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Answer saved"));
    assertTrue(body.contains("How do you feel after the session?"));

    // Step 8: Player answers second after question
    Update playerAnswer4 =
        createUpdate(
            2001L, "player_user", "Player", "Johnson", "I feel accomplished and motivated");
    response = sendWebhookRequest(playerAnswer4);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Done for now, good luck with the session"));

    // Step 9: Player checks their last journal
    Update playerLast = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    response = sendWebhookRequest(playerLast);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("I feel accomplished and motivated"));
  }

  @Test
  void testCoachQuestionsUpdateAndVerificationFlow() {
    // Step 1: Coach sets initial questions
    Update coachQuestions1 = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    ResponseEntity<String> response = sendWebhookRequest(coachQuestions1);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Please provide questions"));

    Update coachSetsInitial =
        createUpdate(
            1001L,
            "coach_user",
            "Coach",
            "Smith",
            "Before: What is your main focus today?\n" + "After: How did the session go?");
    response = sendWebhookRequest(coachSetsInitial);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Questions updated successfully"));

    // Step 2: Coach checks current questions
    Update coachCheckQuestions = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachCheckQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Current Questions"));
    assertTrue(body.contains("Before: What is your main focus today?"));
    assertTrue(body.contains("After: How did the session go?"));

    // Step 3: Coach updates questions
    Update coachUpdatesQuestions =
        createUpdate(
            1001L,
            "coach_user",
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
    assertTrue(body.contains("Questions updated successfully"));

    // Step 4: Coach verifies updated questions
    Update coachVerifyQuestions = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachVerifyQuestions);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Current Questions"));
    assertTrue(body.contains("Before: What is your main focus today?"));
    assertTrue(body.contains("Before: Any concerns before we start?"));
    assertTrue(body.contains("After: How did the session go?"));
    assertTrue(body.contains("After: What did you learn?"));

    // Step 5: Coach cancels update (empty string)
    Update coachCancel = createUpdate(1001L, "coach_user", "Coach", "Smith", "cancel");
    response = sendWebhookRequest(coachCancel);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    System.out.println("DEBUG: Response body: " + body);
    assertTrue(body.contains("Question update cancelled"));
  }

  @Test
  void testPlayerBeforeAfterWithMultipleQuestions() {
    // Setup: Coach creates session with multiple questions
    Update coachQuestions = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    ResponseEntity<String> response = sendWebhookRequest(coachQuestions);
    assertNotNull(response);

    Update coachSetsQuestions =
        createUpdate(
            1001L,
            "coach_user",
            "Coach",
            "Smith",
            "Before: What is your goal?\n"
                + "Before: How confident do you feel?\n"
                + "Before: Any concerns?\n"
                + "After: Did you achieve your goal?\n"
                + "After: How do you feel now?\n"
                + "After: What will you do differently next time?");
    response = sendWebhookRequest(coachSetsQuestions);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Questions updated successfully"));

    // Player completes before questions
    Update playerBefore = createUpdate(2001L, "player_user", "Player", "Johnson", "/before");
    response = sendWebhookRequest(playerBefore);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("What is your goal?"));

    // Answer all before questions
    String[] beforeAnswers = {
      "I want to improve my serve", "I feel confident", "No major concerns"
    };
    for (String answer : beforeAnswers) {
      Update playerAnswer = createUpdate(2001L, "player_user", "Player", "Johnson", answer);
      response = sendWebhookRequest(playerAnswer);
      assertNotNull(response);
      body = response.getBody();
      assertNotNull(body);
      assertTrue(body.contains("Answer saved") || body.contains("Done for now"));
    }

    // Player completes after questions
    Update playerAfter = createUpdate(2001L, "player_user", "Player", "Johnson", "/after");
    response = sendWebhookRequest(playerAfter);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Did you achieve your goal?"));

    // Answer all after questions
    String[] afterAnswers = {
      "Yes, I improved my serve", "I feel great", "I'll practice more consistently"
    };
    for (String answer : afterAnswers) {
      Update playerAnswer = createUpdate(2001L, "player_user", "Player", "Johnson", answer);
      response = sendWebhookRequest(playerAnswer);
      assertNotNull(response);
      body = response.getBody();
      assertNotNull(body);
      assertTrue(body.contains("Answer saved") || body.contains("Done for now"));
    }

    // Verify player can see their journals
    Update playerLast = createUpdate(2001L, "player_user", "Player", "Johnson", "/last");
    response = sendWebhookRequest(playerLast);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("I'll practice more consistently"));
  }

  @Test
  void testCoachQuestionsTemplateAndCancellation() {
    // Step 1: Coach sets questions first time
    Update coachQuestions = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    ResponseEntity<String> response = sendWebhookRequest(coachQuestions);
    assertNotNull(response);

    Update coachSetsQuestions =
        createUpdate(
            1001L,
            "coach_user",
            "Coach",
            "Smith",
            "Before: What is your goal?\n" + "After: How did it go?");
    response = sendWebhookRequest(coachSetsQuestions);
    assertNotNull(response);
    String body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Questions updated successfully"));

    // Step 2: Coach views questions as template
    Update coachViewTemplate = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachViewTemplate);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Current Questions"));
    assertTrue(body.contains("Before: What is your goal?"));
    assertTrue(body.contains("After: How did it go?"));
    assertTrue(body.contains("Send new questions to update, or send empty message to cancel"));

    // Step 3: Coach cancels by sending empty message
    Update coachCancel = createUpdate(1001L, "coach_user", "Coach", "Smith", "cancel");
    response = sendWebhookRequest(coachCancel);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Question update cancelled"));

    // Step 4: Verify questions are unchanged
    Update coachVerify = createUpdate(1001L, "coach_user", "Coach", "Smith", "/questions");
    response = sendWebhookRequest(coachVerify);
    assertNotNull(response);
    body = response.getBody();
    assertNotNull(body);
    assertTrue(body.contains("Before: What is your goal?"));
    assertTrue(body.contains("After: How did it go?"));
  }
}
