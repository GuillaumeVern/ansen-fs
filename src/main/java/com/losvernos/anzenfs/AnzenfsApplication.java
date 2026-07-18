package com.losvernos.anzenfs;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.io.File;
import java.io.IOException;

@ImportRuntimeHints(AnzenfsApplication.WebResourcesHints.class)
@SpringBootApplication
public class AnzenfsApplication {

  private static final String APP_NAME = "anzenfs";
  private static final String DB_NAME = "anzenfs.db";

  static {
    System.setProperty("custom.db.path", getDBFilePath());
  }

  public static void main(String[] args) {
    SpringApplication.run(AnzenfsApplication.class, args);
  }

  private static String getDBFilePath() {
    String xdgDataHome = System.getenv("XDG_DATA_HOME");
    if (xdgDataHome == null || xdgDataHome.isEmpty()) {
      xdgDataHome = System.getProperty("user.home") + File.separator + ".local" + File.separator + "share";
    }

    File appDataDir = new File(xdgDataHome, APP_NAME);
    if (!appDataDir.exists()) {
      appDataDir.mkdirs();
    }
    File dbFile = new File(appDataDir, DB_NAME);
    if (!dbFile.exists()) {
      try {
        dbFile.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return dbFile.getAbsolutePath();
  }

  static class WebResourcesHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints.resources().registerPattern("static/browser/**");
    }
  }

}
