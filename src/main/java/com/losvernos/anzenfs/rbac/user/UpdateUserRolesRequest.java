package com.losvernos.anzenfs.rbac.user;

import java.util.List;

public record UpdateUserRolesRequest(List<Long> roleIds) {
}
