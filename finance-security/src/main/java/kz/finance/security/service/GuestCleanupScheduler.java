package kz.finance.security.service;

import kz.finance.security.model.ExpenseEntity;
import kz.finance.security.model.UserEntity;
import kz.finance.security.repository.ExpenseRepository;
import kz.finance.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Almaty") // каждый день в 3:00 утра
    public void cleanOldGuests() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<UserEntity> oldGuests = userRepository.findAllByGuestIsTrueAndCreatedAtBefore(threshold);

        for (UserEntity guest : oldGuests) {
            // удалить расходы гостя
            List<ExpenseEntity> expenses = expenseRepository.findAllByUser(guest);
            expenseRepository.deleteAll(expenses);

            log.info("Deleting guest: {}", guest.getUsername());
            userRepository.delete(guest);
        }

        log.info("✅ Cleaned {} old guest users", oldGuests.size());
    }
}

