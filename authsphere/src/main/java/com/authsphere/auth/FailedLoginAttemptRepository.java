package com.authsphere.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface FailedLoginAttemptRepository extends JpaRepository<FailedLoginAttempt, UUID> {
    long countByUserIdAndAttemptedAtAfter(UUID userId, LocalDateTime after);
    void deleteByUserId(UUID userId);
}
