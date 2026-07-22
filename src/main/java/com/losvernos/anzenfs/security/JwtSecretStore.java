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

public class JwtSecretStore {

  private static final String SECRET_FILE_NAME = "jwt.secret";
  private static final int SECRET_LENGTH_BYTES = 64;

  public static String loadOrGenerate(File dataDir) {
    Path secretPath = dataDir.toPath().resolve(SECRET_FILE_NAME);

    try {
      if (Files.exists(secretPath)) {
        return Files.readString(secretPath).strip();
      }

      String secret = generateSecret();
      writeSecretFile(secretPath, secret);
      return secret;
    } catch (IOException e) {
      throw new IllegalStateException("Could not load or generate the JWT signing secret at " + secretPath, e);
    }
  }

  private static String generateSecret() {
    byte[] randomBytes = new byte[SECRET_LENGTH_BYTES];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.getEncoder().withoutPadding().encodeToString(randomBytes);
  }

  private static void writeSecretFile(Path secretPath, String secret) throws IOException {
    Files.writeString(secretPath, secret);
    try {
      Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(secretPath, ownerOnly);
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX filesystem (e.g. Windows dev machine) - best effort only.
    }
  }
}
