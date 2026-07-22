package com.losvernos.anzenfs.rbac.user;

public final class PasswordPolicy {

  private static final int MIN_LENGTH = 8;

  private PasswordPolicy() {
  }

  public static void validate(String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long.");
    }
    if (password.chars().noneMatch(Character::isLetter)) {
      throw new IllegalArgumentException("Password must contain at least one letter.");
    }
    if (password.chars().noneMatch(Character::isDigit)) {
      throw new IllegalArgumentException("Password must contain at least one digit.");
    }
  }
}
