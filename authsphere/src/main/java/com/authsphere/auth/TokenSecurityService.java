package com.authsphere.auth;

import com.authsphere.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TokenSecurityService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;

    public TokenSecurityService(RefreshTokenRepository refreshTokenRepository,
                                SessionRepository sessionRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        refreshTokenRepository.revokeAllByFamilyId(familyId);
        sessionRepository.deactivateByFamilyId(familyId);
    }
}
