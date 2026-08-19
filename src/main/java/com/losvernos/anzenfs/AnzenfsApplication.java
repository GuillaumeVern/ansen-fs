package com.losvernos.anzenfs;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.io.File;
import java.io.IOException;

import com.losvernos.anzenfs.files.FileNode;
import com.losvernos.anzenfs.files.ResourceAndName;
import com.losvernos.anzenfs.jobs.UploadJobCreationRequest;
import com.losvernos.anzenfs.jobs.UploadJobSummary;
import com.losvernos.anzenfs.rbac.auth.AuthResponse;
import com.losvernos.anzenfs.rbac.auth.LoginRequest;
import com.losvernos.anzenfs.rbac.permission.CreatePermissionRequest;
import com.losvernos.anzenfs.rbac.permission.PermissionSummary;
import com.losvernos.anzenfs.rbac.role.CreateRoleRequest;
import com.losvernos.anzenfs.rbac.role.RoleSummary;
import com.losvernos.anzenfs.rbac.role.UpdateRoleRequest;
import com.losvernos.anzenfs.rbac.user.CreateUserRequest;
import com.losvernos.anzenfs.rbac.user.GetUserRequest;
import com.losvernos.anzenfs.rbac.user.UpdatePasswordRequest;
import com.losvernos.anzenfs.rbac.user.UpdateUserRolesRequest;
import com.losvernos.anzenfs.rbac.user.UserSummary;

// Native image builds don't reflectively expose record accessors (needed by Jackson to
// (de)serialize JSON bodies) unless explicitly registered - Spring's AOT engine doesn't always
// pick every DTO up automatically. Registering all of them here once avoids hitting this the
// same way on every other endpoint, one UnsupportedFeatureError at a time.
@RegisterReflectionForBinding({
    AuthResponse.class,
    LoginRequest.class,
    CreateUserRequest.class,
    GetUserRequest.class,
    UpdatePasswordRequest.class,
    UpdateUserRolesRequest.class,
    UserSummary.class,
    CreateRoleRequest.class,
    UpdateRoleRequest.class,
    RoleSummary.class,
    CreatePermissionRequest.class,
    PermissionSummary.class,
    UploadJobCreationRequest.class,
    UploadJobSummary.class,
    FileNode.class,
    ResourceAndName.class
})
@ImportRuntimeHints(AnzenfsApplication.WebResourcesHints.class)
@SpringBootApplication
public class AnzenfsApplication {

  private static final String APP_NAME = "anzenfs";
  private static final String DB_NAME = "anzenfs.db";
  private static final String LOG_FILE_NAME = "anzenfs.log";

  static {
    System.setProperty("custom.db.path", getDBFilePath());
    System.setProperty("logging.file.name", getAppDataDir().toPath().resolve("logs").resolve(LOG_FILE_NAME).toString());
  }

  public static void main(String[] args) {
    SpringApplication.run(AnzenfsApplication.class, args);
  }

  private static File getAppDataDir() {
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

  private static String getDBFilePath() {
    File dbFile = new File(getAppDataDir(), DB_NAME);
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
