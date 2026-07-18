package com.losvernos.anzenfs.rbac.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.losvernos.anzenfs.rbac.user.UserService;

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
}