package com.authsphere.session;

import com.authsphere.audit.AuditService;
import com.authsphere.auth.RefreshTokenRepository;
import com.authsphere.exception.ResourceNotFoundException;
import com.authsphere.security.JwtService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    public SessionService(SessionRepository sessionRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          AuditService auditService,
                          JwtService jwtService,
                          RedisTemplate<String, String> redisTemplate) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
    }

    public List<SessionResponse> getActiveSessions(UUID userId, UUID currentFamilyId) {
        return sessionRepository.findByUserIdAndActiveTrue(userId).stream()
            .map(s -> new SessionResponse(
                s.getId(),
                s.getDeviceName(),
                s.getBrowser(),
                s.getOs(),
                s.getIpAddress(),
                s.getLoginTime(),
                s.getLastActivity(),
                s.getRefreshTokenFamilyId().equals(currentFamilyId)
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID userId, String currentAccessToken, String ipAddress) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("You can only revoke your own sessions");
        }

        session.setActive(false);
        sessionRepository.save(session);

        refreshTokenRepository.revokeAllByFamilyId(session.getRefreshTokenFamilyId());

        try {
            String tokenId = jwtService.extractTokenId(currentAccessToken);
            Date expiration = jwtService.extractExpiration(currentAccessToken);
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(
                    "blacklist:" + tokenId, "true", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            // Token already invalid, continue
        }

        String metadata = "{\"sessionId\":\"" + sessionId + "\"}";
        auditService.log(userId, "SESSION_REVOKED", ipAddress, metadata);
    }

    @Transactional
    public void revokeAllOtherSessions(UUID userId, UUID currentFamilyId,
                                       String currentAccessToken, String ipAddress) {
        List<Session> sessions = sessionRepository.findByUserIdAndActiveTrue(userId);

        sessions.stream()
            .filter(s -> !s.getRefreshTokenFamilyId().equals(currentFamilyId))
            .forEach(s -> {
                s.setActive(false);
                sessionRepository.save(s);
                refreshTokenRepository.revokeAllByFamilyId(s.getRefreshTokenFamilyId());
            });

        auditService.log(userId, "ALL_OTHER_SESSIONS_REVOKED", ipAddress, null);
    }
}
