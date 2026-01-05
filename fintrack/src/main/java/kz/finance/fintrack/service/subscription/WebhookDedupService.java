package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.repository.WebhookDedupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookDedupService {

    private final WebhookDedupRepository repo;

    /** @return true если это первый раз (вставили запись), false если дубль */
    @Transactional
    public boolean acquire(SubscriptionProvider provider, String eventId) {
        if (eventId == null || eventId.isBlank()) return true; // если нет eventId — не можем дедупнуть
        return repo.insertIgnore(provider.name(), eventId) == 1;
    }
}
