package com.losvernos.anzenfs.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {
  private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
  private static final String APP_NAME = "anzenfs";

  public static File getDataDir() {
    String xdgDataHome = System.getenv("XDG_DATA_HOME");
    if (xdgDataHome == null || xdgDataHome.isEmpty()) {
      xdgDataHome = System.getProperty("user.home") + File.separator + ".local" + File.separator + "share";
    }

    File appDataDir = new File(xdgDataHome, APP_NAME);
    if (!appDataDir.exists()) {
      appDataDir.mkdirs();
    }

    return appDataDir;
  }

  public static void deleteDirectory(Path path) {
    try (var stream = Files.walk(path)) {
      stream.sorted(java.util.Comparator.reverseOrder()) // Delete files, then subfolders, then root
          .map(Path::toFile)
          .forEach(java.io.File::delete);
    } catch (IOException e) {
      // Don't crash the whole app if a temp file is stuck
      log.warn("Could not clean up staging directory: {}", e.getMessage());
    }
  }
}
