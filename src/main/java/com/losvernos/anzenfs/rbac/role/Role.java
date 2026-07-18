package com.losvernos.anzenfs.rbac.role;

import java.util.List;

import com.losvernos.anzenfs.rbac.permission.Permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Role {

  private long ID;

  private String name;

  private List<Permission> permissions;

  public Role(String name) {
    this.name = name;
    this.permissions = new java.util.ArrayList<>();
  }
}
