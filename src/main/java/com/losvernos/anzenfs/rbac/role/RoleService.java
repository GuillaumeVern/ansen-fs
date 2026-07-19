package com.losvernos.anzenfs.rbac.role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

  private static final Set<String> PROTECTED_ROLE_NAMES = Set.of("ADMIN", "USER_ROLE");

  private final RoleRepository roleRepository;

  public RoleService(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  public List<RoleSummary> listRoles() {
    return roleRepository.getAllWithPermissions().stream().map(RoleSummary::from).toList();
  }

  @Transactional
  public RoleSummary createRole(CreateRoleRequest request) {
    Role role = roleRepository.createRoleWithPermissions(request.name(), request.permissionIds());
    return getRole(role.getID()).orElseThrow();
  }

  @Transactional
  public Optional<RoleSummary> updateRole(long id, UpdateRoleRequest request) {
    Optional<Role> existing = roleRepository.get(id);
    if (existing.isEmpty()) {
      return Optional.empty();
    }

    Role role = existing.get();
    if (isProtected(role.getName()) && !role.getName().equals(request.name())) {
      throw new IllegalStateException("The built-in " + role.getName() + " role cannot be renamed.");
    }

    roleRepository.renameRole(id, request.name());
    roleRepository.replaceRolePermissions(id, request.permissionIds());
    return getRole(id);
  }

  public boolean deleteRole(long id) {
    Optional<Role> role = roleRepository.get(id);
    if (role.isEmpty()) {
      return false;
    }

    if (isProtected(role.get().getName())) {
      throw new IllegalStateException("The built-in " + role.get().getName() + " role cannot be deleted.");
    }

    roleRepository.deleteById(id);
    return true;
  }

  private Optional<RoleSummary> getRole(long id) {
    return roleRepository.getAllWithPermissions().stream()
            .filter(r -> r.getID() == id)
            .map(RoleSummary::from)
            .findFirst();
  }

  private boolean isProtected(String name) {
    return name != null && PROTECTED_ROLE_NAMES.contains(name.toUpperCase());
  }
}
