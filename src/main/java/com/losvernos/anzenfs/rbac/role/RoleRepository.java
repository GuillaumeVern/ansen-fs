package com.losvernos.anzenfs.rbac.role;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Role> getAll() {
        String sql = "SELECT * FROM roles;";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Role role = new Role();
            role.setID(rs.getLong("role_id"));
            role.setName(rs.getString("role_name"));
            return role;
        });
    }

    public Optional<Role> get(long ID) {
        String sql = "SELECT * FROM roles WHERE role_id = ?;";
        try {
            Role role = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Role r = new Role();
                r.setID(rs.getLong("role_id"));
                r.setName(rs.getString("role_name"));
                return r;
            }, ID);
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void save(Role elementToSave) {
        String sql = "INSERT INTO roles (role_name) VALUES (?);";
        jdbcTemplate.update(sql, elementToSave.getName());
    }

    public void update(Role elementToUpdate, String[] params) {
        System.out.println("update role not implemented");
    }

    public void delete(Role elementToDelete) {
        System.out.println("delete role not implemented");
    }

    public Optional<Role> findByName(String name) {
        String sql = "SELECT role_id, role_name FROM roles WHERE role_name = ?;";

        try {
            Role role = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                            new Role(
                                    rs.getLong("role_id"),
                                    rs.getString("role_name"),
                                    null
                            ),
                    name
            );
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}