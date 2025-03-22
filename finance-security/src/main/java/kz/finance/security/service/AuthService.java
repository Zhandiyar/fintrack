package kz.finance.security.service;

import kz.finance.security.model.UserRole;
import kz.finance.security.model.UserEntity;
import kz.finance.security.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// This service handles the authentication logic:
// - Generating and sending verification codes
// - Verifying the code and generating JWT tokens
@Service
public class AuthService {
    private final UserRepository userRepository;

    private final EmailService emailService;

    // Используем BCrypt для шифрования паролей
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // Регистрация пользователя
    public UserEntity register(String username, String password, String email, UserRole userRole) throws Exception {
        if(userRepository.findByUsername(username).isPresent()){
            throw new Exception("Username is already taken");
        }
        if(userRepository.findByEmail(email).isPresent()){
            throw new Exception("Email is already registered");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.getRoles().add(userRole.getRole());
        return userRepository.save(user);
    }

    // Аутентификация пользователя
    public UserEntity login(String username, String password) throws Exception {
        Optional<UserEntity> optionalUser = userRepository.findByUsername(username);
        if(optionalUser.isEmpty()){
            throw new Exception("User not found");
        }
        UserEntity user = optionalUser.get();
        if(!passwordEncoder.matches(password, user.getPassword())){
            throw new Exception("Invalid password");
        }
        // В продакшене можно возвращать JWT-токен
        return user;
    }

    // Функционал "Забыли пароль": генерирует новый пароль, обновляет его и отправляет на email
    public void forgotPassword(String email) throws Exception {
        Optional<UserEntity> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isEmpty()){
            throw new Exception("Email not found");
        }
        UserEntity user = optionalUser.get();
        // Генерируем новый случайный пароль
        String newPassword = generateRandomPassword();
        // Обновляем пароль пользователя
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // Отправляем новый пароль на email
        emailService.sendSimpleMessage(email, "Password Reset", "Your new password: " + newPassword);
    }

    // Простой генератор случайного пароля
    private String generateRandomPassword() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
