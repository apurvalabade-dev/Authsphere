package com.authsphere.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findByUserIdAndActiveTrue(UUID userId);

    Optional<Session> findByRefreshTokenFamilyId(UUID familyId);

    @Modifying
    @Query("UPDATE Session s SET s.active = false WHERE s.userId = :userId")
    void deactivateAllByUserId(UUID userId);

    @Modifying
    @Query("UPDATE Session s SET s.active = false WHERE s.refreshTokenFamilyId = :familyId")
    void deactivateByFamilyId(UUID familyId);
}
