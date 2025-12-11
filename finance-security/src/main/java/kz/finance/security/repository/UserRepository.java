package kz.finance.security.repository;

import kz.finance.security.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByAppleId(String appleId);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    List<UserEntity> findAllByGuestIsTrueAndCreatedAtBefore(LocalDateTime threshold);
}
