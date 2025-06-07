package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.LocalizedTransactionResponseDto;
import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.dto.analytics.AnalyticsCategoriesDto;
import kz.finance.fintrack.dto.analytics.AnalyticsCategoryDto;
import kz.finance.fintrack.dto.analytics.AnalyticsSummaryDto;
import kz.finance.fintrack.dto.analytics.CategorySummaryDto;
import kz.finance.fintrack.dto.analytics.DashboardDto;
import kz.finance.fintrack.dto.analytics.DashboardStatsDto;
import kz.finance.fintrack.model.CategorySummaryProjection;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.TransactionRepository;
import kz.finance.fintrack.utils.DateRangeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final UserService userService;

    public DashboardDto getDashboard(LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd, String lang) {
        UserEntity currentUser = userService.getCurrentUser();

        long daysBetween = ChronoUnit.DAYS.between(currentPeriodStart, currentPeriodEnd);
        LocalDateTime previousPeriodStart = currentPeriodStart.minusDays(daysBetween);
        LocalDateTime previousPeriodEnd = currentPeriodStart;

        DashboardStatsDto stats = transactionRepository.getDashboardStats(
                currentUser,
                currentPeriodStart,
                currentPeriodEnd,
                previousPeriodStart,
                previousPeriodEnd
        );

        Double incomeChange = calculatePercentageChange(stats.previousPeriodIncome(), stats.currentPeriodIncome());
        Double expenseChange = calculatePercentageChange(stats.previousPeriodExpense(), stats.currentPeriodExpense());

        BigDecimal balance = stats.totalIncome().subtract(stats.totalExpense());

        List<TransactionResponseDto> recentTransactions =
                transactionRepository.findRecentTransactions(currentUser, PageRequest.of(0, 5));

        List<LocalizedTransactionResponseDto> localizedTransactions =
                recentTransactions.stream()
                        .map(t -> new LocalizedTransactionResponseDto(
                                t.id(),
                                t.amount(),
                                t.date(),
                                t.createdAt(),
                                t.updatedAt(),
                                t.comment(),
                                t.type(),
                                t.categoryId(),
                                t.getCategoryName(lang) // 👈 вот тут локализация
                        ))
                        .toList();

        String periodLabel = DateRangeResolver.formatPeriodLabel(currentPeriodStart, lang);

        return new DashboardDto(
                stats.totalIncome(),
                stats.totalExpense(),
                balance,
                stats.currentPeriodIncome(),
                stats.currentPeriodExpense(),
                incomeChange,
                expenseChange,
                periodLabel,
                localizedTransactions
        );
    }

    public AnalyticsSummaryDto getTransactionSummary(LocalDateTime start, LocalDateTime end, PeriodType periodType) {
        UserEntity currentUser = userService.getCurrentUser();
        LocalDateTime previousStart = start.minus(Duration.between(start, end));
        LocalDateTime previousEnd = start;

        var stats = transactionRepository.getSummaryStats(currentUser, start, end, previousStart, previousEnd);

        Double incomeChange = calculatePercentageChange(stats.previousIncome(), stats.currentIncome());
        Double expenseChange = calculatePercentageChange(stats.previousExpense(), stats.currentExpense());

        var chartData = transactionRepository.getChartData(currentUser.getId(), start, end, periodType);
        BigDecimal netIncome = stats.currentIncome().subtract(stats.currentExpense()); // 💰 расчёт баланса

        return new AnalyticsSummaryDto(
                stats.currentIncome(),
                stats.currentExpense(),
                netIncome,
                incomeChange,
                expenseChange,
                chartData
        );
    }

    public AnalyticsCategoriesDto getCategoriesAnalytics(LocalDateTime start, LocalDateTime end, String lang) {
        UserEntity user = userService.getCurrentUser();

        List<CategorySummaryProjection> rawData = transactionRepository.getCategorySummaryNative(
                user.getId(), start, end, lang
        );

        List<CategorySummaryDto> allData = rawData.stream()
                .map(p -> new CategorySummaryDto(
                        p.getCategoryId(),
                        p.getNameRu(),
                        p.getNameEn(),
                        p.getTotalIncome(),
                        p.getTotalExpense()
                ))
                .toList();

        List<CategorySummaryDto> income = allData.stream()
                .filter(dto -> dto.totalIncome().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        List<CategorySummaryDto> expense = allData.stream()
                .filter(dto -> dto.totalExpense().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return new AnalyticsCategoriesDto(income, expense);
    }

    public AnalyticsCategoryDto getCategoryAnalytics(
            String categoryName, LocalDateTime start, LocalDateTime end, PeriodType periodType, String lang) {

        UserEntity user = userService.getCurrentUser();

        var totals = transactionRepository.getCategoryTotals(user, categoryName, start, end, lang);

        var chart = transactionRepository.getCategoryChartPoints(
                user.getId(), categoryName, start, end, periodType, lang
        );

        return new AnalyticsCategoryDto(categoryName, totals.income(), totals.expense(), chart);
    }

    private Double calculatePercentageChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return (current == null || current.compareTo(BigDecimal.ZERO) == 0)
                    ? 0.0
                    : 100.0;
        }

        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String getFormatByPeriodType(PeriodType type) {
        return switch (type) {
            case DAY -> "YYYY-MM-DD";
            case WEEK -> "IYYY-IW";
            case MONTH -> "YYYY-MM";
            case YEAR -> "YYYY";
        };
    }
}
