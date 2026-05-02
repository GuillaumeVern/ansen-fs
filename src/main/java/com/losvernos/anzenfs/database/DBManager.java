package com.losvernos.anzenfs.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBManager {

  private static final String DB_NAME = "anzen.db";

  private static final String APP_NAME = "anzenfs";

  private static DBManager instance;

  private Connection connection;

  private final String url;

  private DBManager() {
    this.url = getStringUrl();
    try {
      this.connection = DriverManager.getConnection(url);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static synchronized DBManager getInstance() {
    if (instance == null) {
      instance = new DBManager();
    }
    return instance;
  }

  public Connection getConnection() {
    return connection;
  }

  public void closeConnection() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private String getStringUrl() {
    var url = "jdbc:sqlite:" + getDBFile();
    return url;
  }

  private String getDBFile() {
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
}
