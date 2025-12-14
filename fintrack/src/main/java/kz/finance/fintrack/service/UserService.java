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

    public UserEntity getByLogin(String userName) {
        return repository.findByUsername(userName)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public String getCurrentLogin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
       return null;
    }

    public UserEntity getCurrentUser() {
        String login = getCurrentLogin();

        if (login == null || "anonymousUser".equals(login)) {
            throw new IllegalStateException("Unauthenticated user");
        }

        return getByLogin(login);
    }

}