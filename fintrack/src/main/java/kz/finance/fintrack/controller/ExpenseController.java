package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.CategoryExpenseDetailsDto;
import kz.finance.fintrack.dto.ExpenseCategory;
import kz.finance.fintrack.dto.ExpenseDto;
import kz.finance.fintrack.dto.ExpenseSummaryDto;
import kz.finance.fintrack.utils.*;
import kz.finance.fintrack.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;
    private final ExpenseCategoryConverter expenseCategoryConverter;
private final PeriodTypeCategoryConverter periodTypeCategoryConverter;
    @GetMapping
    public List<ExpenseDto> getExpenses() {
        return expenseService.getUserExpenses();
    }

    @PostMapping
    public ExpenseDto addExpense(@RequestBody ExpenseDto dto) {
        return expenseService.addExpense(dto);
    }

    @GetMapping("/{id}")
    public ExpenseDto getExpenseById(@PathVariable Long id) {
        return expenseService.getExpenseById(id);
    }

    // Обновить расход
    @PutMapping
    public ExpenseDto updateExpense(@RequestBody ExpenseDto expenseDto) {
        return expenseService.updateExpense(expenseDto);
    }

    // Удалить расход
    @DeleteMapping("/{id}")
    public void deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
    }

    /**
     * Получение сводки расходов за день, неделю, месяц, год, средний расход
     */
    @GetMapping("/analytics/summary")
    public ExpenseSummaryDto getExpenseSummary(
            @RequestParam String periodType,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day

    ) {
        var period = periodTypeCategoryConverter.convert(periodType);
        return expenseService.getExpenseSummary(period, year, month, day);
    }

    /**
     * Получение детализированной статистики по категориям (сумма, % и последние 10 транзакций)
     */
    @GetMapping("/analytics/summary/categories")
    public List<CategoryExpenseDetailsDto> getExpenseSummaryByCategory(
            @RequestParam String periodType,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day
    ) {
        var period = periodTypeCategoryConverter.convert(periodType);
        return expenseService.getExpenseSummaryByCategory(period, year, month, day);
    }

    /**
     * Получение списка расходов по конкретной категории с пагинацией
     */
    @GetMapping("/analytics/category")
    public List<ExpenseDto> getExpensesByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ExpenseCategory expenseCategory = expenseCategoryConverter.convert(category);
        return expenseService.getExpensesByCategory(expenseCategory, page, size);
    }
}
