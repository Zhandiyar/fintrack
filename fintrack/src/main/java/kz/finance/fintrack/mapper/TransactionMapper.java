package kz.finance.fintrack.mapper;


import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.model.TransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.nameRu", target = "categoryNameRu")
    @Mapping(source = "category.nameEn", target = "categoryNameEn")
    TransactionResponseDto toDto(TransactionEntity entity);
}
