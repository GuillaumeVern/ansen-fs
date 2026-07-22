package com.losvernos.anzenfs.rbac.user;

import java.util.List;
import java.util.Optional;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.rbac.auth.JwtUtils;
import com.losvernos.anzenfs.rbac.permission.Permission;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.role.RoleRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

  private static final String PROTECTED_ADMIN_USERNAME = "admin";
  private static final String ADMIN_ROLE_NAME = "ADMIN";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final FileRepository fileRepository;
  private final AuthenticationConfiguration authenticationConfiguration;
  private final JwtUtils jwtUtils;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository,
                     RoleRepository roleRepository,
                     FileRepository fileRepository,
                     AuthenticationConfiguration authenticationConfiguration,
                     JwtUtils jwtUtils,
                     PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.fileRepository = fileRepository;
    this.authenticationConfiguration = authenticationConfiguration;
    this.jwtUtils = jwtUtils;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return userRepository.findByUsernameWithRolesAndPermissions(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }

  public String authenticate(String username, String password) {
    try {
      AuthenticationManager authManager = authenticationConfiguration.getAuthenticationManager();
      authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    } catch (Exception e) {
      throw new UsernameNotFoundException("Authentication failed", e);
    }

    User user = userRepository.findByUsernameWithRolesAndPermissions(username)
            .orElseThrow(() -> new UsernameNotFoundException("User profiles syncing anomaly post-auth"));

    List<String> rawRoleNames = user.getUserRoles().stream()
            .map(Role::getName)
            .toList();

    return jwtUtils.generateToken(username, rawRoleNames);
  }

  @Transactional
  public void registerNewUser(CreateUserRequest request) {
    PasswordPolicy.validate(request.password());

    String customRoleName = request.username().toUpperCase() + "_ROLE";

    Role globalUserRole = roleRepository.findByName("USER_ROLE")
            .orElseThrow(() -> new IllegalStateException("Global USER_ROLE missing"));

    Integer rootFolderId = fileRepository.findIdByNameAndParent("root", null)
            .orElseThrow(() -> new IllegalStateException("System root folder missing"));

    Role personalRole = Role.builder()
            .name(customRoleName)
            .permissions(List.of(
                    Permission.builder().name("READ").build(),
                    Permission.builder().name("WRITE").build()
            ))
            .build();

    User newUser = User.builder()
            .username(request.username())
            .password(request.password())
            .userRoles(List.of(personalRole, globalUserRole))
            .build();

    userRepository.save(newUser);

    long folderId = fileRepository.createFolder(rootFolderId, request.username());

    User savedUser = userRepository.findByUsernameWithRolesAndPermissions(request.username())
            .orElseThrow(() -> new IllegalStateException("User tracking failure during creation lifecycle steps"));

    long targetRoleId = savedUser.getUserRoles().stream()
            .filter(r -> r.getName().equals(customRoleName))
            .map(Role::getID)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Role allocation mapping target missing"));

    fileRepository.linkFileToRole(folderId, targetRoleId, "WRITE");
  }

  public List<UserSummary> listUsers() {
    return userRepository.getAllWithRoles().stream().map(UserSummary::from).toList();
  }

  public Optional<UserSummary> getUserSummary(long id) {
    return userRepository.get(id)
            .flatMap(u -> userRepository.findByUsernameWithRolesAndPermissions(u.getUsername()))
            .map(UserSummary::from);
  }

  public Optional<UserSummary> updateUserRoles(User currentUser, long targetId, List<Long> roleIds) {
    if (userRepository.get(targetId).isEmpty()) {
      return Optional.empty();
    }

    if (targetId == currentUser.getID()) {
      boolean currentlyAdmin = currentUser.getUserRoles() != null
              && currentUser.getUserRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase(ADMIN_ROLE_NAME));

      Optional<Role> adminRole = roleRepository.findByName(ADMIN_ROLE_NAME);
      boolean willRemainAdmin = adminRole.isPresent() && roleIds.contains(adminRole.get().getID());

      if (currentlyAdmin && !willRemainAdmin) {
        throw new IllegalStateException("You cannot remove your own ADMIN role.");
      }
    }

    userRepository.replaceUserRoles(targetId, roleIds);
    return getUserSummary(targetId);
  }

  public boolean deleteUser(User currentUser, long targetId) {
    if (targetId == currentUser.getID()) {
      throw new IllegalStateException("You cannot delete your own account.");
    }

    Optional<User> target = userRepository.get(targetId);
    if (target.isEmpty()) {
      return false;
    }

    if (PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(target.get().getUsername())) {
      throw new IllegalStateException("The built-in admin account cannot be deleted.");
    }

    userRepository.deleteById(targetId);
    return true;
  }

  public Optional<UserSummary> updatePassword(long id, String newPassword) {
    if (userRepository.get(id).isEmpty()) {
      return Optional.empty();
    }

    PasswordPolicy.validate(newPassword);
    userRepository.updatePassword(id, passwordEncoder.encode(newPassword));
    return getUserSummary(id);
  }
}