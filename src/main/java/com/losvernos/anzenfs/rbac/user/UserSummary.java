package com.losvernos.anzenfs.rbac.user;

import com.losvernos.anzenfs.rbac.role.RoleSummary;

import java.util.List;

public record UserSummary(long id, String username, List<RoleSummary> roles) {
  public static UserSummary from(User user) {
    List<RoleSummary> roles = user.getUserRoles() == null
        ? List.of()
        : user.getUserRoles().stream().map(RoleSummary::from).toList();
    return new UserSummary(user.getID(), user.getUsername(), roles);
  }
}
