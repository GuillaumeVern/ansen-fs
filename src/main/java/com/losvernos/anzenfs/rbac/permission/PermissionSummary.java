package com.losvernos.anzenfs.rbac.permission;

public record PermissionSummary(long id, String name) {
  public static PermissionSummary from(Permission permission) {
    return new PermissionSummary(permission.getID(), permission.getName());
  }
}
