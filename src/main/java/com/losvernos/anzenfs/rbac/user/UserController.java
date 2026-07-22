package com.losvernos.anzenfs.rbac.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/create")
  public ResponseEntity<?> createUser(@RequestBody CreateUserRequest createUserRequest) {
    try {
      userService.registerNewUser(createUserRequest);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}