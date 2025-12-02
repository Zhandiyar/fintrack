package kz.finance.security.repository;

import kz.finance.security.model.RefreshTokenEntity;
import kz.finance.security.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByToken(String token);

    Optional<RefreshTokenEntity> findByTokenAndUser(String token, UserEntity user);

    @Query("""
                SELECT t FROM RefreshTokenEntity t
                WHERE t.user = :user AND t.revoked = FALSE AND t.expiresAt > CURRENT_TIMESTAMP
            """)
    List<RefreshTokenEntity> findAllValidTokensByUser(UserEntity user);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}