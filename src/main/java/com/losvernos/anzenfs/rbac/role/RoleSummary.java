package com.losvernos.anzenfs.rbac.role;

import com.losvernos.anzenfs.rbac.permission.PermissionSummary;

import java.util.List;

public record RoleSummary(long id, String name, List<PermissionSummary> permissions) {
  public static RoleSummary from(Role role) {
    List<PermissionSummary> permissions = role.getPermissions() == null
        ? List.of()
        : role.getPermissions().stream().map(PermissionSummary::from).toList();
    return new RoleSummary(role.getID(), role.getName(), permissions);
  }
}
