package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.CategoryExpenseDetailsDto;
import kz.finance.fintrack.dto.CategoryExpenseDto;
import kz.finance.fintrack.dto.ExpenseDto;
import kz.finance.fintrack.dto.ExpenseSummaryDto;
import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.analytics.ChartDataDto;
import kz.finance.fintrack.model.ExpenseCategory;
import kz.finance.fintrack.model.ExpenseEntity;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final UserService userService;

    public List<ExpenseDto> getUserExpenses() {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Fetching expenses for user with ID: {}", currentUser.getId());
        List<ExpenseDto> expenses = expenseRepository.findByUser(currentUser).stream()
                .map(expense -> new ExpenseDto(
                        expense.getId(),
                        expense.getCategory(),
                        expense.getAmount(),
                        expense.getDescription(),
                        expense.getDate()))
                .collect(Collectors.toList());
        log.info("Found {} expenses for user with ID: {}", expenses.size(), currentUser.getId());
        return expenses;
    }

    public ExpenseDto addExpense(ExpenseDto dto) {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Adding new expense for user with ID: {}", currentUser.getId());

        ExpenseEntity expense = ExpenseEntity.builder()
                .category(dto.category())
                .amount(dto.amount())
                .date(dto.date())
                .description(dto.description())
                .user(currentUser)
                .build();

        expenseRepository.save(expense);
        log.info("Expense added with ID: {} for user with ID: {}", expense.getId(), currentUser.getId());
        return dto;
    }

    public ExpenseDto getExpenseById(Long expenseId) {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Fetching expense with ID: {} for user with ID: {}", expenseId, currentUser.getId());
        var expenseOpt = expenseRepository.findByIdAndUser(expenseId, currentUser);
        if (expenseOpt.isEmpty()) {
            log.error("Expense with ID: {} not found or does not belong to user with ID: {}", expenseId, currentUser.getId());
            throw new RuntimeException("Expense not found or does not belong to current user");
        }
        var expense = expenseOpt.get();
        log.info("Expense with ID: {} retrieved successfully", expense.getId());
        return new ExpenseDto(
                expense.getId(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getDate()
        );
    }

    public ExpenseDto updateExpense(ExpenseDto dto) {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Updating expense with ID: {} for user with ID: {}", dto.id(), currentUser.getId());
        var expenseOpt = expenseRepository.findByIdAndUser(dto.id(), currentUser);
        if (expenseOpt.isEmpty()) {
            log.error("Expense with ID: {} not found or does not belong to user with ID: {}", dto.id(), currentUser.getId());
            throw new RuntimeException("Expense not found or does not belong to current user");
        }
        var expense = expenseOpt.get();
        expense.setCategory(dto.category());
        expense.setAmount(dto.amount());
        expense.setDate(dto.date());
        expense.setDescription(dto.description());
        expenseRepository.save(expense);
        log.info("Expense with ID: {} updated successfully", expense.getId());
        return dto;
    }

    public void deleteExpense(Long expenseId) {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Deleting expense with ID: {} for user with ID: {}", expenseId, currentUser.getId());
        var expenseOpt = expenseRepository.findByIdAndUser(expenseId, currentUser);
        if (expenseOpt.isEmpty()) {
            log.error("Expense with ID: {} not found or does not belong to user with ID: {}", expenseId, currentUser.getId());
            throw new RuntimeException("Expense not found or does not belong to current user");
        }
        expenseRepository.delete(expenseOpt.get());
        log.info("Expense with ID: {} deleted successfully", expenseId);
    }

    public ExpenseSummaryDto getExpenseSummary(PeriodType periodType, Integer year, Integer month, Integer day) {
        UserEntity user = userService.getCurrentUser();

        LocalDate startDate;
        LocalDate endDate;
        int periodLength;
        String averageLabel;

        switch (periodType) {
            case YEAR -> {
                startDate = LocalDate.of(year, 1, 1);
                endDate = LocalDate.of(year, 12, 31);
                periodLength = 12;
                averageLabel = "₸/Мес";
            }
            case MONTH -> {
                if (month == null) throw new IllegalArgumentException("Month is required for MONTH period");
                startDate = LocalDate.of(year, month, 1);
                endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
                periodLength = endDate.lengthOfMonth();
                averageLabel = "₸/День";
            }
            case WEEK -> {
                if (month == null || day == null) throw new IllegalArgumentException("Month and Day are required for WEEK period");
                startDate = LocalDate.of(year, month, day).with(DayOfWeek.MONDAY);
                endDate = startDate.plusDays(6);
                periodLength = 7;
                averageLabel = "₸/День";
            }
            default -> throw new IllegalStateException("Unexpected period type: " + periodType);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<ChartDataDto> chartData = findChartData(periodType, user, start, end);
        BigDecimal totalAmount = chartData.stream()
                .map(ChartDataDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = totalAmount.divide(BigDecimal.valueOf(periodLength), 2, RoundingMode.HALF_UP);

        List<CategoryExpenseDto> rawCategories = expenseRepository.findExpenseSumGroupedByCategory(user, start, end);
        List<CategoryExpenseDto> categoryExpenses = calculateCategoryPercentages(rawCategories, totalAmount);

        return new ExpenseSummaryDto(
                totalAmount,
                average,
                averageLabel,
                chartData,
                categoryExpenses
        );
    }

    private List<ChartDataDto> findChartData(PeriodType periodType, UserEntity user, LocalDateTime start, LocalDateTime end) {
        return switch (periodType) {
            case YEAR -> expenseRepository.findMonthlyTotals(user, start, end);
            case MONTH -> expenseRepository.findDailyTotals(user, start, end);
            case WEEK -> expenseRepository.findWeekdayTotals(user, start, end);
            case DAY -> expenseRepository.findWeekdayTotals(user, start, end);
        };
    }

    private List<CategoryExpenseDto> calculateCategoryPercentages(List<CategoryExpenseDto> categories, BigDecimal total) {
        return categories.stream()
                .map(cat -> new CategoryExpenseDto(
                        cat.category(),
                        cat.amount(),
                        total.compareTo(BigDecimal.ZERO) > 0
                                ? cat.amount().multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue()
                                : 0
                ))
                .toList();
    }

    /**
     * Получение детальной статистики по категориям: сумма, процент, список транзакций
     */
    public List<CategoryExpenseDetailsDto> getExpenseSummaryByCategory(PeriodType periodType, Integer year, Integer month, Integer day) {
        UserEntity user = userService.getCurrentUser();
        LocalDate startDate;
        LocalDate endDate;

        switch (periodType) {
            case YEAR -> {
                startDate = LocalDate.of(year, 1, 1);
                endDate = LocalDate.of(year, 12, 31);
            }
            case MONTH -> {
                if (month == null) throw new IllegalArgumentException("Month required");
                startDate = LocalDate.of(year, month, 1);
                endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            }
            case WEEK -> {
                if (month == null || day == null) throw new IllegalArgumentException("Month and day required");
                startDate = LocalDate.of(year, month, day).with(DayOfWeek.MONDAY);
                endDate = startDate.plusDays(6);
            }
            default -> throw new IllegalStateException("Unexpected value: " + periodType);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<CategoryExpenseDto> categoryExpenses = expenseRepository.findExpenseSumGroupedByCategory(user, start, end);
        BigDecimal totalAmount = categoryExpenses.stream()
                .map(CategoryExpenseDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return categoryExpenses.stream()
                .map(cat -> new CategoryExpenseDetailsDto(
                        cat.category(),
                        cat.amount(),
                        totalAmount.compareTo(BigDecimal.ZERO) > 0
                                ? cat.amount()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalAmount, 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO,
                        expenseRepository.findExpensesByCategoryDto(user, cat.category(), PageRequest.of(0, 10))
                ))
                .toList();
    }

    /**
     * Получение списка расходов по категории с пагинацией
     */
    public List<ExpenseDto> getExpensesByCategory(ExpenseCategory category, int page, int size) {
        UserEntity currentUser = userService.getCurrentUser();
        log.info("Fetching expenses for user ID: {} by category: {}", currentUser.getId(), category);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        return expenseRepository.findExpensesByCategoryDto(currentUser, category, pageRequest);
    }

}
