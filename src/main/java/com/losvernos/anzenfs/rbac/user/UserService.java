package com.losvernos.anzenfs.rbac.user;

import java.util.List;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final FileRepository fileRepository;
  private final AuthenticationConfiguration authenticationConfiguration;
  private final JwtUtils jwtUtils;

  public UserService(UserRepository userRepository,
                     RoleRepository roleRepository,
                     FileRepository fileRepository,
                     AuthenticationConfiguration authenticationConfiguration,
                     JwtUtils jwtUtils) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.fileRepository = fileRepository;
    this.authenticationConfiguration = authenticationConfiguration;
    this.jwtUtils = jwtUtils;
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
}