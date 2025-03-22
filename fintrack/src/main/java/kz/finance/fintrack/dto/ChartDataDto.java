package kz.finance.fintrack.dto;

public record ChartDataDto(
        String label, // "Янв", "Фев", "1", "Пн", etc.
        java.math.BigDecimal amount
) {
}
