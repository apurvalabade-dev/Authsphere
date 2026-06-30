# AuthSphere — Enterprise Authentication Microservice

A production-grade authentication and authorization microservice built with **Java 21** and **Spring Boot 3.5.0**. Designed as a standalone service that any application can integrate with via REST API.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security 6, JJWT 0.12.6 (HS512) |
| Database | PostgreSQL 16 + Hibernate + Spring Data JPA |
| Migrations | Flyway |
| Cache | Redis 7 |
| Email | JavaMailSender + Mailtrap (dev) |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Containers | Docker + Docker Compose |

---

## Features

- **JWT Authentication** — HS512 signed access tokens (15 min expiry)
- **Refresh Token Rotation** — opaque UUID tokens with family-based tracking
- **Theft Detection** — replayed token triggers full family revocation via `REQUIRES_NEW` transaction
- **Token Blacklisting** — Redis-backed, TTL matched to token remaining lifetime
- **RBAC** — role + permission based access control, embedded in JWT claims
- **Account Locking** — 5 failed attempts → locked 15 min, auto-unlock on next attempt
- **Rate Limiting** — Redis fixed-window counter per IP per endpoint, runs before Spring Security
- **Email Verification** — required before login, 24-hour expiry, MD5 hash stored (not plaintext)
- **Password Reset** — single-use token, 1-hour expiry, revokes all existing sessions on success
- **Session Management** — per-device tracking, revoke specific or all other sessions
- **Audit Logging** — async writes in `REQUIRES_NEW` transactions, survives outer transaction rollback

---

## Architecture

```

Client (React / Mobile / Any Service)
              │
                            ▼
                                 Spring Boot API (:8080)
                                               │
                                                    ┌────────┼────────┐
                                                         ▼        ▼        ▼
                                                         PostgreSQL   Redis   SMTP Server
                                                         (users,    (blacklist, (verification +
                                                          tokens,    rate limit)  reset emails)
                                                           sessions,
                                                            audit logs)
                                                            ```

                                                            ---

                                                            ## Security Design

                                                            **Why `REQUIRES_NEW` on security writes**
                                                            Spring `@Transactional` rolls back all DB writes when an exception is thrown. Account locking, theft detection revocation, and failed attempt recording all happen *before* throwing an `AuthException`. Without `REQUIRES_NEW` on a separate service class, these writes are silently undone — the lock never saves, the revocation never commits. Every security-critical write runs in its own transaction that commits independently.

                                                            **Theft detection**
                                                            Each refresh token has a `familyId`. On rotation, the old token is marked `used=true` and a new token is issued with the same `familyId`. If a used token is ever presented again, it means a token was stolen and replayed. The system immediately revokes every token in that family, deactivates the session, and forces re-login — committed in a `REQUIRES_NEW` transaction before the exception rolls back the outer transaction.

                                                            **No email enumeration**
                                                            `/forgot-password` always returns the same response regardless of whether the email exists in the system.

                                                            **Password reset revokes all sessions**
                                                            A successful reset invalidates every active refresh token family for that user. Existing sessions on all devices are terminated.

                                                            ---

                                                            ## API Reference

                                                            ### Authentication (no token required)

                                                            | Method | Endpoint | Description |
                                                            |---|---|---|
                                                            | POST | `/api/v1/auth/register` | Register new user |
                                                            | GET | `/api/v1/auth/verify-email?token=` | Verify email address |
                                                            | POST | `/api/v1/auth/login` | Login, returns token pair |
                                                            | POST | `/api/v1/auth/refresh` | Rotate refresh token |
                                                            | POST | `/api/v1/auth/forgot-password` | Request password reset email |
                                                            | POST | `/api/v1/auth/reset-password` | Reset password with token |

                                                            ### Authenticated (Bearer token required)

                                                            | Method | Endpoint | Required | Description |
                                                            |---|---|---|---|
                                                            | POST | `/api/v1/auth/logout` | Any | Logout, blacklist access token |
                                                            | GET | `/api/v1/users/me` | Any | Get own profile |
                                                            | GET | `/api/v1/users` | `ROLE_ADMIN` | List all users |
                                                            | GET | `/api/v1/users/{id}` | `USER_READ` | Get user by ID |
                                                            | PUT | `/api/v1/users/{id}/roles` | `ROLE_ASSIGN` | Assign role to user |
                                                            | DELETE | `/api/v1/users/{id}` | `USER_DELETE` | Delete user |
                                                            | GET | `/api/v1/sessions` | Any | List active sessions |
                                                            | DELETE | `/api/v1/sessions/{id}` | Any | Revoke specific session |
                                                            | DELETE | `/api/v1/sessions/all` | Any | Revoke all other sessions |
                                                            | GET | `/api/v1/audit-logs/user/{id}` | `AUDIT_READ` | Get user audit trail |

                                                            ### Rate limits

                                                            | Endpoint | Limit |
                                                            |---|---|
                                                            | `/api/v1/auth/login` | 10 requests / minute / IP |
                                                            | `/api/v1/auth/register` | 5 requests / hour / IP |
                                                            | `/api/v1/auth/forgot-password` | 5 requests / hour / IP |

                                                            ---

                                                            ## Database Schema

                                                            12 tables managed via Flyway migrations (`V1` through `V8`):

                                                            ```

                                                            users                     status: UNVERIFIED | ACTIVE | LOCKED
                                                            roles                     ADMIN, MANAGER, SUPPORT, USER
                                                            permissions               USER_CREATE/READ/UPDATE/DELETE, ROLE_CREATE/ASSIGN, AUDIT_READ
                                                            user_roles                many-to-many
                                                            role_permissions          many-to-many
                                                            refresh_tokens            token_hash, family_id, parent_id, used, revoked
                                                            email_verification_tokens token_hash, used, expires_at
                                                            password_reset_tokens     token_hash, used, expires_at
                                                            sessions                  per-device: ip, user_agent, family_id, active
                                                            failed_login_attempts     per-user lockout counter
                                                            audit_logs                action, user_id, ip_address, metadata (JSONB)
                                                            ```

                                                            ---

                                                            ## RBAC

                                                            | Role | Permissions |
                                                            |---|---|
                                                            | `ADMIN` | All permissions |
                                                            | `MANAGER` | `USER_READ`, `USER_UPDATE` |
                                                            | `SUPPORT` | `USER_READ`, `AUDIT_READ` |
                                                            | `USER` | `USER_READ` |

                                                            ---

                                                            ## Running Locally

                                                            **Prerequisites:** Java 21, Maven, Docker

                                                            ```bash
                                                            git clone https://github.com/YOUR_USERNAME/authsphere.git
                                                            cd authsphere

                                                            # Start PostgreSQL and Redis
                                                            docker compose up -d

                                                            # Run
                                                            mvn spring-boot:run
                                                            ```

                                                            - API: `http://localhost:8080`
                                                            - Swagger UI: `http://localhost:8080/swagger-ui/index.html`

                                                            ---

                                                            ## Configuration

                                                            Key values in `application.yml` — replace for production:

                                                            | Key | Description |
                                                            |---|---|
                                                            | `jwt.secret` | Min 256-bit string for HS512 signing |
                                                            | `spring.mail.username` | SMTP username |
                                                            | `spring.mail.password` | SMTP password |
                                                            | `spring.datasource.url` | PostgreSQL connection string |
                                                            | `spring.data.redis.host` | Redis host |

                                                            ---

                                                            ## Integration Example

                                                            ```javascript
                                                            // Login
                                                            const { accessToken, refreshToken } = await fetch('/api/v1/auth/login', {
                                                              method: 'POST',
                                                                headers: { 'Content-Type': 'application/json' },
                                                                  body: JSON.stringify({ email, password })
                                                                  }).then(r => r.json());

                                                                  // Authenticated request
                                                                  const profile = await fetch('/api/v1/users/me', {
                                                                    headers: { 'Authorization': `Bearer ${accessToken}` }
                                                                    }).then(r => r.json());

                                                                    // Refresh when access token expires
                                                                    const { accessToken: newToken } = await fetch('/api/v1/auth/refresh', {
                                                                      method: 'POST',
                                                                        headers: { 'Content-Type': 'application/json' },
                                                                          body: JSON.stringify({ refreshToken })
                                                                          }).then(r => r.json());
                                                                          ```

                                                                          ---

                                                                          ## Audit Events

                                                                          | Event | Trigger |
                                                                          |---|---|
                                                                          | `LOGIN_SUCCESS` | Successful login |
                                                                          | `LOGIN_FAILURE` | Wrong password |
                                                                          | `LOGOUT` | Explicit logout |
                                                                          | `ACCOUNT_LOCKED` | 5 failed attempts |
                                                                          | `ACCOUNT_UNLOCKED` | Auto-unlock on next login |
                                                                          | `PASSWORD_RESET_REQUEST` | Forgot password submitted |
                                                                          | `PASSWORD_CHANGED` | Reset completed |
                                                                          | `ROLE_ASSIGNED` | Admin assigns role |
                                                                          | `SESSION_REVOKED` | Specific session deleted |
                                                                          | `ALL_OTHER_SESSIONS_REVOKED` | Revoke all other sessions |

                                                                          ---
                                                                          ---
