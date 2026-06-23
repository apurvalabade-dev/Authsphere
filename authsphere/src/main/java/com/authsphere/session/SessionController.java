package com.authsphere.session;

import com.authsphere.auth.RefreshToken;
import com.authsphere.auth.RefreshTokenRepository;
import com.authsphere.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final RefreshTokenRepository refreshTokenRepository;

    public SessionController(SessionService sessionService,
                             RefreshTokenRepository refreshTokenRepository) {
        this.sessionService = sessionService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "X-Refresh-Token", required = false) String rawRefreshToken) {

        UUID userId = userDetails.getUser().getId();
        UUID currentFamilyId = resolveFamilyId(rawRefreshToken);
        return ResponseEntity.ok(sessionService.getActiveSessions(userId, currentFamilyId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> revokeSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {

        String accessToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        sessionService.revokeSession(id, userDetails.getUser().getId(), accessToken, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Session revoked successfully"));
    }

    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> revokeAllOtherSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Refresh-Token", required = false) String rawRefreshToken,
            HttpServletRequest request) {

        String accessToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        UUID currentFamilyId = resolveFamilyId(rawRefreshToken);
        sessionService.revokeAllOtherSessions(
            userDetails.getUser().getId(), currentFamilyId, accessToken, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "All other sessions revoked successfully"));
    }

    private UUID resolveFamilyId(String rawRefreshToken) {
        if (rawRefreshToken == null) return UUID.randomUUID();
        String hash = org.springframework.util.DigestUtils
            .md5DigestAsHex(rawRefreshToken.getBytes());
        return refreshTokenRepository.findByTokenHash(hash)
            .map(RefreshToken::getFamilyId)
            .orElse(UUID.randomUUID());
    }
}
