package com.losvernos.anzenfs.rbac.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, userRepository)).build();
    }

    @Test
    void createUserDelegatesToServiceAndReturnsOk() throws Exception {
        String body = new ObjectMapper().writeValueAsString(new CreateUserRequest("bob", "secret"));

        mockMvc.perform(post("/api/users/create").contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(userService).registerNewUser(any(CreateUserRequest.class));
    }

    @Test
    void getUserReturnsUserWhenFound() throws Exception {
        User user = User.builder().username("bob").build();
        when(userRepository.get(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void getUserFailsWhenMissing() {
        when(userRepository.get(99L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(get("/api/users/99")))
                .hasRootCauseInstanceOf(java.util.NoSuchElementException.class);
    }
}
