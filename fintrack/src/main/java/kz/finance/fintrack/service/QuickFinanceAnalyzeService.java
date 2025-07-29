package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.ai.FinanceAnalyzeResponse;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.TransactionRepository;
import kz.finance.fintrack.utils.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuickFinanceAnalyzeService {

    private final UserService userService;
    private final TransactionRepository transactionRepository;
    private final Random rnd = new SecureRandom();

    private static final List<String> GREETINGS = List.of(
            "Ваш финансовый итог за %s %d!",
            "Вот краткий разбор за %s %d:",
            "Молниеносный отчёт за %s %d:",
            "Финансовый экспресс-итог за %s %d — смотрим вместе!"
    );
    private static final List<String> BALANCE_COMMENTS = List.of(
            "В этом месяце ваш баланс %s %s.",
            "Баланс месяца: %s %s.",
            "Ваш итог по балансу: %s %s.",
            "Финансовый результат: %s %s."
    );
    private static final List<String> TIPS = List.of(
            "Самая крупная категория расходов — %s. Совет: попробуйте вести список покупок заранее!",
            "Ваша основная статья расходов — %s. Может, пора поискать акции и скидки?",
            "Траты на %s превысили другие категории. Планирование — ключ к успеху!",
            "Попробуйте неделю без трат на %s — это интересный эксперимент!",
            "💡 Помните: контроль расходов — ваш путь к большим целям!"
    );
    private static final List<String> ADVICE = List.of(
            "😉 Держитесь курса — даже маленькие шаги приносят большой результат!",
            "🚀 Каждый месяц — новая возможность для роста!"
    );

    public FinanceAnalyzeResponse quickAnalyze(int year, int month, String currency) {
        if (currency == null || currency.isBlank()) currency = "KZT";
        String symbol = CurrencyUtil.getSymbol(currency);

        UserEntity user = userService.getCurrentUser();
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<TransactionEntity> txs = transactionRepository.findAllByUserIdAndMonth(user.getId(), from, to);

        if (txs.isEmpty()) {
            return new FinanceAnalyzeResponse(
                    "В этом месяце пока нет данных о доходах и расходах. " +
                    "Добавьте хотя бы одну транзакцию, чтобы получить анализ! 🚀"
            );
        }

        BigDecimal income = getTotalByType(txs, TransactionType.INCOME);
        BigDecimal expense = getTotalByType(txs, TransactionType.EXPENSE);
        BigDecimal balance = income.subtract(expense);

        String topCategory = getTopExpenseCategory(txs);

        String greeting = String.format(
                GREETINGS.get(rnd.nextInt(GREETINGS.size())),
                ym.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru")), ym.getYear());

        String balanceComment = String.format(
                BALANCE_COMMENTS.get(rnd.nextInt(BALANCE_COMMENTS.size())),
                balance.setScale(2, RoundingMode.DOWN), symbol);

        String tip = String.format(
                TIPS.get(rnd.nextInt(TIPS.size())),
                topCategory);

        String advice = ADVICE.get(rnd.nextInt(ADVICE.size()));

        String result = String.join("\n",
                greeting,
                balanceComment,
                String.format("Доходы: %s %s | Расходы: %s %s",
                        income.setScale(2, RoundingMode.DOWN), symbol,
                        expense.setScale(2, RoundingMode.DOWN), symbol),
                tip,
                advice
        );

        log.info("QuickAnalyze for user {}: {}", user.getId(), result);

        return new FinanceAnalyzeResponse(result);
    }

    private BigDecimal getTotalByType(List<TransactionEntity> txs, TransactionType type) {
        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String getTopExpenseCategory(List<TransactionEntity> txs) {
        return txs.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory().getNameRu(),
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Нет расходов");
    }
}
