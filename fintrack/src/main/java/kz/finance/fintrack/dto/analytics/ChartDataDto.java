package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record ChartDataDto(
        String label, // "Янв", "Фев", "1", "Пн", etc.
        BigDecimal amount
) {
}
