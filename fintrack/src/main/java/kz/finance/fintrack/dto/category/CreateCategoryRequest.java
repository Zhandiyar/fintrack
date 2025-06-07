package kz.finance.fintrack.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kz.finance.fintrack.model.TransactionType;

public record CreateCategoryRequest(
        @NotBlank String nameRu,
        @NotBlank String nameEn,
        @NotNull TransactionType type,
        @NotBlank String icon,
        @NotBlank String color
) {}