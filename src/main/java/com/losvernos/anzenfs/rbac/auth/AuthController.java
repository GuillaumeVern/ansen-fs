package com.losvernos.anzenfs.rbac.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import com.losvernos.anzenfs.rbac.user.User;
import com.losvernos.anzenfs.rbac.user.UserService;
import com.losvernos.anzenfs.rbac.user.UserSummary;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;
  private final LoginRateLimiter loginRateLimiter;

  public AuthController(UserService userService, LoginRateLimiter loginRateLimiter) {
    this.userService = userService;
    this.loginRateLimiter = loginRateLimiter;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
    String username = loginRequest.username();

    if (loginRateLimiter.isBlocked(username)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body("Too many failed login attempts. Please try again later.");
    }

    try {
      String token = userService.authenticate(username, loginRequest.password());
      loginRateLimiter.recordSuccessfulLogin(username);
      return ResponseEntity.ok(new AuthResponse(token));
    } catch (UsernameNotFoundException e) {
      loginRateLimiter.recordFailedAttempt(username);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
    }
  }

  @GetMapping("/me")
  public ResponseEntity<UserSummary> me(@AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(UserSummary.from(currentUser));
  }
}