package kz.finance.security.service;

import io.jsonwebtoken.Claims;
import kz.finance.security.dto.AppleUserInfo;
import kz.finance.security.exception.AppleAuthException;
import kz.finance.security.model.AuthProvider;
import kz.finance.security.model.UserEntity;
import kz.finance.security.model.UserRole;
import kz.finance.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleAuthService {

    private final AppleJwtValidator jwtValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AppleUserInfo validateAndExtractUserInfo(String identityToken, String fullName, String email) {
        try {
            Claims claims = jwtValidator.validateAndParseToken(identityToken);

            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new AppleAuthException("Token does not contain 'sub' claim");
            }

            String tokenEmail = claims.get("email", String.class);
            String finalEmail = null;
            if (email != null && !email.isBlank()) {
                finalEmail = email;
            } else if (tokenEmail != null && !tokenEmail.isBlank()) {
                finalEmail = tokenEmail;
            }

            boolean emailVerified = readEmailVerified(claims);
            if (!emailVerified) {
                log.warn("Apple email is not verified for sub={}", sub);
            }

            String[] names = splitName(fullName);

            return new AppleUserInfo(
                    sub,
                    finalEmail,     // может быть null — это ок
                    emailVerified,
                    names[0],
                    names[1]
            );

        } catch (AppleAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract user info from Apple token", e);
            throw new AppleAuthException("Failed to extract user info", e);
        }
    }

    @Transactional
    public UserEntity getOrCreateAppleUser(AppleUserInfo userInfo) {
        // 1) ищем по appleId
        var byAppleId = userRepository.findByAppleId(userInfo.sub());
        if (byAppleId.isPresent()) {
            return byAppleId.get();
        }

        // 2) если есть email — пробуем прилепиться к существующему пользователю
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            var byEmail = userRepository.findByEmail(userInfo.email());
            if (byEmail.isPresent()) {
                UserEntity user = byEmail.get();

                // защита от "угонов" аккаунтов других провайдеров
                if (user.getProvider() != AuthProvider.LOCAL &&
                    user.getProvider() != AuthProvider.APPLE) {
                    throw new AppleAuthException("Email is already linked to another auth provider");
                }

                if (user.getAppleId() == null) {
                    user.setAppleId(userInfo.sub());
                }
                user.setProvider(AuthProvider.APPLE);
                user.addRole(UserRole.USER);
                return userRepository.save(user);
            }
        }

        // 3) создаём нового пользователя
        String baseUsername = generateUsername(userInfo);
        String finalUsername = ensureUniqueUsername(baseUsername);

        UserEntity user = UserEntity.builder()
                .username(finalUsername)
                .email(userInfo.email()) // может быть null — это нормально для Apple-only
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .appleId(userInfo.sub())
                .provider(AuthProvider.APPLE)
                .build();

        user.addRole(UserRole.USER);
        return userRepository.save(user);
    }

    private boolean readEmailVerified(Claims claims) {
        Object v = claims.get("email_verified");
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"", ""};
        String[] parts = fullName.trim().split("\\s+", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
    }

    private String generateUsername(AppleUserInfo userInfo) {
        // не завязываемся на sub, сразу делаем уникальный username
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "apple_" + randomSuffix;
    }

    private String ensureUniqueUsername(String base) {
        String username = base;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + "_" + counter++;
        }
        return username;
    }
}
