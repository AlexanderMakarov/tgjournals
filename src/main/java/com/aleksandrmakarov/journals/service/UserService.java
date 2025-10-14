package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import com.aleksandrmakarov.journals.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  @Autowired private UserRepository userRepository;

  public User findOrCreateUser(
      Long telegramId, String username, String firstName, String lastName) {
    Optional<User> existingUser = userRepository.findByTelegramId(telegramId);
    if (existingUser.isPresent()) {
      User user = existingUser.get();
      // Update user info if changed.
      if (!username.equals(user.username())
          || !firstName.equals(user.firstName())
          || !lastName.equals(user.lastName())) {
        User updatedUser =
            new User(
                user.id(),
                user.telegramId(),
                username,
                firstName,
                lastName,
                user.role(),
                user.createdAt(),
                user.stateType(),
                user.stateSessionId(),
                user.stateQuestionIndex(),
                user.stateUpdatedAt());
        return userRepository.save(updatedUser);
      }
      return user;
    } else {
      // Create new user as a player.
      User newUser =
          new User(
              null,
              telegramId,
              username,
              firstName,
              lastName,
              UserRole.PLAYER,
              LocalDateTime.now(),
              null,
              null,
              null,
              null);
      return userRepository.save(newUser);
    }
  }

  public User findUserByTelegramId(Long telegramId) {
    return userRepository.findByTelegramId(telegramId).orElse(null);
  }

  public List<User> getPlayersOrderedByLastJournal() {
    return userRepository.findPlayersOrderedByLastJournal();
  }

  public void promoteToCoach(Long telegramId) {
    User user = findUserByTelegramId(telegramId);
    if (user != null) {
      User updatedUser =
          new User(
              user.id(),
              user.telegramId(),
              user.username(),
              user.firstName(),
              user.lastName(),
              UserRole.ADMIN,
              user.createdAt(),
              user.stateType(),
              user.stateSessionId(),
              user.stateQuestionIndex(),
              user.stateUpdatedAt());
      userRepository.save(updatedUser);
    }
  }

  public User findOrCreateUserWithRole(
      Long telegramId, String username, String firstName, String lastName, UserRole role) {
    Optional<User> existingUser = userRepository.findByTelegramId(telegramId);
    if (existingUser.isPresent()) {
      User user = existingUser.get();
      // Update user info and role if changed
      if (!username.equals(user.username())
          || !firstName.equals(user.firstName())
          || !lastName.equals(user.lastName())
          || !role.equals(user.role())) {
        User updatedUser =
            new User(
                user.id(),
                user.telegramId(),
                username,
                firstName,
                lastName,
                role,
                user.createdAt(),
                user.stateType(),
                user.stateSessionId(),
                user.stateQuestionIndex(),
                user.stateUpdatedAt());
        return userRepository.save(updatedUser);
      }
      return user;
    } else {
      // Create new user with specified role
      User newUser =
          new User(
              null,
              telegramId,
              username,
              firstName,
              lastName,
              role,
              LocalDateTime.now(),
              null,
              null,
              null,
              null);
      return userRepository.save(newUser);
    }
  }

  // --------------- User state operations moved here ---------------

  public void setQuestionFlowState(Long userId, Long sessionId, int questionIndex) {
    userRepository.upsertState(userId, StateType.QA_FLOW, sessionId, questionIndex);
  }

  public void clearQuestionFlowState(Long userId) {
    userRepository.clearState(userId);
  }

  public void setQuestionUpdateMode(Long userId, Long sessionId) {
    userRepository.upsertState(userId, StateType.QUESTION_UPDATE, sessionId, null);
  }

  public void clearQuestionUpdateMode(Long userId) {
    userRepository.clearState(userId);
  }

  // No read method needed here; the `User` entity carries state fields when loaded
}
