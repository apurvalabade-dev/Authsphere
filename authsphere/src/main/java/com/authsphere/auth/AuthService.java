package com.authsphere.auth;

import com.authsphere.audit.AuditService;
import com.authsphere.exception.EmailAlreadyExistsException;
import com.authsphere.exception.AuthException;
import com.authsphere.notification.EmailService;
import com.authsphere.role.Role;
import com.authsphere.role.RoleRepository;
import com.authsphere.security.JwtService;
import com.authsphere.session.Session;
import com.authsphere.session.SessionRepository;
import com.authsphere.user.User;
import com.authsphere.user.UserRepository;
import com.authsphere.user.UserStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final AuditService auditService;
    private final RedisTemplate<String, String> redisTemplate;
    private final TokenSecurityService tokenSecurityService;
    private final AccountSecurityService accountSecurityService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService,
                       JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository,
                       SessionRepository sessionRepository,
                       AuditService auditService,
                       RedisTemplate<String, String> redisTemplate,
                       TokenSecurityService tokenSecurityService,
                       AccountSecurityService accountSecurityService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
        this.tokenSecurityService = tokenSecurityService;
        this.accountSecurityService = accountSecurityService;
    }

    @Transactional
    public String register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setStatus(UserStatus.UNVERIFIED);

        Role userRole = roleRepository.findByName("USER")
            .orElseThrow(() -> new RuntimeException("Default role USER not found"));
        user.getRoles().add(userRole);
        userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(rawToken.getBytes());

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setTokenHash(tokenHash);
        verificationToken.setUser(user);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), rawToken);

        return "Registration successful. Please check your email to verify your account.";
    }

    @Transactional
    public String verifyEmail(String rawToken) {
        String tokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(rawToken.getBytes());

        EmailVerificationToken token = emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthException("Invalid verification token"));

        if (token.isUsed()) throw new AuthException("Token already used");
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) throw new AuthException("Token expired");

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return "Email verified successfully. You can now log in.";
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
            .orElseThrow(() -> new AuthException("Invalid email or password"));

        if (user.getStatus() == UserStatus.LOCKED) {
            boolean unlocked = accountSecurityService.isLockExpiredAndUnlock(user);
            if (!unlocked) {
                String waitMessage;
                if (user.getLockedUntil() != null) {
                    long minutesRemaining = Math.max(1,
                        Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes());
                    waitMessage = "Try again in " + minutesRemaining + " minute(s).";
                } else {
                    waitMessage = "Please try again later.";
                }
                throw new AuthException("Account is locked due to too many failed attempts. " + waitMessage);
            }
        }

        if (user.getStatus() == UserStatus.UNVERIFIED) {
            throw new AuthException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            accountSecurityService.recordFailedAttempt(user, ipAddress);
            auditService.log(user.getId(), "LOGIN_FAILURE", ipAddress, "{\"reason\":\"invalid_password\"}");
            throw new AuthException("Invalid email or password");
        }

        accountSecurityService.clearFailedAttempts(user);

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = UUID.randomUUID().toString();
        String refreshTokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(rawRefreshToken.getBytes());
        UUID familyId = UUID.randomUUID();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(refreshTokenHash);
        refreshToken.setFamilyId(familyId);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenRepository.save(refreshToken);

        Session session = new Session();
        session.setUserId(user.getId());
        session.setRefreshTokenFamilyId(familyId);
        session.setIpAddress(ipAddress);
        session.setDeviceName(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 255)) : "Unknown");
        sessionRepository.save(session);

        auditService.log(user.getId(), "LOGIN_SUCCESS", ipAddress, null);

        return new LoginResponse(accessToken, rawRefreshToken, 900);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String tokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(rawRefreshToken.getBytes());

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthException("Invalid refresh token"));

        if (token.isUsed()) {
            tokenSecurityService.revokeFamily(token.getFamilyId());
            throw new AuthException("Refresh token already used. Possible token theft detected.");
        }

        if (token.isRevoked()) throw new AuthException("Refresh token revoked");
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) throw new AuthException("Refresh token expired");

        token.setUsed(true);
        refreshTokenRepository.save(token);

        User user = token.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRawRefreshToken = UUID.randomUUID().toString();
        String newRefreshTokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(newRawRefreshToken.getBytes());

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setTokenHash(newRefreshTokenHash);
        newRefreshToken.setFamilyId(token.getFamilyId());
        newRefreshToken.setUser(user);
        newRefreshToken.setParentId(token.getId());
        newRefreshToken.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenRepository.save(newRefreshToken);

        return new LoginResponse(newAccessToken, newRawRefreshToken, 900);
    }

    @Transactional
    public String logout(String accessToken, String rawRefreshToken, UUID userId, String ipAddress) {
        try {
            String tokenId = jwtService.extractTokenId(accessToken);
            Date expiration = jwtService.extractExpiration(accessToken);
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set("blacklist:" + tokenId, "true", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            // Token already invalid, continue
        }

        if (rawRefreshToken != null) {
            String tokenHash = org.springframework.util.DigestUtils
                .md5DigestAsHex(rawRefreshToken.getBytes());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                sessionRepository.deactivateByFamilyId(token.getFamilyId());
            });
        }

        auditService.log(userId, "LOGOUT", ipAddress, null);
        return "Logged out successfully";
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        String email = request.getEmail().toLowerCase().trim();
        String genericResponse = "If an account with that email exists, a password reset link has been sent.";

        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = org.springframework.util.DigestUtils
                .md5DigestAsHex(rawToken.getBytes());

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setTokenHash(tokenHash);
            resetToken.setUser(user);
            resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            auditService.log(user.getId(), "PASSWORD_RESET_REQUEST", ipAddress, null);
        });

        // Always return the same message whether or not the email exists,
        // so the endpoint can't be used to enumerate registered accounts.
        return genericResponse;
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request, String ipAddress) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String tokenHash = org.springframework.util.DigestUtils
            .md5DigestAsHex(request.getToken().getBytes());

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthException("Invalid or expired reset token"));

        if (token.isUsed()) throw new AuthException("This reset link has already been used");
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) throw new AuthException("This reset link has expired");

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // A successful reset is valid proof of identity — clear any lockout.
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
        }
        userRepository.save(user);
        accountSecurityService.clearFailedAttempts(user);

        // Force logout everywhere: revoke every refresh token family for this user.
        List<RefreshToken> activeTokens = refreshTokenRepository.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())
            .toList();
        activeTokens.forEach(t -> tokenSecurityService.revokeFamily(t.getFamilyId()));

        auditService.log(user.getId(), "PASSWORD_CHANGED", ipAddress, null);

        return "Password reset successful. Please log in with your new password.";
    }
}
