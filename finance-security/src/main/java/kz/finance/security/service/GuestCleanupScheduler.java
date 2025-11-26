package kz.finance.security.service;

import kz.finance.security.model.TransactionEntity;
import kz.finance.security.model.UserEntity;
import kz.finance.security.repository.RefreshTokenRepository;
import kz.finance.security.repository.TransactionRepository;
import kz.finance.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Almaty") // каждый день в 3:00 утра
    public void cleanOldGuests() {
        int count = refreshTokenRepository.deleteAllExpired(Instant.now());
        log.info("🧹 Deleted {} expired refresh tokens", count);
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<UserEntity> oldGuests = userRepository.findAllByGuestIsTrueAndCreatedAtBefore(threshold);

        for (UserEntity guest : oldGuests) {
            // удалить транзакции гостя
            List<TransactionEntity> expenses = transactionRepository.findAllByUser(guest);
            transactionRepository.deleteAll(expenses);

            log.info("Deleting guest: {}", guest.getUsername());
            userRepository.delete(guest);
        }

        log.info("✅ Cleaned {} old guest users", oldGuests.size());
    }
}

