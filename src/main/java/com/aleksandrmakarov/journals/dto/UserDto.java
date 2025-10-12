package com.aleksandrmakarov.journals.dto;

import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import java.time.LocalDateTime;

public record UserDto(
    Long id,
    Long telegramId,
    String username,
    String firstName,
    String lastName,
    UserRole role,
    LocalDateTime createdAt,
    String displayName) {
  public static UserDto from(User user) {
    return new UserDto(
        user.id(),
        user.telegramId(),
        user.username(),
        user.firstName(),
        user.lastName(),
        user.role(),
        user.createdAt(),
        user.getDisplayName());
  }
}
