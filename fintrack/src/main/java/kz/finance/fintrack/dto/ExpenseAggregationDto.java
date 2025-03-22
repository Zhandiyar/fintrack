package kz.finance.fintrack.dto;

import java.math.BigDecimal;
import java.util.Optional;

public record ExpenseAggregationDto(
        BigDecimal totalExpenses,
        BigDecimal dailyExpenses,
        BigDecimal weeklyExpenses,
        BigDecimal monthlyExpenses,
        BigDecimal yearlyExpenses
) {
    public ExpenseAggregationDto(Number totalExpenses, Number dailyExpenses, Number weeklyExpenses, Number monthlyExpenses, Number yearlyExpenses) {
        this(
                toBigDecimal(totalExpenses),
                toBigDecimal(dailyExpenses),
                toBigDecimal(weeklyExpenses),
                toBigDecimal(monthlyExpenses),
                toBigDecimal(yearlyExpenses)
        );
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(value.doubleValue());
        }
        return BigDecimal.valueOf(value.longValue());
    }
}
