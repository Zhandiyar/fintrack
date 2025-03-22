package kz.finance.fintrack.dto;

public record ExpenseSummaryRequestDto(
        @jakarta.validation.constraints.NotNull PeriodType periodType,
        @jakarta.validation.constraints.NotNull Integer year,
        Integer month, // Nullable, если выбрана неделя или год
        Integer day
) {
}
