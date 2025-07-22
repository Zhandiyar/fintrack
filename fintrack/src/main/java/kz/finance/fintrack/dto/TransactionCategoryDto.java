package kz.finance.fintrack.dto;

public record TransactionCategoryDto(
        Long id,
        String name,
        String icon,
        String color
) {
}
