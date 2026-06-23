package com.authsphere.user;

import com.authsphere.audit.AuditService;
import com.authsphere.exception.AuthException;
import com.authsphere.exception.ResourceNotFoundException;
import com.authsphere.role.Role;
import com.authsphere.role.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    public List<UserSummaryResponse> listAllUsers() {
        return userRepository.findAll().stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
    }

    public UserSummaryResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toSummary(user);
    }

    @Transactional
    public UserSummaryResponse assignRole(UUID userId, String roleName, UUID actingAdminId, String ipAddress) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Role role = roleRepository.findByName(roleName.toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Role does not exist: " + roleName));

        user.getRoles().add(role);
        userRepository.save(user);

        auditService.log(actingAdminId, "ROLE_ASSIGNED", ipAddress,
            "{\"targetUser\":\"" + userId + "\",\"role\":\"" + roleName.toUpperCase() + "\"}");

        return toSummary(user);
    }

    @Transactional
    public void deleteUser(UUID userId, UUID actingAdminId, String ipAddress) {
        if (userId.equals(actingAdminId)) {
            throw new AuthException("You cannot delete your own account");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userRepository.delete(user);
        auditService.log(actingAdminId, "USER_DELETED", ipAddress, "{\"targetUser\":\"" + userId + "\"}");
    }

    private UserSummaryResponse toSummary(User user) {
        List<String> roleNames = user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toList());
        return new UserSummaryResponse(
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.getStatus(), roleNames, user.getCreatedAt()
        );
    }
}
