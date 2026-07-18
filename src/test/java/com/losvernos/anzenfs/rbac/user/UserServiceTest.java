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

    private UserService userService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepository, roleRepository, fileRepository, authenticationConfiguration, jwtUtils);
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
}
