package kz.finance.fintrack.mapper;


import kz.finance.fintrack.dto.TransactionCategoryDto;
import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.model.TransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    @Mapping(source = "category.id", target = "category.id")
    @Mapping(source = "category.icon", target = "category.icon")
    @Mapping(source = "category.color", target = "category.color")
    TransactionResponseDto toDto(TransactionEntity entity);


    default TransactionResponseDto toDto(TransactionEntity entity, String lang) {
        TransactionResponseDto baseDto = toDto(entity);

        String name = (lang != null && lang.startsWith("en"))
                ? entity.getCategory().getNameEn()
                : entity.getCategory().getNameRu();

        // Собираем новый TransactionCategoryDto с локализованным name
        TransactionCategoryDto localizedCategory = new TransactionCategoryDto(
                entity.getCategory().getId(),
                name,
                entity.getCategory().getIcon(),
                entity.getCategory().getColor()
        );

        return new TransactionResponseDto(
                baseDto.id(),
                baseDto.amount(),
                baseDto.date(),
                baseDto.createdAt(),
                baseDto.updatedAt(),
                baseDto.comment(),
                baseDto.type(),
                localizedCategory
        );
    }
}
