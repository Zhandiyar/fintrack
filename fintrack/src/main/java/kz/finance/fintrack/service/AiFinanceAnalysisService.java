package kz.finance.fintrack.service;

import feign.FeignException;
import kz.finance.fintrack.client.DeepSeekFeignClient;
import kz.finance.fintrack.client.DeepSeekMessage;
import kz.finance.fintrack.client.DeepSeekRequest;
import kz.finance.fintrack.client.DeepSeekResponse;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiFinanceAnalysisService {

    private final TransactionRepository transactionRepository;
    private final DeepSeekFeignClient deepSeekFeignClient;
    private final UserService userService;

    @Value("${deepseek.api-key}")
    private String deepSeekApiKey;

    @Transactional(readOnly = true)
    public FinanceAnalyzeResponse analyzeMonth(int year, int month, String currency) {
        if (currency == null || currency.isBlank()) currency = "KZT";

        UserEntity user = userService.getCurrentUser();

        // Границы месяца
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<TransactionEntity> txs = transactionRepository.findAllByUserIdAndMonth(user.getId(), from, to);
        if (txs.isEmpty()) {
            return new FinanceAnalyzeResponse("Нет данных за выбранный месяц.");
        }

        String summary = buildSummary(txs, currency);
        String prompt = buildPrompt(summary, ym, txs.size(), currency);

        log.info("AI PROMPT:\n{}", prompt);

        try {
            DeepSeekRequest request = new DeepSeekRequest(
                    "deepseek-chat",
                    List.of(new DeepSeekMessage("user", prompt))
            );

            DeepSeekResponse response = deepSeekFeignClient.chatCompletion("Bearer " + deepSeekApiKey, request);

            return new FinanceAnalyzeResponse(response.choices().isEmpty()
                    ? "AI не вернул ответ, попробуйте позже."
                    : response.choices().get(0).message().content());
        } catch (FeignException e) {
            if (e.status() == 402) {
                return new FinanceAnalyzeResponse( "Технические работы на сервере AI. Попробуйте позже — мы уже всё чиним!");
            }
            log.error("Ошибка обращения к DeepSeek: {}", e.getMessage(), e);
            throw e;
        }
    }

    /** Генерация адаптивного prompt в зависимости от числа транзакций */
    private String buildPrompt(String summary, YearMonth ym, int txCount, String currency) {
        String period = "%s %d".formatted(
                ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("ru")),
                ym.getYear()
        );
        String symbol = CurrencyUtil.getSymbol(currency);

        if (txCount < 4) {
            // Мало транзакций — быстрый лайфхак + мотивация
            return """
                Ты — персональный AI-помощник в приложении Fintrack.

                Вот короткая история пользователя за %s (%s):

                %s

                Все суммы указаны в %s.
                1. Проанализируй траты и доходы, дай 1 яркий лайфхак для экономии — очень кратко!
                2. Ответь дружелюбно, добавь эмодзи и короткую позитивную мотивацию (1-2 предложения).
                3. Всегда делай ответ разным и интересным!
            """.formatted(period, symbol, summary, symbol);
        } else {
            // Стандартный wow-анализ с челленджами
            return """
                Ты — персональный AI-помощник по финансам в приложении Fintrack.

                Данные пользователя за %s (%s):

                %s

                Все суммы указаны в %s.
                1. Проанализируй баланс, выдели необычные/крупные траты, дай краткий вывод.
                2. Напиши на русском — коротко, дружелюбно, с эмодзи.
                3. Придумай 2 новых лайфхака, челлендж или мини-игру (например, “угадай лишнюю трату”, “выбери трату месяца”).
                4. Иногда добавляй неожиданный совет или интересный факт о финансах!
                5. В конце — мотивация (“каждый месяц мы придумаем что-то новенькое!”, “попробуй анализ еще раз — результат может удивить!”).
                6. Ответ всегда делай разным!
            """.formatted(period, symbol, summary, symbol);
        }
    }

    /** Формирование summary: топ-доходы, топ-расходы, категории, итоговые суммы */
    private String buildSummary(List<TransactionEntity> txs, String currency) {
        String symbol = CurrencyUtil.getSymbol(currency);

        var topIncome = txs.stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .sorted(Comparator.comparing(TransactionEntity::getAmount).reversed())
                .limit(3)
                .collect(Collectors.toList());

        var topExpense = txs.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .sorted(Comparator.comparing(TransactionEntity::getAmount).reversed())
                .limit(3)
                .collect(Collectors.toList());

        var byCategory = txs.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory().getNameRu(),
                        Collectors.mapping(TransactionEntity::getAmount, Collectors.toList())
                ));

        var categorySummary = byCategory.entrySet().stream()
                .map(e -> String.format("- %s: %s %s",
                        e.getKey(),
                        e.getValue().stream().map(amount -> amount.toPlainString() + " " + symbol).collect(Collectors.joining(", ")),
                        ""))
                .collect(Collectors.joining("\n"));

        var topIncomeSummary = topIncome.isEmpty() ? "—" : topIncome.stream()
                .map(tx -> String.format("- %s %s (%s, %s)",
                        tx.getAmount().setScale(0, RoundingMode.DOWN),
                        symbol,
                        tx.getCategory().getNameRu(),
                        tx.getComment() == null ? "" : tx.getComment()))
                .collect(Collectors.joining("\n"));

        var topExpenseSummary = topExpense.isEmpty() ? "—" : topExpense.stream()
                .map(tx -> String.format("- %s %s (%s, %s)",
                        tx.getAmount().setScale(0, RoundingMode.DOWN),
                        symbol,
                        tx.getCategory().getNameRu(),
                        tx.getComment() == null ? "" : tx.getComment()))
                .collect(Collectors.joining("\n"));

        var totalIncome = txs.stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalExpense = txs.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var balance = totalIncome.subtract(totalExpense);

        return String.format(
                "Категории расходов:\n%s\n\nТоп-3 дохода:\n%s\n\nТоп-3 расхода:\n%s\n\nВсего доходов: %s %s\nВсего расходов: %s %s\nБаланс: %s %s",
                categorySummary,
                topIncomeSummary,
                topExpenseSummary,
                totalIncome.toPlainString(), symbol,
                totalExpense.toPlainString(), symbol,
                balance.toPlainString(), symbol
        );
    }
}