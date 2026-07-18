package com.losvernos.anzenfs.rbac.role;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<RoleSummary>> listRoles() {
    return ResponseEntity.ok(roleService.listRoles());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<RoleSummary> createRole(@RequestBody CreateRoleRequest request) {
    return ResponseEntity.ok(roleService.createRole(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> updateRole(@PathVariable long id, @RequestBody UpdateRoleRequest request) {
    try {
      return roleService.updateRole(id, request)
              .<ResponseEntity<?>>map(ResponseEntity::ok)
              .orElseGet(() -> ResponseEntity.notFound().build());
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> deleteRole(@PathVariable long id) {
    try {
      boolean deleted = roleService.deleteRole(id);
      return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
