package com.losvernos.anzenfs.rbac.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PermissionControllerTest {

    @Mock
    private PermissionRepository permissionRepository;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new PermissionController(permissionRepository)).build();
    }

    @Test
    void listPermissionsReturnsAllPermissions() throws Exception {
        when(permissionRepository.getAll()).thenReturn(List.of(Permission.builder().ID(1L).name("READ").build()));

        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("READ"));
    }

    @Test
    void createPermissionReturnsGeneratedSummary() throws Exception {
        when(permissionRepository.save(any())).thenReturn(7L);

        String body = new ObjectMapper().writeValueAsString(new CreatePermissionRequest("EXECUTE"));

        mockMvc.perform(post("/api/admin/permissions").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("EXECUTE"));
    }

    @Test
    void deletePermissionReturns200WhenFound() throws Exception {
        when(permissionRepository.get(1L)).thenReturn(Optional.of(Permission.builder().ID(1L).name("READ").build()));

        mockMvc.perform(delete("/api/admin/permissions/1"))
                .andExpect(status().isOk());
    }

    @Test
    void deletePermissionReturns404WhenMissing() throws Exception {
        when(permissionRepository.get(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/admin/permissions/99"))
                .andExpect(status().isNotFound());
    }
}
