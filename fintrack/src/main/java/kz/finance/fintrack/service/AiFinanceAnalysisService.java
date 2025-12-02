package kz.finance.fintrack.service;

import feign.FeignException;
import kz.finance.fintrack.client.deepseek.DeepSeekFeignClient;
import kz.finance.fintrack.client.deepseek.DeepSeekMessage;
import kz.finance.fintrack.client.deepseek.DeepSeekRequest;
import kz.finance.fintrack.client.deepseek.DeepSeekResponse;
import kz.finance.fintrack.dto.ai.FinanceAnalyzeResponse;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.TransactionRepository;
import kz.finance.fintrack.utils.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiFinanceAnalysisService {

    private final TransactionRepository transactionRepository;
    private final DeepSeekFeignClient deepSeekFeignClient;
    private final UserService userService;
    private static final String DEFAULT_CURRENCY = "KZT";

    @Value("${deepseek.api-key}")
    private String deepSeekApiKey;

    @Transactional(readOnly = true)
    public FinanceAnalyzeResponse analyzeMonth(int year, int month, String currency) {
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }

        UserEntity user = userService.getCurrentUser();

        // Границы месяца
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<TransactionEntity> txs = transactionRepository.findAllByUserIdAndMonth(user.getId(), from, to);
        if (txs.isEmpty()) {
            return new FinanceAnalyzeResponse("Нет данных за выбранный месяц.");
        }

        String summary = buildPremiumSummary(txs, currency);
        String prompt = buildPremiumPrompt(summary, ym);

        log.info("AI PROMPT:\n{}", prompt);

        try {
            DeepSeekRequest request = new DeepSeekRequest(
                    "deepseek-chat",
                    List.of(new DeepSeekMessage("user", prompt)),
                    300,       // ограничиваем размер ответа (ускоряет)
                    0.7,       // креативность
                    1.0        // логичность
            );

            DeepSeekResponse response = deepSeekFeignClient.chatCompletion("Bearer " + deepSeekApiKey, request);

            return new FinanceAnalyzeResponse(response.choices().isEmpty()
                    ? "AI не вернул ответ, попробуйте позже."
                    : response.choices().get(0).message().content());
        } catch (FeignException e) {
            if (e.status() == 402) {
                return new FinanceAnalyzeResponse(
                        "AI временно недоступен — мы уже всё чиним. Попробуйте позже!"
                );
            }
            log.error("Ошибка обращения к DeepSeek: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Premium Luxury Deep Review — оптимизированный короткий prompt
     */
    private String buildPremiumPrompt(String summary, YearMonth ym) {

        String period = "%s %d".formatted(
                ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                        java.util.Locale.forLanguageTag("ru")),
                ym.getYear()
        );

        return """
            Ты — персональный Premium AI-консультант по финансам в приложении FinTrack.  
            Пользователь заплатил за подписку, ожидая экспертность, стиль и сильные инсайты.

            Данные за месяц (%s):
            %s

            Создай Premium Luxury Deep-Review:

            1) Атмосфера месяца  
            Опиши настроение месяца: уверенный, нестабильный, импульсивный, спокойный, собранный.  
            Лёгкие эмоции + умный тон.

            2) 3 самых глубоких наблюдения  
            То, что реально важно: повторяющиеся траты, перекосы, скрытые мелкие расходы, “вампиры бюджета”.

            3) Финансовый портрет  
            Стиль поведения: аккуратный, спонтанный, рациональный, гибкий, расходный или накопительный.

            4) Premium рекомендация  
            1 точный совет, основанный только на цифрах.

            5) Прогноз + краткий план  
            Мягкий прогноз следующего месяца + 2 шага для улучшения результата.

            6) Финальный Premium-аккорд  
            Элитный тон, уверенность, лёгкий оптимизм, 1–2 эмодзи.  
            Ответ делай уникальным, эстетичным, сильным.
            """.formatted(period, summary);
    }

    /**
     * Очень лёгкий summary → модель работает быстро.
     * ТОЛЬКО ключевые цифры — без списков транзакций.
     */
    private String buildPremiumSummary(List<TransactionEntity> txs, String currency) {
        String symbol = CurrencyUtil.getSymbol(currency);

        BigDecimal income = txs.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = txs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = income.subtract(expense);

        Map<String, BigDecimal> topCategories = txs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().getNameRu(),
                        Collectors.reducing(BigDecimal.ZERO,
                                TransactionEntity::getAmount, BigDecimal::add)
                ));

        String categories = topCategories.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> "%s: %s %s".formatted(
                        e.getKey(),
                        e.getValue().setScale(0, RoundingMode.DOWN),
                        symbol
                ))
                .collect(Collectors.joining("; "));

        return """
            Топ категорий расходов: %s
            Доходы: %s %s
            Расходы: %s %s
            Баланс: %s %s
            """.formatted(
                categories,
                income.setScale(0, RoundingMode.DOWN), symbol,
                expense.setScale(0, RoundingMode.DOWN), symbol,
                balance.setScale(0, RoundingMode.DOWN), symbol
        );
    }
}