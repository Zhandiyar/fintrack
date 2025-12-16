package kz.finance.fintrack.service;

import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repository;

    public UserEntity getByLogin(String userName) {
        return repository.findByUsername(userName)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
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