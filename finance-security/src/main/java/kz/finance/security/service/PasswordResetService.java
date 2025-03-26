package kz.finance.security.service;

import kz.finance.security.exception.TokenException;
import kz.finance.security.model.PasswordResetTokenEntity;
import kz.finance.security.model.UserEntity;
import kz.finance.security.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;

    @Transactional
    public PasswordResetTokenEntity createOrUpdatePasswordResetTokenForUser(UserEntity user) {
        java.util.Optional<PasswordResetTokenEntity> existingTokenOpt = tokenRepository.findByUser(user);

        PasswordResetTokenEntity token = existingTokenOpt.orElse(
                PasswordResetTokenEntity.builder().user(user).build()
        );

        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(LocalDateTime.now().plusHours(24));

        PasswordResetTokenEntity saved = tokenRepository.save(token);
        log.info("Password reset token (re)created for user {}: {}", user.getUsername(), token.getToken());
        return saved;
    }

    @Transactional
    public PasswordResetTokenEntity validatePasswordResetToken(String tokenValue) {
        PasswordResetTokenEntity token = tokenRepository.findByToken(tokenValue)
            .orElseThrow(() -> new TokenException("Invalid password reset token"));
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new TokenException("Password reset token expired");
        }
        return token;
    }
}
