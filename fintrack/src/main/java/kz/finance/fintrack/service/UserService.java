package kz.finance.fintrack.service;

import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserEntity getByLogin(String userName) {
        log.info("Текущий пользователь: {}", userName);
        return repository.findByUsername(userName)
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
        repository.save(currentUser);
    }

    @Transactional
    public void deleteUserAndAllData() {
        UserEntity currentUser = getCurrentUser();
        log.info("Удаление пользователя и всех его данных: {}", currentUser.getUsername());
        repository.delete(currentUser);
        log.info("Пользователь и все его данные успешно удалены");
    }
}