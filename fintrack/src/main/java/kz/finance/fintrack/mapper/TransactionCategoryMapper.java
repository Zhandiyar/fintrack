package kz.finance.fintrack.mapper;

import kz.finance.fintrack.dto.category.TransactionCategoryResponseDto;
import kz.finance.fintrack.model.TransactionCategoryEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionCategoryMapper {
    TransactionCategoryResponseDto toDto(TransactionCategoryEntity transactionCategoryEntity);
}
