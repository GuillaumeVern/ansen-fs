package com.losvernos.anzenfs.rbac.role;

import java.util.List;

public record UpdateRoleRequest(String name, List<Long> permissionIds) {
}
