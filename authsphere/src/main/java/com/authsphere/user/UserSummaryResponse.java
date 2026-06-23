package com.authsphere.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class UserSummaryResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private List<String> roles;
    private LocalDateTime createdAt;

    public UserSummaryResponse(UUID id, String email, String firstName, String lastName,
                               UserStatus status, List<String> roles, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public UserStatus getStatus() { return status; }
    public List<String> getRoles() { return roles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
