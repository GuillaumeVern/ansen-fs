package com.losvernos.anzenfs.rbac.role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleService roleService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        roleService = new RoleService(roleRepository);
    }

    @Test
    void listRolesMapsRepositoryResultsToSummaries() {
        when(roleRepository.getAllWithPermissions()).thenReturn(List.of(new Role(1L, "USER_ROLE", List.of())));

        List<RoleSummary> roles = roleService.listRoles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).name()).isEqualTo("USER_ROLE");
    }

    @Test
    void createRoleDelegatesToRepositoryAndReturnsSummary() {
        Role created = new Role(5L, "EDITOR", List.of());
        when(roleRepository.createRoleWithPermissions("EDITOR", List.of(1L, 2L))).thenReturn(created);
        when(roleRepository.getAllWithPermissions()).thenReturn(List.of(created));

        RoleSummary summary = roleService.createRole(new CreateRoleRequest("EDITOR", List.of(1L, 2L)));

        assertThat(summary.name()).isEqualTo("EDITOR");
    }

    @Test
    void updateRoleReturnsEmptyWhenRoleMissing() {
        when(roleRepository.get(99L)).thenReturn(Optional.empty());

        assertThat(roleService.updateRole(99L, new UpdateRoleRequest("NEW", List.of()))).isEmpty();
    }

    @Test
    void updateRoleRenamesAndReplacesPermissions() {
        Role existing = new Role(1L, "EDITOR", List.of());
        when(roleRepository.get(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.getAllWithPermissions()).thenReturn(List.of(new Role(1L, "SENIOR_EDITOR", List.of())));

        Optional<RoleSummary> result = roleService.updateRole(1L, new UpdateRoleRequest("SENIOR_EDITOR", List.of(3L)));

        assertThat(result).isPresent();
        verify(roleRepository).renameRole(1L, "SENIOR_EDITOR");
        verify(roleRepository).replaceRolePermissions(1L, List.of(3L));
    }

    @Test
    void updateRoleRejectsRenamingProtectedAdminRole() {
        when(roleRepository.get(1L)).thenReturn(Optional.of(new Role(1L, "ADMIN", List.of())));

        assertThatThrownBy(() -> roleService.updateRole(1L, new UpdateRoleRequest("SUPERADMIN", List.of())))
                .isInstanceOf(IllegalStateException.class);

        verify(roleRepository, never()).renameRole(anyLong(), any());
    }

    @Test
    void updateRoleRejectsRenamingProtectedUserRole() {
        when(roleRepository.get(2L)).thenReturn(Optional.of(new Role(2L, "USER_ROLE", List.of())));

        assertThatThrownBy(() -> roleService.updateRole(2L, new UpdateRoleRequest("REGULAR", List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateRoleAllowsPermissionChangesOnProtectedRoleWhenNameUnchanged() {
        when(roleRepository.get(1L)).thenReturn(Optional.of(new Role(1L, "ADMIN", List.of())));
        when(roleRepository.getAllWithPermissions()).thenReturn(List.of(new Role(1L, "ADMIN", List.of())));

        Optional<RoleSummary> result = roleService.updateRole(1L, new UpdateRoleRequest("ADMIN", List.of(9L)));

        assertThat(result).isPresent();
        verify(roleRepository).replaceRolePermissions(1L, List.of(9L));
    }

    @Test
    void deleteRoleReturnsFalseWhenMissing() {
        when(roleRepository.get(99L)).thenReturn(Optional.empty());

        assertThat(roleService.deleteRole(99L)).isFalse();
    }

    @Test
    void deleteRoleRejectsProtectedAdminRole() {
        when(roleRepository.get(1L)).thenReturn(Optional.of(new Role(1L, "ADMIN", List.of())));

        assertThatThrownBy(() -> roleService.deleteRole(1L)).isInstanceOf(IllegalStateException.class);
        verify(roleRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteRoleRejectsProtectedUserRole() {
        when(roleRepository.get(2L)).thenReturn(Optional.of(new Role(2L, "USER_ROLE", List.of())));

        assertThatThrownBy(() -> roleService.deleteRole(2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteRoleRemovesOrdinaryRole() {
        when(roleRepository.get(5L)).thenReturn(Optional.of(new Role(5L, "EDITOR", List.of())));

        assertThat(roleService.deleteRole(5L)).isTrue();
        verify(roleRepository).deleteById(5L);
    }
}
