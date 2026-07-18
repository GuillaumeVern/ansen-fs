package com.losvernos.anzenfs.rbac.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final UserRepository userRepository;

  public UserController(UserService userService, UserRepository userRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
  }

  @PostMapping("/create")
  public ResponseEntity<Void> createUser(@RequestBody CreateUserRequest createUserRequest) {
    userService.registerNewUser(createUserRequest);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}")
  public ResponseEntity<User> getUser(@PathVariable long id) {
    return ResponseEntity.ok(userRepository.get(id).orElseThrow());
  }
}