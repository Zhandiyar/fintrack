package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.repository.IapIdempotencyRepository;
import kz.finance.fintrack.repository.WebhookDedupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class IapIdempotencyCleanupJob {

    private final IapIdempotencyRepository iapIdempotencyRepository;
    private final WebhookDedupRepository webhookDedupRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Almaty")
    @Transactional
    public void cleanup() {
        iapIdempotencyRepository.deleteOlderThan(Instant.now().minus(7, ChronoUnit.DAYS));
        webhookDedupRepository.deleteOlderThan(Instant.now().minus(30, ChronoUnit.DAYS));
    }
}

