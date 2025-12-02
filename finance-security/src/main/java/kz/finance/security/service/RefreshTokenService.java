package kz.finance.security.service;

import kz.finance.security.config.JwtTokenProvider;
import kz.finance.security.dto.AuthResponseDto;
import kz.finance.security.exception.RefreshTokenException;
import kz.finance.security.model.RefreshTokenEntity;
import kz.finance.security.model.UserEntity;
import kz.finance.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.access.expiration.ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh.expiration.ms}")
    private long refreshTokenExpirationMs;

    /**
     * Создаём новый refresh token для пользователя.
     */
    public RefreshTokenEntity createRefreshToken(UserEntity user) {
        var token = RefreshTokenEntity.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        RefreshTokenEntity saved = refreshTokenRepository.save(token);
        log.info("Refresh token created for user {}", user.getUsername());
        return saved;
    }

    /**
     * Генерируем пару access + refresh токенов для пользователя.
     */
    public AuthResponseDto generateTokenPairForUser(UserEntity user) {
        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        RefreshTokenEntity refreshToken = createRefreshToken(user);
        return AuthResponseDto.of(accessToken, refreshToken.getToken(), accessTokenExpirationMs);
    }

    /**
     * Рефрешим токен: валидируем, ротируем refresh и выдаём новую пару токенов.
     */
    public AuthResponseDto refresh(String refreshTokenValue) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        if (token.isRevoked()) {
            throw new RefreshTokenException("Refresh token has been revoked");
        }

        if (token.isExpired()) {
            throw new RefreshTokenException("Refresh token has expired");
        }

        UserEntity user = token.getUser();

        // Ревокация старого refresh токена
        token.setRevoked(true);
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        // Создаём новый refresh токен + access токен
        return generateTokenPairForUser(user);
    }

    public void revokeToken(String token, UserEntity user) {
        RefreshTokenEntity entity = refreshTokenRepository
                .findByTokenAndUser(token, user)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        entity.setRevoked(true);
        entity.setRevokedAt(Instant.now());

        refreshTokenRepository.save(entity);
    }

    public void revokeAllUserTokens(UserEntity user) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findAllValidTokensByUser(user);

        for (RefreshTokenEntity t : tokens) {
            t.setRevoked(true);
            t.setRevokedAt(Instant.now());
        }

        refreshTokenRepository.saveAll(tokens);
    }
}
