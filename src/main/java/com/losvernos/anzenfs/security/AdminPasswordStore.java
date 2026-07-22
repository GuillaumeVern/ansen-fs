package com.losvernos.anzenfs.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Generates the built-in admin account's initial password once, on first startup, and persists
 * it next to the JWT secret (owner-only permissions, never committed to version control) - see
 * JwtSecretStore, whose pattern this mirrors, rather than shipping a guessable hardcoded default.
 */
public class AdminPasswordStore {

  private static final String PASSWORD_FILE_NAME = "admin.initial-password";
  private static final int PASSWORD_LENGTH_BYTES = 18;

  public static String loadOrGenerate(File dataDir) {
    Path passwordPath = dataDir.toPath().resolve(PASSWORD_FILE_NAME);

    try {
      if (Files.exists(passwordPath)) {
        return Files.readString(passwordPath).strip();
      }

      String password = generatePassword();
      writePasswordFile(passwordPath, password);
      return password;
    } catch (IOException e) {
      throw new IllegalStateException("Could not load or generate the initial admin password at " + passwordPath, e);
    }
  }

  private static String generatePassword() {
    byte[] randomBytes = new byte[PASSWORD_LENGTH_BYTES];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.getEncoder().withoutPadding().encodeToString(randomBytes);
  }

  private static void writePasswordFile(Path passwordPath, String password) throws IOException {
    Files.writeString(passwordPath, password);
    try {
      Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(passwordPath, ownerOnly);
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX filesystem (e.g. Windows dev machine) - best effort only.
    }
  }
}
