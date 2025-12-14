package kz.finance.security.service;

import kz.finance.security.exception.UserAlreadyExistsException;
import kz.finance.security.exception.UserNotFoundException;
import kz.finance.security.model.AuthProvider;
import kz.finance.security.model.UserEntity;
import kz.finance.security.model.UserRole;
import kz.finance.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public void registerUser(String username, String email, String rawPassword, boolean isPro) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already in use: " + email);
        }

        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .build();

        user.addRole(UserRole.USER);
        if (isPro) {
            user.addRole(UserRole.PRO);
        }
        UserEntity savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getUsername());
    }

    public UserEntity getByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    public UserEntity getByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }

    public UserEntity getOrCreateGoogleUser(String email, String name) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            UserEntity user = UserEntity.builder()
                    .email(email)
                    .provider(AuthProvider.GOOGLE)
                    .username(generateUsername(email))
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .build();
            user.addRole(UserRole.USER);
            return userRepository.save(user);
        });
    }

    private String generateUsername(String email) {
        return email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 5);
    }

    public UserEntity save(UserEntity user) {
        return userRepository.save(user);
    }

    public UserEntity createGuestUser() {
        String guestId = UUID.randomUUID().toString();
        String username = "guest_" + guestId.substring(0, 8);
        String email = username + "@guest.fintrack.pro";
        String password = UUID.randomUUID().toString();

        UserEntity guestUser = UserEntity.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .guest(true)
                .roles(new HashSet<>(Set.of(UserRole.GUEST.getRole())))
                .build();

        UserEntity savedUser = userRepository.save(guestUser);
        log.info("Guest user created: {}", savedUser.getUsername());
        return savedUser;
    }


    public UserEntity upgradeGuestToUser(UserEntity guestUser, String newUsername, String email, String password) {
        guestUser.setUsername(newUsername);
        guestUser.setEmail(email);
        guestUser.setPassword(passwordEncoder.encode(password));
        guestUser.setGuest(false);
        guestUser.setRoles(new HashSet<>(Set.of(UserRole.USER.getRole())));

        return userRepository.save(guestUser);
    }

    public Optional<UserEntity> findByAppleId(String appleId) {
        return userRepository.findByAppleId(appleId);
    }

    public UserEntity getByLogin(String userName) {
        log.info("Текущий пользователь: {}", userName);
        return userRepository.findByUsername(userName)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public String getCurrentLogin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            log.info("Текущий пользователь: {}", authentication.getName());
            return authentication.getName();
        }
        return null;
    }

    public UserEntity getCurrentUser() {
        return getByLogin(getCurrentLogin());
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        UserEntity currentUser = getCurrentUser();
        // Проверяем, совпадает ли текущий пароль
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }

        // Кодируем и сохраняем новый пароль
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);
    }

    @Transactional
    public void deleteUserAndAllData() {
        UserEntity currentUser = getCurrentUser();
        log.info("Удаление пользователя и всех его данных: {}", currentUser.getUsername());
        userRepository.delete(currentUser);
        log.info("Пользователь и все его данные успешно удалены");
    }
}