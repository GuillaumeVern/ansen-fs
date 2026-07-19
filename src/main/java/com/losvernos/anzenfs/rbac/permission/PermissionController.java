package com.losvernos.anzenfs.rbac.permission;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {

  private final PermissionRepository permissionRepository;

  public PermissionController(PermissionRepository permissionRepository) {
    this.permissionRepository = permissionRepository;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<PermissionSummary>> listPermissions() {
    return ResponseEntity.ok(permissionRepository.getAll().stream().map(PermissionSummary::from).toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PermissionSummary> createPermission(@RequestBody CreatePermissionRequest request) {
    long id = permissionRepository.save(Permission.builder().name(request.name()).build());
    return ResponseEntity.ok(new PermissionSummary(id, request.name()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> deletePermission(@PathVariable long id) {
    if (permissionRepository.get(id).isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    permissionRepository.deleteById(id);
    return ResponseEntity.ok().build();
  }
}
