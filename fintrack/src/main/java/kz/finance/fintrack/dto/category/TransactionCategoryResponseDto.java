package kz.finance.fintrack.dto.category;

import kz.finance.fintrack.model.TransactionType;
import lombok.Data;

@Data
public class TransactionCategoryResponseDto {
    private Long id;
    private String nameRu;
    private String nameEn;
    private String icon;
    private String color;
    private TransactionType type;
    private boolean system;
}

