package kz.finance.security.service;

import kz.finance.security.exception.UserAlreadyExistsException;
import kz.finance.security.exception.UserNotFoundException;
import kz.finance.security.model.UserEntity;
import kz.finance.security.model.UserRole;
import kz.finance.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
                    .username(generateUsername(email))
                    .password(java.util.UUID.randomUUID().toString()) // фиктивный пароль
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
}