package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import java.util.List;

/** User service interface. */
public interface UserService {

  User findOrCreateUser(Long telegramId, String username, String firstName, String lastName);

  User findUserByTelegramId(Long telegramId);

  User findUserByUsername(String username);

  List<User> getAdmins();

  List<Participant> getParticipantsOrderedByLastJournal();

  void promoteToAdmin(User user);

  User findOrCreateUserWithRole(
      Long telegramId, String username, String firstName, String lastName, UserRole role);

  void setQuestionFlowState(Long userId, Long sessionId, int questionIndex);

  void setQuestionsUpdateMode(Long userId, Long sessionId);

  void clearUserState(Long userId, boolean isClearQuestionIndex);
}
