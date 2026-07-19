package com.losvernos.anzenfs.rbac.user;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.rbac.auth.JwtUtils;
import com.losvernos.anzenfs.rbac.permission.Permission;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.role.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private AuthenticationConfiguration authenticationConfiguration;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepository, roleRepository, fileRepository, authenticationConfiguration, jwtUtils, passwordEncoder);
    }

    @Test
    void loadUserByUsernameReturnsUserWhenFound() {
        User user = User.builder().username("alice").build();
        when(userRepository.findByUsernameWithRolesAndPermissions("alice")).thenReturn(Optional.of(user));

        assertThat(userService.loadUserByUsername("alice")).isEqualTo(user);
    }

    @Test
    void loadUserByUsernameThrowsWhenMissing() {
        when(userRepository.findByUsernameWithRolesAndPermissions("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void authenticateReturnsGeneratedTokenOnSuccess() throws Exception {
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);

        Role role = new Role(1L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(role)).build();
        when(userRepository.findByUsernameWithRolesAndPermissions("alice")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(eq("alice"), eq(List.of("USER_ROLE")))).thenReturn("signed-token");

        String token = userService.authenticate("alice", "password");

        assertThat(token).isEqualTo("signed-token");
    }

    @Test
    void authenticateThrowsWhenCredentialsInvalid() throws Exception {
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        assertThatThrownBy(() -> userService.authenticate("alice", "wrong"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void authenticateThrowsWhenPostAuthLookupFails() throws Exception {
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);
        when(userRepository.findByUsernameWithRolesAndPermissions("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticate("alice", "password"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void registerNewUserWiresPersonalRoleAndFolder() {
        Role globalRole = new Role(2L, "USER_ROLE", null);
        when(roleRepository.findByName("USER_ROLE")).thenReturn(Optional.of(globalRole));
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.createFolder(10, "bob")).thenReturn(55);

        Role personalRole = new Role(99L, "BOB_ROLE", List.of(
                Permission.builder().name("READ").build(),
                Permission.builder().name("WRITE").build()));
        User savedUser = User.builder().username("bob").userRoles(List.of(personalRole, globalRole)).build();
        when(userRepository.findByUsernameWithRolesAndPermissions("bob")).thenReturn(Optional.of(savedUser));

        userService.registerNewUser(new CreateUserRequest("bob", "secret"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("bob");
        assertThat(userCaptor.getValue().getUserRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("BOB_ROLE", "USER_ROLE");

        org.mockito.Mockito.verify(fileRepository).linkFileToRole(55L, 99L, "WRITE");
    }

    @Test
    void registerNewUserThrowsWhenGlobalRoleMissing() {
        when(roleRepository.findByName("USER_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerNewUser(new CreateUserRequest("bob", "secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerNewUserThrowsWhenRootFolderMissing() {
        when(roleRepository.findByName("USER_ROLE")).thenReturn(Optional.of(new Role(2L, "USER_ROLE", null)));
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerNewUser(new CreateUserRequest("bob", "secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerNewUserThrowsWhenPostSaveLookupFails() {
        when(roleRepository.findByName("USER_ROLE")).thenReturn(Optional.of(new Role(2L, "USER_ROLE", null)));
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.createFolder(10, "bob")).thenReturn(55);
        when(userRepository.findByUsernameWithRolesAndPermissions("bob")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerNewUser(new CreateUserRequest("bob", "secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerNewUserThrowsWhenPersonalRoleNotFoundAfterSave() {
        when(roleRepository.findByName("USER_ROLE")).thenReturn(Optional.of(new Role(2L, "USER_ROLE", null)));
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.createFolder(10, "bob")).thenReturn(55);
        User savedUserMissingPersonalRole = User.builder().username("bob")
                .userRoles(List.of(new Role(2L, "USER_ROLE", null)))
                .build();
        when(userRepository.findByUsernameWithRolesAndPermissions("bob")).thenReturn(Optional.of(savedUserMissingPersonalRole));

        assertThatThrownBy(() -> userService.registerNewUser(new CreateUserRequest("bob", "secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listUsersMapsRepositoryResultsToSummaries() {
        User user = User.builder().ID(1L).username("alice").userRoles(List.of(new Role(1L, "USER_ROLE", null))).build();
        when(userRepository.getAllWithRoles()).thenReturn(List.of(user));

        List<UserSummary> summaries = userService.listUsers();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).username()).isEqualTo("alice");
        assertThat(summaries.get(0).roles()).extracting(r -> r.name()).containsExactly("USER_ROLE");
    }

    @Test
    void getUserSummaryReturnsEmptyWhenMissing() {
        when(userRepository.get(99L)).thenReturn(Optional.empty());

        assertThat(userService.getUserSummary(99L)).isEmpty();
    }

    @Test
    void getUserSummaryHydratesRolesForExistingUser() {
        User basic = User.builder().ID(1L).username("alice").build();
        User withRoles = User.builder().ID(1L).username("alice")
                .userRoles(List.of(new Role(1L, "USER_ROLE", null)))
                .build();
        when(userRepository.get(1L)).thenReturn(Optional.of(basic));
        when(userRepository.findByUsernameWithRolesAndPermissions("alice")).thenReturn(Optional.of(withRoles));

        Optional<UserSummary> summary = userService.getUserSummary(1L);

        assertThat(summary).isPresent();
        assertThat(summary.get().roles()).hasSize(1);
    }

    @Test
    void updateUserRolesReturnsEmptyWhenTargetMissing() {
        User currentAdmin = User.builder().ID(1L).username("admin").userRoles(List.of(new Role(1L, "ADMIN", null))).build();
        when(userRepository.get(99L)).thenReturn(Optional.empty());

        assertThat(userService.updateUserRoles(currentAdmin, 99L, List.of(1L))).isEmpty();
    }

    @Test
    void updateUserRolesReplacesRolesForAnotherUser() {
        User currentAdmin = User.builder().ID(1L).username("admin").userRoles(List.of(new Role(1L, "ADMIN", null))).build();
        User target = User.builder().ID(2L).username("bob").build();
        when(userRepository.get(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsernameWithRolesAndPermissions("bob")).thenReturn(Optional.of(target));

        Optional<UserSummary> result = userService.updateUserRoles(currentAdmin, 2L, List.of(5L, 6L));

        org.mockito.Mockito.verify(userRepository).replaceUserRoles(2L, List.of(5L, 6L));
        assertThat(result).isPresent();
    }

    @Test
    void updateUserRolesRejectsSelfRemovalOfAdminRole() {
        User currentAdmin = User.builder().ID(1L).username("admin").userRoles(List.of(new Role(1L, "ADMIN", null))).build();
        when(userRepository.get(1L)).thenReturn(Optional.of(currentAdmin));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(new Role(1L, "ADMIN", null)));

        assertThatThrownBy(() -> userService.updateUserRoles(currentAdmin, 1L, List.of(2L)))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).replaceUserRoles(any(Long.class), any());
    }

    @Test
    void updateUserRolesAllowsSelfUpdateWhenAdminRoleRetained() {
        User currentAdmin = User.builder().ID(1L).username("admin").userRoles(List.of(new Role(1L, "ADMIN", null))).build();
        when(userRepository.get(1L)).thenReturn(Optional.of(currentAdmin));
        when(userRepository.findByUsernameWithRolesAndPermissions("admin")).thenReturn(Optional.of(currentAdmin));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(new Role(1L, "ADMIN", null)));

        Optional<UserSummary> result = userService.updateUserRoles(currentAdmin, 1L, List.of(1L, 2L));

        assertThat(result).isPresent();
        org.mockito.Mockito.verify(userRepository).replaceUserRoles(1L, List.of(1L, 2L));
    }

    @Test
    void deleteUserRejectsSelfDeletion() {
        User currentAdmin = User.builder().ID(1L).username("admin").build();

        assertThatThrownBy(() -> userService.deleteUser(currentAdmin, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteUserReturnsFalseWhenTargetMissing() {
        User currentAdmin = User.builder().ID(1L).username("admin").build();
        when(userRepository.get(2L)).thenReturn(Optional.empty());

        assertThat(userService.deleteUser(currentAdmin, 2L)).isFalse();
    }

    @Test
    void deleteUserRejectsDeletingTheBuiltInAdminAccount() {
        User currentAdmin = User.builder().ID(1L).username("someoneElse").build();
        User builtInAdmin = User.builder().ID(2L).username("admin").build();
        when(userRepository.get(2L)).thenReturn(Optional.of(builtInAdmin));

        assertThatThrownBy(() -> userService.deleteUser(currentAdmin, 2L))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).deleteById(any(Long.class));
    }

    @Test
    void deleteUserRemovesAnOrdinaryUser() {
        User currentAdmin = User.builder().ID(1L).username("admin").build();
        User target = User.builder().ID(2L).username("bob").build();
        when(userRepository.get(2L)).thenReturn(Optional.of(target));

        assertThat(userService.deleteUser(currentAdmin, 2L)).isTrue();
        org.mockito.Mockito.verify(userRepository).deleteById(2L);
    }

    @Test
    void updatePasswordReturnsEmptyWhenUserMissing() {
        when(userRepository.get(99L)).thenReturn(Optional.empty());

        assertThat(userService.updatePassword(99L, "newpass")).isEmpty();
        org.mockito.Mockito.verify(passwordEncoder, org.mockito.Mockito.never()).encode(any());
    }

    @Test
    void updatePasswordEncodesAndStoresNewPassword() {
        User target = User.builder().ID(1L).username("alice").build();
        when(userRepository.get(1L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsernameWithRolesAndPermissions("alice")).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded-newpass");

        Optional<UserSummary> result = userService.updatePassword(1L, "newpass");

        assertThat(result).isPresent();
        org.mockito.Mockito.verify(userRepository).updatePassword(1L, "encoded-newpass");
    }
}
