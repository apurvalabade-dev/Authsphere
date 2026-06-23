package com.authsphere.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    private record LimitRule(int maxRequests, Duration window) {}

    // Login gets a higher threshold since account locking already protects
    // individual accounts after 5 failures; this catches IP-level spraying
    // across many accounts instead.
    private static final Map<String, LimitRule> RULES = Map.of(
        "/api/v1/auth/login", new LimitRule(10, Duration.ofMinutes(1)),
        "/api/v1/auth/register", new LimitRule(5, Duration.ofHours(1)),
        "/api/v1/auth/forgot-password", new LimitRule(5, Duration.ofHours(1))
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        LimitRule rule = RULES.get(path);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        String key = "ratelimit:" + path + ":" + ip;

        boolean allowed = rateLimitService.isAllowed(key, rule.maxRequests(), rule.window());

        if (!allowed) {
            long ttl = rateLimitService.getTtlSeconds(key);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(ttl));
            response.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in " + ttl + " seconds.\",\"status\":429}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
