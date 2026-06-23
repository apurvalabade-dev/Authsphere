package com.authsphere.auth;

import com.authsphere.audit.AuditService;
import com.authsphere.user.User;
import com.authsphere.user.UserRepository;
import com.authsphere.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccountSecurityService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ATTEMPT_WINDOW_MINUTES = 15;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final FailedLoginAttemptRepository failedLoginAttemptRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AccountSecurityService(FailedLoginAttemptRepository failedLoginAttemptRepository,
                                  UserRepository userRepository,
                                  AuditService auditService) {
        this.failedLoginAttemptRepository = failedLoginAttemptRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(User user, String ipAddress) {
        FailedLoginAttempt attempt = new FailedLoginAttempt();
        attempt.setUserId(user.getId());
        attempt.setIpAddress(ipAddress);
        failedLoginAttemptRepository.save(attempt);

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(ATTEMPT_WINDOW_MINUTES);
        long recentFailures = failedLoginAttemptRepository
            .countByUserIdAndAttemptedAtAfter(user.getId(), windowStart);

        if (recentFailures >= MAX_FAILED_ATTEMPTS) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            userRepository.save(user);
            auditService.log(user.getId(), "ACCOUNT_LOCKED", ipAddress,
                "{\"reason\":\"max_failed_attempts\",\"attempts\":" + recentFailures + "}");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isLockExpiredAndUnlock(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            userRepository.save(user);
            failedLoginAttemptRepository.deleteByUserId(user.getId());
            auditService.log(user.getId(), "ACCOUNT_UNLOCKED", null, null);
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailedAttempts(User user) {
        failedLoginAttemptRepository.deleteByUserId(user.getId());
    }
}
