package kz.finance.fintrack.model;

import java.math.BigDecimal;

public interface CategorySummaryProjection {
    Long getCategoryId();
    String getNameRu();
    String getNameEn();
    BigDecimal getTotalIncome();
    BigDecimal getTotalExpense();
}