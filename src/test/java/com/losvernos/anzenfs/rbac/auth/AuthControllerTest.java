package com.losvernos.anzenfs.rbac.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import com.losvernos.anzenfs.rbac.user.UserService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void loginReturnsTokenOnSuccess() throws Exception {
        when(userService.authenticate("alice", "secret")).thenReturn("signed-token");

        String body = new ObjectMapper().writeValueAsString(new LoginRequest("alice", "secret"));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-token"));
    }

    @Test
    void meReturnsCurrentUserSummary() throws Exception {
        User currentUser = User.builder().username("alice")
                .userRoles(List.of(new Role(1L, "ADMIN", null)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));

        try {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.roles[0].name").value("ADMIN"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
