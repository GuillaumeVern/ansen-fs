package com.losvernos.anzenfs.rbac.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.losvernos.anzenfs.rbac.role.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;
    private final User admin = User.builder().username("admin").userRoles(List.of(new Role(1L, "ADMIN", null))).build();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        AdminUserController controller = new AdminUserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void listUsersReturnsServiceResult() throws Exception {
        when(userService.listUsers()).thenReturn(List.of(new UserSummary(1L, "bob", List.of())));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"));
    }

    @Test
    void getUserReturns404WhenMissing() throws Exception {
        when(userService.getUserSummary(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserReturnsSummaryWhenFound() throws Exception {
        when(userService.getUserSummary(1L)).thenReturn(Optional.of(new UserSummary(1L, "bob", List.of())));

        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void updateUserRolesReturns200OnSuccess() throws Exception {
        when(userService.updateUserRoles(any(User.class), eq(2L), eq(List.of(3L))))
                .thenReturn(Optional.of(new UserSummary(2L, "bob", List.of())));

        String body = new ObjectMapper().writeValueAsString(new UpdateUserRolesRequest(List.of(3L)));

        mockMvc.perform(put("/api/admin/users/2/roles").contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateUserRolesReturns400WhenGuardRejects() throws Exception {
        when(userService.updateUserRoles(any(User.class), eq(1L), any()))
                .thenThrow(new IllegalStateException("You cannot remove your own ADMIN role."));

        String body = new ObjectMapper().writeValueAsString(new UpdateUserRolesRequest(List.of()));

        mockMvc.perform(put("/api/admin/users/1/roles").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUserRolesReturns404WhenTargetMissing() throws Exception {
        when(userService.updateUserRoles(any(User.class), eq(99L), any())).thenReturn(Optional.empty());

        String body = new ObjectMapper().writeValueAsString(new UpdateUserRolesRequest(List.of()));

        mockMvc.perform(put("/api/admin/users/99/roles").contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePasswordReturns200OnSuccess() throws Exception {
        when(userService.updatePassword(2L, "newpass")).thenReturn(Optional.of(new UserSummary(2L, "bob", List.of())));

        String body = new ObjectMapper().writeValueAsString(new UpdatePasswordRequest("newpass"));

        mockMvc.perform(put("/api/admin/users/2/password").contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updatePasswordReturns404WhenMissing() throws Exception {
        when(userService.updatePassword(99L, "newpass")).thenReturn(Optional.empty());

        String body = new ObjectMapper().writeValueAsString(new UpdatePasswordRequest("newpass"));

        mockMvc.perform(put("/api/admin/users/99/password").contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUserReturns200OnSuccess() throws Exception {
        when(userService.deleteUser(any(User.class), eq(2L))).thenReturn(true);

        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUserReturns404WhenMissing() throws Exception {
        when(userService.deleteUser(any(User.class), eq(99L))).thenReturn(false);

        mockMvc.perform(delete("/api/admin/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUserReturns400WhenGuardRejects() throws Exception {
        when(userService.deleteUser(any(User.class), eq(1L)))
                .thenThrow(new IllegalStateException("You cannot delete your own account."));

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isBadRequest());
    }
}
