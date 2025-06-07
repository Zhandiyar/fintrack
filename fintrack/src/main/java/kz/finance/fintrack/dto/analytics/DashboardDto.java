package kz.finance.fintrack.dto.analytics;

import kz.finance.fintrack.dto.LocalizedTransactionResponseDto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal balance,
    BigDecimal currentPeriodIncome,
    BigDecimal currentPeriodExpense,
    Double incomeChange,
    Double expenseChange,
    String periodLabel,
    List<LocalizedTransactionResponseDto> recentTransactions
) {}