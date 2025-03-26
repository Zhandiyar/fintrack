package kz.finance.security.repository;

import kz.finance.security.model.PasswordResetTokenEntity;
import kz.finance.security.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByToken(String token);
    Optional<PasswordResetTokenEntity> findByUser(UserEntity user);

    void deleteByUser(UserEntity user);
}
