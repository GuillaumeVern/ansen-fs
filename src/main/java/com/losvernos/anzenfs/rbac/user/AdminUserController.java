package com.losvernos.anzenfs.rbac.user;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserSummary>> listUsers() {
    return ResponseEntity.ok(userService.listUsers());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserSummary> getUser(@PathVariable long id) {
    return userService.getUserSummary(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> updateUserRoles(@PathVariable long id,
                                            @RequestBody UpdateUserRolesRequest request,
                                            @AuthenticationPrincipal User currentUser) {
    try {
      return userService.updateUserRoles(currentUser, id, request.roleIds())
              .<ResponseEntity<?>>map(ResponseEntity::ok)
              .orElseGet(() -> ResponseEntity.notFound().build());
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}/password")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> updatePassword(@PathVariable long id, @RequestBody UpdatePasswordRequest request) {
    try {
      return userService.updatePassword(id, request.newPassword())
              .<ResponseEntity<?>>map(ResponseEntity::ok)
              .orElseGet(() -> ResponseEntity.notFound().build());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> deleteUser(@PathVariable long id, @AuthenticationPrincipal User currentUser) {
    try {
      boolean deleted = userService.deleteUser(currentUser, id);
      return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
