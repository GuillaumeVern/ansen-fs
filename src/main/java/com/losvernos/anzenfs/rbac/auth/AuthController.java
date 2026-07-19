package com.losvernos.anzenfs.rbac.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.losvernos.anzenfs.rbac.user.User;
import com.losvernos.anzenfs.rbac.user.UserService;
import com.losvernos.anzenfs.rbac.user.UserSummary;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
    String token = userService.authenticate(loginRequest.username(), loginRequest.password());
    return ResponseEntity.ok(new AuthResponse(token));
  }

  @GetMapping("/me")
  public ResponseEntity<UserSummary> me(@AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(UserSummary.from(currentUser));
  }
}