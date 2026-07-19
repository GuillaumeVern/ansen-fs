package com.losvernos.anzenfs.rbac.role;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerTest {

    @Mock
    private RoleService roleService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new RoleController(roleService)).build();
    }

    @Test
    void listRolesReturnsServiceResult() throws Exception {
        when(roleService.listRoles()).thenReturn(List.of(new RoleSummary(1L, "EDITOR", List.of())));

        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("EDITOR"));
    }

    @Test
    void createRoleReturnsCreatedSummary() throws Exception {
        when(roleService.createRole(any())).thenReturn(new RoleSummary(5L, "EDITOR", List.of()));

        String body = new ObjectMapper().writeValueAsString(new CreateRoleRequest("EDITOR", List.of(1L)));

        mockMvc.perform(post("/api/admin/roles").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EDITOR"));
    }

    @Test
    void updateRoleReturns200OnSuccess() throws Exception {
        when(roleService.updateRole(eq(1L), any())).thenReturn(Optional.of(new RoleSummary(1L, "RENAMED", List.of())));

        String body = new ObjectMapper().writeValueAsString(new UpdateRoleRequest("RENAMED", List.of()));

        mockMvc.perform(put("/api/admin/roles/1").contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateRoleReturns404WhenMissing() throws Exception {
        when(roleService.updateRole(eq(99L), any())).thenReturn(Optional.empty());

        String body = new ObjectMapper().writeValueAsString(new UpdateRoleRequest("X", List.of()));

        mockMvc.perform(put("/api/admin/roles/99").contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRoleReturns400WhenGuardRejects() throws Exception {
        when(roleService.updateRole(eq(1L), any()))
                .thenThrow(new IllegalStateException("The built-in ADMIN role cannot be renamed."));

        String body = new ObjectMapper().writeValueAsString(new UpdateRoleRequest("SUPERADMIN", List.of()));

        mockMvc.perform(put("/api/admin/roles/1").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRoleReturns200OnSuccess() throws Exception {
        when(roleService.deleteRole(5L)).thenReturn(true);

        mockMvc.perform(delete("/api/admin/roles/5"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRoleReturns404WhenMissing() throws Exception {
        when(roleService.deleteRole(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/admin/roles/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRoleReturns400WhenGuardRejects() throws Exception {
        when(roleService.deleteRole(1L)).thenThrow(new IllegalStateException("The built-in ADMIN role cannot be deleted."));

        mockMvc.perform(delete("/api/admin/roles/1"))
                .andExpect(status().isBadRequest());
    }
}
