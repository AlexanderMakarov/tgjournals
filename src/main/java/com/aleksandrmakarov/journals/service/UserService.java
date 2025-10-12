package com.aleksandrmakarov.journals.service;

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
      // Update user info if changed
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
                user.createdAt());
        return userRepository.save(updatedUser);
      }
      return user;
    } else {
      // Create new user as player by default
      User newUser =
          new User(
              null,
              telegramId,
              username,
              firstName,
              lastName,
              UserRole.PLAYER,
              LocalDateTime.now());
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
              UserRole.COACH,
              user.createdAt());
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
                user.createdAt());
        return userRepository.save(updatedUser);
      }
      return user;
    } else {
      // Create new user with specified role
      User newUser =
          new User(null, telegramId, username, firstName, lastName, role, LocalDateTime.now());
      return userRepository.save(newUser);
    }
  }
}
