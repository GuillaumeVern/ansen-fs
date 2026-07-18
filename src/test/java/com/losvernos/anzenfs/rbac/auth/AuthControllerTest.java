package com.losvernos.anzenfs.rbac.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.losvernos.anzenfs.rbac.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService)).build();
    }

    @Test
    void loginReturnsTokenOnSuccess() throws Exception {
        when(userService.authenticate("alice", "secret")).thenReturn("signed-token");

        String body = new ObjectMapper().writeValueAsString(new LoginRequest("alice", "secret"));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-token"));
    }
}
