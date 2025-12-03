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

        // 1) попробуем chat
        try {
            return callDeepSeek("deepseek-chat", prompt);
        } catch (FeignException e) {
            log.warn("deepseek-chat failed, fallback to reasoner: {}", e.getMessage());
        }

        // 2) fallback → reasoner
        try {
            return callDeepSeek("deepseek-reasoner", prompt);
        } catch (FeignException e) {
            log.error("deepseek-reasoner also failed: {}", e.getMessage());
            return new FinanceAnalyzeResponse(
                    "AI временно недоступен — попробуйте позже!"
            );
        }
    }

    private FinanceAnalyzeResponse callDeepSeek(String model, String prompt) {

        log.info("Using DeepSeek model: {}", model);

        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(model)
                .messages(List.of(new DeepSeekMessage("user", prompt)))
                .maxTokens(model.equals("deepseek-chat") ? 260 : 200)
                .stream(false)
                .build();

        DeepSeekResponse response = deepSeekFeignClient.chatCompletion(
                "Bearer " + deepSeekApiKey,
                request
        );

        if (response.choices().isEmpty()) {
            return new FinanceAnalyzeResponse("AI не вернул ответ.");
        }

        return new FinanceAnalyzeResponse(
                response.choices().get(0).message().content()
        );
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
                Ты — персональный AI-финансовый консультант в приложении FinTrack.
                Пользователь оформил подписку и ожидает честный, практичный и компактный анализ.

                ВХОДНЫЕ ДАННЫЕ (контекст, только для тебя, не повторяй дословно числа):
                Период: %s
                %s

                Сформируй ответ СТРОГО в следующем markdown-формате:

                ### 1. Главный вывод
                1–2 предложения, кратко объясни, что произошло с бюджетом за месяц. Важную часть выдели **жирным**.

                ### 2. 3 ключевых наблюдения
                - Наблюдение 1 — конкретный паттерн или перекос в расходах/доходах.
                - Наблюдение 2.
                - Наблюдение 3.

                ### 3. 3 шага на следующий месяц
                - Шаг 1 — очень конкретное действие (лимит, правило, привычка).
                - Шаг 2 — ещё одно действие, связанное с сокращением расходов или улучшением дохода.
                - Шаг 3 — действие, связанное с планированием/контролем.

                ### 4. Финальная мотивация
                1 короткое предложение поддержки, можно 1 эмодзи.

                Жёсткие правила:
                - Пиши по-русски, на "ты".
                - Не используй проценты, отношения "в N раз" и точные вычисления.
                  Говори: "существенно больше", "заметно меньше" и т.п.
                - Не повторяй дословно входные числа.
                - Общий объём ответа — не больше 120–140 слов.
                - Стиль: понятный, деловой, без длинных метафор.
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