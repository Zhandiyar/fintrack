package kz.finance.fintrack.model;

import java.math.BigDecimal;

public interface CategoryChartPointProjection {
    String getDate();
    BigDecimal getIncome();
    BigDecimal getExpense();
}
