package com.authsphere.user;

import jakarta.validation.constraints.NotBlank;

public class RoleAssignmentRequest {

    @NotBlank(message = "Role name is required")
    private String roleName;

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
}
